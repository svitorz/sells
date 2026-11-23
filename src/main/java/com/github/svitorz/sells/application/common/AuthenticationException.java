package com.github.svitorz.sells.application.common;

/** Login failed. */
public class AuthenticationException extends RuntimeException {

    public AuthenticationException(String message) {
        super(message);
    }
}
