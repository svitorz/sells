package com.github.svitorz.sells.infrastructure.database;

import java.nio.file.Path;

public class InstanceAlreadyRunningException extends RuntimeException {

    public InstanceAlreadyRunningException(Path lockFile) {
        super("Another Sells instance is already using " + lockFile.getParent());
    }
}
