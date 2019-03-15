package com.indeed.imhotep.service;

import com.google.common.collect.ComparisonChain;
import com.indeed.imhotep.ShardDir;
import com.indeed.imhotep.client.Host;

import javax.annotation.Nullable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public interface ShardLocator {
    public static ShardLocator appendingSQARShardLocator(final Path rootDir, final Host myHost) {
        return new ShardLocator() {
            private final Host myServer = myHost;
            @Override
            public Optional<Path> locateShard(final String dataset, final ShardHostInfo shardInfo) {
                final String shardName = shardInfo.getShardName();
                final String archiveName;
                if (shardName.endsWith(".sqar")) {
                    archiveName = shardName;
                } else {
                    archiveName = shardName + ".sqar";
                }
                return locateAndCheck(
                        rootDir,
                        dataset,
                        archiveName,
                        myServer,
                        shardInfo.getShardOwner()
                );
            }
        };
    }

    public static ShardLocator pathShardLocator(final Path rootDir, final Host myHost) {
        return new ShardLocator() {
            private final Host myServer = myHost;
            @Override
            public Optional<Path> locateShard(final String dataset, final ShardHostInfo shardInfo) {
                return locateAndCheck(
                        rootDir,
                        dataset,
                        shardInfo.getShardName(),
                        myServer,
                        shardInfo.getShardOwner()
                );
            }
        };
    }

    /**
     * Call multiple shard locators, and return a shard that has the latest version.
     * Nulls in the arguments are just ignored.
     */
    public static ShardLocator combine(final ShardLocator... shardLocators) {
        return new CombinedShardLocator(
                Arrays.stream(shardLocators)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList())
        );
    }

    /**
     * convert a local path to p2p path
     */
    static Path toP2PPath(final Path rootPath, final Path localPath, final Host shardOwner) {
        final Path fakeP2PPath = rootPath
                .resolve("remote")
                .resolve(shardOwner.toString())
                .resolve(localPath.toString());
        return Paths.get(fakeP2PPath.toUri());
    }

    static Optional<Path> locateAndCheck(
            final Path rootDir,
            final String dataset,
            final String shardName,
            final Host shardServer,
            @Nullable final Host shardOwner) {
        Path shardPath = rootDir.resolve(dataset).resolve(shardName);
        // no need generate P2PCachingPath if shard on the same server
        if (shardOwner != null && !shardServer.equals(shardOwner)) {
            shardPath = toP2PPath(rootDir, shardPath, shardOwner);
        }

        if (Files.exists(shardPath)) {
            return Optional.of(shardPath);
        } else {
            return Optional.empty();
        }
    }

    /**
     * @param dataset
     * @param shardHostInfo the infromation about shardName, shardServer and shardOwner
     */
    Optional<Path> locateShard(final String dataset, final ShardHostInfo shardHostInfo);

    static class CombinedShardLocator implements ShardLocator {
        private static final Comparator<ShardDir> SHARD_DIR_COMPARATOR = (lhs, rhs) -> {
            return ComparisonChain.start()
                    .compare(lhs.getVersion(), rhs.getVersion())
                    .compare(lhs.getName(), rhs.getName())
                    .compare(lhs.getIndexDir(), rhs.getIndexDir(), Comparator.comparing(Path::toString))
                    .result();
        };

        final List<ShardLocator> shardLocators;

        private CombinedShardLocator(final List<ShardLocator> shardLocators) {
            this.shardLocators = shardLocators;
        }

        @Override
        public Optional<Path> locateShard(final String dataset, final ShardHostInfo shardInfo) {
            return shardLocators.stream()
                    .map(shardLocator -> shardLocator.locateShard(dataset, shardInfo))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .map(ShardDir::new)
                    .max(SHARD_DIR_COMPARATOR)
                    .map(ShardDir::getIndexDir);
        }
    }
}
