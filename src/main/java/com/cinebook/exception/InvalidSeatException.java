package com.cinebook.exception;

/**
 * Thrown when an invalid seat code is supplied (e.g. Z9, A9, C0, empty, duplicate).
 */
public class InvalidSeatException extends RuntimeException {

    public InvalidSeatException(String message) {
        super(message);
    }
}

