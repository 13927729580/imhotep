/*
 * Copyright (C) 2014 Indeed Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
 package com.indeed.imhotep.local;

import com.google.common.collect.Lists;
import com.google.common.io.ByteStreams;
import com.indeed.flamdex.api.FlamdexReader;
import com.indeed.imhotep.AbstractImhotepMultiSession;
import com.indeed.imhotep.ImhotepRemoteSession;
import com.indeed.imhotep.MemoryReservationContext;
import com.indeed.imhotep.api.ImhotepOutOfMemoryException;
import com.indeed.imhotep.multicache.ftgs.*;
import com.indeed.util.core.Throwables2;
import com.indeed.util.core.io.Closeables2;
import org.apache.log4j.Logger;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author jsgroth
 */
public class MTImhotepLocalMultiSession extends AbstractImhotepMultiSession<ImhotepLocalSession> {
    private static final Logger log = Logger.getLogger(MTImhotepLocalMultiSession.class);

    static {
        loadNativeLibrary();
        nativeInit();
        log.info("libftgs loaded");
        log.info("Using SSSE3! (if the processor in this computer doesn't support SSSE3 "
                         + "this process will fail with SIGILL)");
    }

    private static void loadNativeLibrary() {
        try {
            final String osName = System.getProperty("os.name");
            final String arch = System.getProperty("os.arch");
            final String resourcePath = "/native/" + osName + "-" + arch + "/libftgs.so.1.0.1";
            final InputStream is = MTImhotepLocalMultiSession.class.getResourceAsStream(resourcePath);
            if (is == null) {
                throw new FileNotFoundException(
                        "unable to find libftgs.so.1.0.1 at resource path " + resourcePath);
            }
            final File tempFile = File.createTempFile("libftgs", ".so");
            final OutputStream os = new FileOutputStream(tempFile);
            ByteStreams.copy(is, os);
            os.close();
            is.close();
            System.load(tempFile.getAbsolutePath());
            // noinspection ResultOfMethodCallIgnored
            tempFile.delete();
        } catch (Throwable e) {
            log.warn("unable to load libftgs using class loader, looking in java.library.path", e);
            System.loadLibrary("ftgs"); // if this fails it throws UnsatisfiedLinkError
        }
    }
    private native static void nativeInit();

    private final AtomicReference<CyclicBarrier> writeFTGSSplitBarrier = new AtomicReference<>();
    private Socket[] ftgsOutputSockets = new Socket[256];

    private final MemoryReservationContext memory;

    private final ExecutorService executor;

    private final AtomicReference<Boolean> closed = new AtomicReference<>();

    private final long memoryClaimed;

    private final boolean useNativeFtgs;

    public MTImhotepLocalMultiSession(final ImhotepLocalSession[] sessions,
                                      final MemoryReservationContext memory,
                                      final ExecutorService executor,
                                      final AtomicLong tempFileSizeBytesLeft, boolean useNativeFtgs) throws ImhotepOutOfMemoryException {
        super(sessions, tempFileSizeBytesLeft);
        this.useNativeFtgs = useNativeFtgs;
        this.memory = memory;
        this.executor = executor;
        memoryClaimed = 0;

        if (!memory.claimMemory(memoryClaimed)) {
            //noinspection NewExceptionWithoutArguments
            throw new ImhotepOutOfMemoryException();
        }
    }
    
    @Override
    protected void preClose() {
        if (closed.compareAndSet(false, true)) {
            try {
                super.preClose();
            } finally {
                closeFTGSSockets();
                memory.releaseMemory(memoryClaimed);
                // don't want to shut down the executor since it is re-used
            }
        }
    }

    /**
     * Closes the sockets silently. Guaranteed to not throw Exceptions
     */
    private void closeFTGSSockets() {
        Closeables2.closeAll(Arrays.asList(ftgsOutputSockets), log);
    }

    @Override
    public void writeFTGSIteratorSplit(final String[] intFields,
                                       final String[] stringFields,
                                       final int splitIndex,
                                       final int numSplits,
                                       final Socket socket) {
        // save socket
        ftgsOutputSockets[splitIndex] = socket;

        final CyclicBarrier newBarrier = new CyclicBarrier(numSplits, new Runnable() {
            @Override
            public void run() {
                // run service
                final FlamdexReader[] readers = new FlamdexReader[sessions.length];
                final MultiCache[] nativeCaches = new MultiCache[sessions.length];
                final MultiCacheConfig config = new MultiCacheConfig(sessions.length);
                for (int i = 0; i < sessions.length; i++) {
                    readers[i] = sessions[i].getReader();
                    nativeCaches[i] = sessions[i].buildMulticache(config, i);
                }

                final NativeFtgsRunner runner = new NativeFtgsRunner(readers,
                                                                     nativeCaches,
                                                                     getNumGroups(),
                                                                     numStats);
                try {
                    runner.run(intFields, stringFields, numSplits, ftgsOutputSockets);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });

        // agree on a single barrier
        CyclicBarrier barrier = writeFTGSSplitBarrier.get();
        if (barrier == null) {
            if (writeFTGSSplitBarrier.compareAndSet(null, newBarrier)) {
                barrier = writeFTGSSplitBarrier.get();
            } else {
                barrier = writeFTGSSplitBarrier.get();
            }
        }

        //There is a potential race condition between ftgsOutputSockets[i] being assigned and the sockets being closed
        // when <code>close()</code> is called. If this method is called concurrently with close it's possible that
        //ftgsOutputSockets[i] will be assigned after it has already been determined to be null in close. This will
        //cause the socket to not be closed. By checking if the session is closed after the assignment we guarantee
        //that either close() will close all sockets correctly (if closed is false here) or that we will close all the
        //sockets if the session was closed simultaneously with this method being called (if closed is true here)
        if (closed.get()) {
            closeFTGSSockets();
            throw new IllegalStateException("the session was closed before getting all the splits!");
        }
//        if (latch.getCount() == 0) {
//            throw new IllegalStateException("Latch was already set to zero!");
//        }

        // now run the ftgs on the final thread
        try {
            barrier.await();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (BrokenBarrierException e) {
            throw new RuntimeException(e);
        }

        // now reset the barrier value (yes, every thread will do it)
        writeFTGSSplitBarrier.set(null);
    }

    @Override
    protected void postClose() {
        if (memory.usedMemory() > 0) {
            log.error("MTImhotepMultiSession is leaking! usedMemory = "+memory.usedMemory());
        }
        Closeables2.closeQuietly(memory, log);
    }

    @Override
    protected ImhotepRemoteSession createImhotepRemoteSession(InetSocketAddress address, String sessionId, AtomicLong tempFileSizeBytesLeft) {
        return new ImhotepRemoteSession(address.getHostName(), address.getPort(), sessionId, tempFileSizeBytesLeft, useNativeFtgs);
    }

    @Override
    protected <E, T> void execute(final T[] ret, E[] things, final ThrowingFunction<? super E, ? extends T> function) throws ExecutionException {
        final List<Future<T>> futures = Lists.newArrayListWithCapacity(things.length);
        for (final E thing : things) {
            futures.add(executor.submit(new Callable<T>() {
                @Override
                public T call() throws Exception {
                    return function.apply(thing);
                }
            }));
        }

        Throwable t = null;
        for (int i = 0; i < futures.size(); ++i) {
            try {
                ret[i] = futures.get(i).get();
            } catch (final Throwable t2) {
                t = t2;
            }
            if (t != null) {
                safeClose();
                throw Throwables2.propagate(t, ExecutionException.class);
            }
        }
    }
}