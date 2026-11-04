package com.cinebook.exception;

/**
 * Thrown when one or more requested seats are already booked.
 */
public class SeatUnavailableException extends RuntimeException {

    public SeatUnavailableException(String message) {
        super(message);
    }
}

