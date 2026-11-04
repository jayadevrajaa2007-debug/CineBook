package com.cinebook.exception;

/**
 * Thrown when attempting to cancel a booking that is already cancelled.
 */
public class BookingAlreadyCancelledException extends RuntimeException {

    public BookingAlreadyCancelledException(String message) {
        super(message);
    }
}

