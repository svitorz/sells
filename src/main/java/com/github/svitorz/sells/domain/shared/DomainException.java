package com.github.svitorz.sells.domain.shared;

/** A business rule was violated. The message is shown to the user, in Portuguese. */
public class DomainException extends RuntimeException {

    public DomainException(String message) {
        super(message);
    }
}
