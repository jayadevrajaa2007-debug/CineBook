package com.cinebook.service;

import com.cinebook.exception.BookingAlreadyCancelledException;
import com.cinebook.exception.BookingNotFoundException;
import com.cinebook.exception.InvalidSeatException;
import com.cinebook.exception.SeatUnavailableException;
import com.cinebook.exception.ShowNotFoundException;
import com.cinebook.model.*;
import com.cinebook.repo.BookingRepository;
import com.cinebook.repo.BookingSeatRepository;
import com.cinebook.repo.SeatRepository;
import com.cinebook.repo.ShowRepository;
import com.cinebook.sheets.BookingSavedEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Implementation of Bookable interface.
 * Coordinates seat validation, pessimistic row locking, pricing, booking creation,
 * and cancellations with transactional guarantees.
 */
@Service
public class BookingService implements Bookable {

    private static final Pattern SEAT_CODE_PATTERN = Pattern.compile("^[A-E][1-8]$");

    private final ShowRepository showRepository;
    private final SeatRepository seatRepository;
    private final BookingRepository bookingRepository;
    private final BookingSeatRepository bookingSeatRepository;
    private final PricingService pricingService;
    private final ApplicationEventPublisher eventPublisher;

    public BookingService(ShowRepository showRepository,
                          SeatRepository seatRepository,
                          BookingRepository bookingRepository,
                          BookingSeatRepository bookingSeatRepository,
                          PricingService pricingService) {
        this(showRepository, seatRepository, bookingRepository, bookingSeatRepository, pricingService, null);
    }

    @Autowired
    public BookingService(ShowRepository showRepository,
                          SeatRepository seatRepository,
                          BookingRepository bookingRepository,
                          BookingSeatRepository bookingSeatRepository,
                          PricingService pricingService,
                          @Autowired(required = false) ApplicationEventPublisher eventPublisher) {
        this.showRepository = showRepository;
        this.seatRepository = seatRepository;
        this.bookingRepository = bookingRepository;
        this.bookingSeatRepository = bookingSeatRepository;
        this.pricingService = pricingService;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public Booking bookSeats(Long showId, List<String> seatCodes, String couponCode) {
        if (showId == null) {
            throw new IllegalArgumentException("Show ID must not be null");
        }

        // 1. Normalize and validate input seat codes
        List<String> normalizedCodes = normalizeAndValidateSeatCodes(seatCodes);

        // 2. Load Show and seats
        Show show = showRepository.findByIdWithSeats(showId)
                .orElseThrow(() -> new ShowNotFoundException("Show with ID " + showId + " does not exist."));

        // 3. Validate seat codes against Show's 2D grid using nested loops (OOP requirement)
        Seat[][] grid = show.buildSeatGrid();
        List<Seat> matchedSeats = new ArrayList<>();
        for (String code : normalizedCodes) {
            Seat found = null;
            for (int r = 0; r < Show.GRID_ROWS; r++) {
                for (int c = 0; c < Show.GRID_COLS; c++) {
                    Seat s = grid[r][c];
                    if (s != null && s.getCode().equalsIgnoreCase(code)) {
                        found = s;
                        break;
                    }
                }
                if (found != null) {
                    break;
                }
            }
            if (found == null) {
                throw new InvalidSeatException("Seat code '" + code + "' does not exist in the cinema layout.");
            }
            matchedSeats.add(found);
        }

        // 4. Concurrency: Lock selected seat rows ordered by seat ID to avoid deadlocks
        List<Long> seatIds = matchedSeats.stream()
                .map(Seat::getId)
                .sorted()
                .collect(Collectors.toList());

        List<Seat> lockedSeats = seatRepository.findByIdInOrderByIdAscWithLock(seatIds);
        if (lockedSeats.size() != seatIds.size()) {
            throw new InvalidSeatException("One or more selected seats could not be found for locking.");
        }

        // 5. All-or-nothing availability check
        List<BookingSeat> activeBookings = bookingSeatRepository.findActiveBySeatIdIn(seatIds);
        if (!activeBookings.isEmpty()) {
            String unavailableCodes = activeBookings.stream()
                    .map(bs -> bs.getSeat().getCode())
                    .distinct()
                    .sorted()
                    .collect(Collectors.joining(", "));
            throw new SeatUnavailableException("Seat(s) already booked: " + unavailableCodes +
                    ". Booking is all-or-nothing; none were booked.");
        }

        // 6. Calculate total with PricingService
        PricingResult pricing = pricingService.calculateTotal(lockedSeats, couponCode);

        // 7. Persist booking and seat associations
        try {
            Booking booking = new Booking(
                    show,
                    pricing.isCouponApplied() ? pricing.getCouponCode() : null,
                    pricing.getSubtotal(),
                    pricing.getDiscountAmount(),
                    pricing.getTotalAmount()
            );

            for (Seat seat : lockedSeats) {
                BookingSeat bookingSeat = new BookingSeat(booking, seat);
                booking.addBookingSeat(bookingSeat);
            }

            Booking saved = bookingRepository.save(booking);
            if (eventPublisher != null) {
                eventPublisher.publishEvent(new BookingSavedEvent(saved.getId()));
            }
            return saved;
        } catch (DataIntegrityViolationException ex) {
            // Converts database unique constraint violation (active_seat_id) into friendly message
            throw new SeatUnavailableException("One or more selected seats were just booked by another guest. Please choose different seats.");
        }
    }

    @Override
    @Transactional
    public Booking cancelBooking(Long bookingId) {
        if (bookingId == null) {
            throw new IllegalArgumentException("Booking ID must not be null");
        }

        Booking booking = bookingRepository.findByIdWithDetails(bookingId)
                .orElseThrow(() -> new BookingNotFoundException("Booking with ID BK" + bookingId + " not found."));

        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new BookingAlreadyCancelledException("Booking BK" + bookingId + " has already been cancelled.");
        }

        // Calculate 80% refund (scale 2, RoundingMode.HALF_UP)
        BigDecimal refundAmount = booking.getTotalAmount()
                .multiply(BigDecimal.valueOf(Booking.REFUND_PERCENT))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        booking.setStatus(BookingStatus.CANCELLED);
        booking.setCancelledAt(LocalDateTime.now());
        booking.setRefundAmount(refundAmount);
        booking.setSheetSyncStatus(SheetSyncStatus.PENDING);

        // Free the seats by marking active = false
        for (BookingSeat bookingSeat : booking.getBookingSeats()) {
            bookingSeat.setActive(false);
            bookingSeatRepository.save(bookingSeat);
        }

        Booking saved = bookingRepository.save(booking);
        if (eventPublisher != null) {
            eventPublisher.publishEvent(new BookingSavedEvent(saved.getId()));
        }
        return saved;
    }

    /**
     * Normalizes and validates seat codes.
     * Trims, converts to uppercase, validates regex ^[A-E][1-8]$, and checks for duplicates.
     */
    public List<String> normalizeAndValidateSeatCodes(List<String> rawCodes) {
        if (rawCodes == null || rawCodes.isEmpty()) {
            throw new InvalidSeatException("Seat selection cannot be empty. Please select at least one seat.");
        }

        List<String> normalized = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (String raw : rawCodes) {
            if (raw == null) {
                continue;
            }
            // Allow comma or space separated strings inside individual elements if user entered "c3, c4"
            String[] parts = raw.split("[,\\s]+");
            for (String part : parts) {
                String trimmed = part.trim().toUpperCase();
                if (trimmed.isEmpty()) {
                    continue;
                }
                if (!SEAT_CODE_PATTERN.matcher(trimmed).matches()) {
                    throw new InvalidSeatException("Invalid seat code: '" + part.trim() + "'. Expected format like A1, C3, E8 (Rows A-E, Seats 1-8).");
                }
                if (!seen.add(trimmed)) {
                    throw new InvalidSeatException("Duplicate seat code in selection: '" + trimmed + "'. Each seat can only be selected once.");
                }
                normalized.add(trimmed);
            }
        }

        if (normalized.isEmpty()) {
            throw new InvalidSeatException("Seat selection cannot be empty. Please select at least one seat.");
        }

        return normalized;
    }
}

