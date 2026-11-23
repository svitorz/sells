package com.github.svitorz.sells.infrastructure.database;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InstanceLockTest {

    @TempDir
    Path dir;

    @Test
    void secondAcquireFailsWhileFirstIsHeld() throws Exception {
        Path lockFile = dir.resolve("sells.lock");

        try (InstanceLock first = InstanceLock.acquire(lockFile)) {
            assertThrows(InstanceAlreadyRunningException.class, () -> InstanceLock.acquire(lockFile));
        }
    }

    @Test
    void canAcquireAgainAfterClose() throws Exception {
        Path lockFile = dir.resolve("sells.lock");

        InstanceLock first = InstanceLock.acquire(lockFile);
        first.close();

        assertDoesNotThrow(() -> InstanceLock.acquire(lockFile).close());
    }
}
