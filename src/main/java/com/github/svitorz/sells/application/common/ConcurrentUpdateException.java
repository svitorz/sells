package com.github.svitorz.sells.application.common;

/** The record was changed by another operation (version conflict). Thrown by the persistence adapter. */
public class ConcurrentUpdateException extends RuntimeException {

    public ConcurrentUpdateException(String message) {
        super(message);
    }
}
