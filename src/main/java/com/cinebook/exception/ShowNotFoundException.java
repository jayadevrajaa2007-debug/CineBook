package com.cinebook.exception;

/**
 * Thrown when a show cannot be found.
 */
public class ShowNotFoundException extends RuntimeException {

    public ShowNotFoundException(String message) {
        super(message);
    }
}

