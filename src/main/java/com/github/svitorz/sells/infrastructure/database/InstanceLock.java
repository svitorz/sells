package com.github.svitorz.sells.infrastructure.database;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Cross-process lock that keeps a single application per data directory
 * (ADR-0004, item 6).
 */
final class InstanceLock implements AutoCloseable {

    private final FileChannel channel;
    private final FileLock lock;

    private InstanceLock(FileChannel channel, FileLock lock) {
        this.channel = channel;
        this.lock = lock;
    }

    static InstanceLock acquire(Path lockFile) throws IOException {
        Files.createDirectories(lockFile.getParent());
        FileChannel channel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        try {
            FileLock lock = channel.tryLock();
            if (lock == null) {
                channel.close();
                throw new InstanceAlreadyRunningException(lockFile);
            }
            return new InstanceLock(channel, lock);
        } catch (OverlappingFileLockException e) {
            channel.close();
            throw new InstanceAlreadyRunningException(lockFile);
        }
    }

    @Override
    public void close() throws IOException {
        try {
            lock.release();
        } finally {
            channel.close();
        }
    }
}
