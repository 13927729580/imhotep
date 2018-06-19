/*
 * Copyright (C) 2018 Indeed Inc.
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
 package com.indeed.imhotep;

import com.indeed.imhotep.api.FTGSIterator;
import com.indeed.imhotep.io.MultiFile;
import com.indeed.imhotep.io.TempFileSizeLimitExceededException;
import com.indeed.imhotep.io.WriteLimitExceededException;
import com.indeed.imhotep.service.FTGSOutputStreamWriter;
import com.indeed.util.core.Throwables2;
import com.indeed.util.core.hash.MurmurHash;
import com.indeed.util.core.io.Closeables2;
import org.apache.log4j.Logger;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
* @author jplaisance
*/
public final class FTGSSplitter implements Closeable {
    private static final Logger log = Logger.getLogger(FTGSSplitter.class);

    private final FTGSIterator iterator;

    private final int numSplits;

    private final FTGSOutputStreamWriter[] outputs;
    private final OutputStream[] outputStreams;
    private final FTGSIterator[] ftgsIterators;

    private final AtomicBoolean done = new AtomicBoolean(false);

    private final int largePrime;

    public FTGSSplitter(final FTGSIterator ftgsIterator,
                        final int numSplits,
                        final int largePrime) throws IOException {
        this(ftgsIterator, numSplits, largePrime, null);
    }

    public FTGSSplitter(final FTGSIterator ftgsIterator,
                        final int numSplits,
                        final int largePrime,
                        final AtomicLong tempFileSizeBytesLeft) throws IOException {
        this.iterator = ftgsIterator;
        this.numSplits = numSplits;
        final int numStats = iterator.getNumStats();
        final int numGroups = ftgsIterator.getNumGroups();
        this.largePrime = largePrime;
        outputs = new FTGSOutputStreamWriter[numSplits];
        outputStreams = new OutputStream[numSplits];
        ftgsIterators = new FTGSIterator[numSplits];
        final AtomicInteger doneCounter = new AtomicInteger();
        final File file = File.createTempFile("ftgsSplitter", ".tmp");
        try {
            final MultiFile multiFile;
            try {
                multiFile = MultiFile.create(file, numSplits, 256 * 1024, tempFileSizeBytesLeft);
            } finally {
                file.delete();
            }
            for (int i = 0; i < numSplits; i++) {
                outputStreams[i] = multiFile.getOutputStream(i);
                outputs[i] = new FTGSOutputStreamWriter(outputStreams[i]);
                ftgsIterators[i] = new SplitterFTGSIterator(multiFile.getInputStream(i), numStats, numGroups, doneCounter);
            }
            run();
        } catch (final Throwable t) {
            try {
                close();
            } finally {
                throw Throwables2.propagate(t, IOException.class);
            }
        }
    }

    public FTGSIterator[] getFtgsIterators() {
        return ftgsIterators;
    }

    private void run() throws IOException {
        try {
            final long[] statBuf = new long[iterator.getNumStats()];
            while (iterator.nextField()) {
                final boolean fieldIsIntType = iterator.fieldIsIntType();
                for (final FTGSOutputStreamWriter output : outputs) {
                    output.switchField(iterator.fieldName(), fieldIsIntType);
                }

                while (iterator.nextTerm()) {
                    final FTGSOutputStreamWriter output;
                    final int split;
                    if (fieldIsIntType) {
                        final long term = iterator.termIntVal();
                        split = (int) ((term*largePrime+12345 & Integer.MAX_VALUE) >> 16) % numSplits;
                        output = outputs[split];
                        output.switchIntTerm(term, iterator.termDocFreq());
                    } else {
                        split = hashStringTerm(iterator.termStringBytes(),
                                               iterator.termStringLength());
                        output = outputs[split];
                        output.switchBytesTerm(iterator.termStringBytes(),
                                               iterator.termStringLength(),
                                               iterator.termDocFreq());
                    }
                    while (iterator.nextGroup()) {
                        output.switchGroup(iterator.group());
                        iterator.groupStats(statBuf);
                        for (final long stat : statBuf) {
                            output.addStat(stat);
                        }
                    }
                }
            }
            for (final FTGSOutputStreamWriter output : outputs) {
                output.close();
            }
        } catch (final Throwable t) {
            try {
                close();
            } finally {
                if (t instanceof WriteLimitExceededException) {
                    throw new TempFileSizeLimitExceededException(t);
                }
                throw Throwables2.propagate(t, IOException.class);
            }
        } finally {
            Closeables2.closeQuietly(iterator, log);
        }
    }

    private int hashStringTerm(final byte[] termStringBytes, final int termStringLength) {
        return ((MurmurHash.hash32(termStringBytes, 0, termStringLength) *
                 largePrime+12345 & 0x7FFFFFFF) >> 16) % numSplits;
    }

    public void close() {
        if (done.compareAndSet(false, true)) {
            final Closeable closeIterators = Closeables2.forArray(log, ftgsIterators);
            final Closeable closeOutputs = Closeables2.forArray(log, outputs);
            final Closeable closeOutputStreams = Closeables2.forArray(log, outputStreams);
            Closeables2.closeAll(log, iterator, closeIterators,
                    closeOutputs, closeOutputStreams);
        }
    }

    public boolean isClosed() {
        return done.get();
    }

    private class SplitterFTGSIterator extends InputStreamFTGSIterator {
        private boolean closed = false;
        private AtomicInteger doneCounter;

        SplitterFTGSIterator(final InputStream in,
                             final int numStats,
                             final int numGroups,
                             final AtomicInteger doneCounter) {
            super(in, numStats, numGroups);
            this.doneCounter = doneCounter;
        }

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                super.close();
                if (doneCounter.incrementAndGet() == numSplits) {
                    FTGSSplitter.this.close();
                }
            }
        }
    }
}
