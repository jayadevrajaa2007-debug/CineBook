package com.cinebook.service;

import com.cinebook.model.Booking;
import java.util.List;

/**
 * Interface defining ticket booking and cancellation operations.
 * Implemented by BookingService.
 */
public interface Bookable {

    /**
     * Books one or more seats for a specified show.
     *
     * @param showId Identifier of the show
     * @param seatCodes List of seat codes (e.g. ["C3", "C4"])
     * @param couponCode Optional coupon code (e.g. "CINE10")
     * @return Confirmed Booking entity
     */
    Booking bookSeats(Long showId, List<String> seatCodes, String couponCode);

    /**
     * Cancels an existing booking, freeing the seats and calculating refund.
     *
     * @param bookingId Numeric ID of the booking to cancel
     * @return Cancelled Booking entity with refundAmount populated
     */
    Booking cancelBooking(Long bookingId);
}

