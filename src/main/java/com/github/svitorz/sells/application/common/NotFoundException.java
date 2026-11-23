package com.github.svitorz.sells.application.common;

/** The requested record does not exist. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
