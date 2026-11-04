package com.cinebook.service;

import com.cinebook.exception.BookingNotFoundException;
import com.cinebook.exception.ShowNotFoundException;
import com.cinebook.model.Booking;
import com.cinebook.model.Seat;
import com.cinebook.model.Show;
import com.cinebook.repo.BookingRepository;
import com.cinebook.repo.BookingSeatRepository;
import com.cinebook.repo.ShowRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * Service Facade representing the Theatre.
 * Manages composition of shows, seats, and all bookings.
 */
@Service
public class Theatre {

    private final ShowRepository showRepository;
    private final BookingRepository bookingRepository;
    private final BookingSeatRepository bookingSeatRepository;
    private final BookingService bookingService;

    public Theatre(ShowRepository showRepository,
                   BookingRepository bookingRepository,
                   BookingSeatRepository bookingSeatRepository,
                   BookingService bookingService) {
        this.showRepository = showRepository;
        this.bookingRepository = bookingRepository;
        this.bookingSeatRepository = bookingSeatRepository;
        this.bookingService = bookingService;
    }

    /**
     * Lists all scheduled shows.
     */
    @Transactional(readOnly = true)
    public List<Show> getAllShows() {
        return showRepository.findAllByOrderByIdAsc();
    }

    /**
     * Retrieves a show with its configured seats.
     */
    @Transactional(readOnly = true)
    public Show getShowById(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("Show ID cannot be null");
        }
        return showRepository.findByIdWithSeats(id)
                .orElseThrow(() -> new ShowNotFoundException("Show with ID " + id + " does not exist."));
    }

    /**
     * Retrieves the 5x8 seat grid for a show with current booked statuses marked.
     * Uses Show.buildSeatGrid() to generate the 2D array.
     */
    @Transactional(readOnly = true)
    public Seat[][] getSeatGridForShow(Long showId) {
        Show show = getShowById(showId);
        Set<Long> bookedSeatIds = bookingSeatRepository.findBookedSeatIdsByShowId(showId);

        for (Seat seat : show.getSeats()) {
            seat.setBooked(bookedSeatIds.contains(seat.getId()));
        }

        return show.buildSeatGrid();
    }

    /**
     * Books selected seats for a show through the Bookable BookingService.
     */
    public Booking bookSeats(Long showId, List<String> seatCodes, String couponCode) {
        return bookingService.bookSeats(showId, seatCodes, couponCode);
    }

    /**
     * Cancels a booking and triggers refund calculation.
     */
    public Booking cancelBooking(Long bookingId) {
        return bookingService.cancelBooking(bookingId);
    }

    /**
     * Retrieves a booking by its primary numeric key with full details.
     */
    @Transactional(readOnly = true)
    public Booking getBookingById(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("Booking ID cannot be null");
        }
        return bookingRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new BookingNotFoundException("Booking with ID BK" + id + " not found."));
    }

    /**
     * Finds a booking by code or ID (accepts "BK1001", "bk1001", or "1001").
     */
    @Transactional(readOnly = true)
    public Booking findBookingByCodeOrId(String input) {
        Long id = Booking.parseBookingId(input);
        return getBookingById(id);
    }

    /**
     * Returns count of active booked seats for a show.
     */
    @Transactional(readOnly = true)
    public int getBookedSeatCount(Long showId) {
        return bookingSeatRepository.findBookedSeatIdsByShowId(showId).size();
    }

    /**
     * Returns count of available seats for a show (40 - booked).
     */
    @Transactional(readOnly = true)
    public int getAvailableSeatCount(Long showId) {
        int totalSeats = Show.GRID_ROWS * Show.GRID_COLS;
        return totalSeats - getBookedSeatCount(showId);
    }
}

