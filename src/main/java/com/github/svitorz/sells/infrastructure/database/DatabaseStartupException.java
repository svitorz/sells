package com.github.svitorz.sells.infrastructure.database;

public class DatabaseStartupException extends RuntimeException {

    public DatabaseStartupException(String message, Throwable cause) {
        super(message, cause);
    }
}
