package com.cinebook.exception;

/**
 * Thrown when a booking cannot be located by its ID or formatted code.
 */
public class BookingNotFoundException extends RuntimeException {

    public BookingNotFoundException(String message) {
        super(message);
    }
}

