package com.github.svitorz.sells.application.common;

/** No session, or the session user is not allowed to run the action. */
public class AccessDeniedException extends RuntimeException {

    public AccessDeniedException(String message) {
        super(message);
    }
}
