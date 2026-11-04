package com.cinebook.service;

import com.cinebook.exception.BookingAlreadyCancelledException;
import com.cinebook.exception.BookingNotFoundException;
import com.cinebook.exception.InvalidSeatException;
import com.cinebook.exception.SeatUnavailableException;
import com.cinebook.model.*;
import com.cinebook.repo.BookingRepository;
import com.cinebook.repo.BookingSeatRepository;
import com.cinebook.repo.SeatRepository;
import com.cinebook.repo.ShowRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for BookingService:
 * 1. Invalid seat codes (Z9, A9, C0, "", duplicates) are rejected.
 * 2. One taken seat means nothing is booked (all-or-nothing).
 * 5. Cancellation refunds exactly 80%, sets status CANCELLED and frees the seats.
 * 6. Cancelling twice is rejected.
 * 7. Seats of different shows are independent.
 */
@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock
    private ShowRepository showRepository;

    @Mock
    private SeatRepository seatRepository;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private BookingSeatRepository bookingSeatRepository;

    @Mock
    private PricingService pricingService;

    @InjectMocks
    private BookingService bookingService;

    private Show show1;
    private Show show2;
    private Seat seatC3_Show1;
    private Seat seatC4_Show1;
    private Seat seatC3_Show2;

    @BeforeEach
    void setUp() {
        show1 = createCompleteShow(1L, "Interstellar", LocalTime.of(19, 30));
        show2 = createCompleteShow(2L, "Inception", LocalTime.of(16, 0));

        seatC3_Show1 = findSeatByCode(show1, "C3");
        seatC4_Show1 = findSeatByCode(show1, "C4");
        seatC3_Show2 = findSeatByCode(show2, "C3");
    }

    private Show createCompleteShow(Long id, String movie, LocalTime time) {
        Show show = new Show(id, movie, time);
        long seatIdCounter = id * 100;
        char[] rows = {'A', 'B', 'C', 'D', 'E'};
        for (char r : rows) {
            String rowStr = String.valueOf(r);
            SeatType type = SeatType.fromRowLabel(rowStr);
            for (int s = 1; s <= 8; s++) {
                Seat seat = new Seat(++seatIdCounter, show, rowStr, s, type);
                show.addSeat(seat);
            }
        }
        return show;
    }

    private Seat findSeatByCode(Show show, String code) {
        return show.getSeats().stream()
                .filter(s -> s.getCode().equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow();
    }

    // =========================================================================
    // TEST 1: Invalid Seat Codes
    // =========================================================================

    @ParameterizedTest
    @ValueSource(strings = {"Z9", "A9", "C0", "", "   ", "X1", "12", "C9", "E0"})
    @DisplayName("Test 1a: Invalid seat codes (Z9, A9, C0, empty) are rejected")
    void testInvalidSeatCodesRejected(String invalidCode) {
        assertThrows(InvalidSeatException.class, () ->
                bookingService.bookSeats(1L, List.of(invalidCode), null));
    }

    @Test
    @DisplayName("Test 1b: Empty seat list is rejected")
    void testEmptySeatListRejected() {
        assertThrows(InvalidSeatException.class, () ->
                bookingService.bookSeats(1L, Collections.emptyList(), null));
    }

    @Test
    @DisplayName("Test 1c: Duplicate seat codes in a single request are rejected")
    void testDuplicateSeatCodesRejected() {
        assertThrows(InvalidSeatException.class, () ->
                bookingService.bookSeats(1L, List.of("C3", "C3"), null));

        assertThrows(InvalidSeatException.class, () ->
                bookingService.bookSeats(1L, List.of("c3", "C3"), null));
    }

    // =========================================================================
    // TEST 2: All-Or-Nothing Booking
    // =========================================================================

    @Test
    @DisplayName("Test 2: One taken seat means nothing is booked (all-or-nothing)")
    void testAllOrNothingBooking() {
        when(showRepository.findByIdWithSeats(1L)).thenReturn(Optional.of(show1));
        when(seatRepository.findByIdInOrderByIdAscWithLock(anyList()))
                .thenReturn(List.of(seatC3_Show1, seatC4_Show1));

        // Seat C3 is already occupied in an active booking
        Booking existingBooking = new Booking(show1, null, new BigDecimal("250.00"), BigDecimal.ZERO, new BigDecimal("250.00"));
        existingBooking.setId(1001L);
        BookingSeat bookedSeatC3 = new BookingSeat(existingBooking, seatC3_Show1);
        bookedSeatC3.setActive(true);

        when(bookingSeatRepository.findActiveBySeatIdIn(anyList()))
                .thenReturn(List.of(bookedSeatC3));

        // Attempting to book C3 and C4 must fail because C3 is taken
        SeatUnavailableException ex = assertThrows(SeatUnavailableException.class, () ->
                bookingService.bookSeats(1L, List.of("C3", "C4"), null));

        assertTrue(ex.getMessage().contains("already booked"), "Exception must mention already booked");
        assertTrue(ex.getMessage().contains("all-or-nothing"), "Exception must mention all-or-nothing");

        // Verify that neither booking nor bookingSeats were saved
        verify(bookingRepository, never()).save(any(Booking.class));
        verify(bookingSeatRepository, never()).save(any(BookingSeat.class));
    }

    // =========================================================================
    // TEST 5: Cancellation & 80% Refund
    // =========================================================================

    @Test
    @DisplayName("Test 5: Cancellation refunds exactly 80%, sets status CANCELLED and frees seats")
    void testCancellationRefundsEightyPercent() {
        Booking booking = new Booking(show1, "CINE10", new BigDecimal("500.00"), new BigDecimal("50.00"), new BigDecimal("450.00"));
        booking.setId(1001L);

        BookingSeat bs1 = new BookingSeat(booking, seatC3_Show1);
        BookingSeat bs2 = new BookingSeat(booking, seatC4_Show1);
        booking.addBookingSeat(bs1);
        booking.addBookingSeat(bs2);

        assertTrue(bs1.isActive());
        assertTrue(bs2.isActive());

        when(bookingRepository.findByIdWithDetails(1001L)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Booking cancelled = bookingService.cancelBooking(1001L);

        // Refund must be exactly 80% of Rs. 450.00 = Rs. 360.00
        assertEquals(new BigDecimal("360.00"), cancelled.getRefundAmount());
        assertEquals(BookingStatus.CANCELLED, cancelled.getStatus());
        assertNotNull(cancelled.getCancelledAt());

        // Seats must be freed (active = false)
        assertFalse(bs1.isActive(), "Seat C3 must be freed (active=false)");
        assertFalse(bs2.isActive(), "Seat C4 must be freed (active=false)");

        verify(bookingSeatRepository, times(2)).save(any(BookingSeat.class));
        verify(bookingRepository).save(booking);
    }

    // =========================================================================
    // TEST 6: Reject Cancelling Twice
    // =========================================================================

    @Test
    @DisplayName("Test 6: Cancelling twice is rejected with BookingAlreadyCancelledException")
    void testCancelTwiceRejected() {
        Booking booking = new Booking(show1, null, new BigDecimal("500.00"), BigDecimal.ZERO, new BigDecimal("500.00"));
        booking.setId(1001L);
        booking.setStatus(BookingStatus.CANCELLED); // Already cancelled

        when(bookingRepository.findByIdWithDetails(1001L)).thenReturn(Optional.of(booking));

        assertThrows(BookingAlreadyCancelledException.class, () ->
                bookingService.cancelBooking(1001L));

        verify(bookingSeatRepository, never()).save(any(BookingSeat.class));
    }

    // =========================================================================
    // TEST 7: Show Seat Independence
    // =========================================================================

    @Test
    @DisplayName("Test 7: Seats of different shows are completely independent")
    void testSeatsOfDifferentShowsAreIndependent() {
        // Seat C3 in Show 1 and Show 2 have different primary keys and different Show parents
        assertNotEquals(seatC3_Show1.getId(), seatC3_Show2.getId());
        assertNotEquals(seatC3_Show1.getShow().getId(), seatC3_Show2.getShow().getId());

        // Both are C3
        assertEquals("C3", seatC3_Show1.getCode());
        assertEquals("C3", seatC3_Show2.getCode());

        // Setup mock for booking C3 in Show 1
        when(showRepository.findByIdWithSeats(1L)).thenReturn(Optional.of(show1));
        when(seatRepository.findByIdInOrderByIdAscWithLock(List.of(seatC3_Show1.getId())))
                .thenReturn(List.of(seatC3_Show1));
        when(bookingSeatRepository.findActiveBySeatIdIn(List.of(seatC3_Show1.getId())))
                .thenReturn(Collections.emptyList());
        when(pricingService.calculateTotal(List.of(seatC3_Show1), null))
                .thenReturn(new PricingResult(new BigDecimal("250.00"), BigDecimal.ZERO, new BigDecimal("250.00"), false, null, 0, null));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> {
            Booking b = invocation.getArgument(0);
            b.setId(1001L);
            return b;
        });

        Booking b1 = bookingService.bookSeats(1L, List.of("C3"), null);
        assertNotNull(b1);
        assertEquals(1L, b1.getShow().getId());

        // Setup mock for booking C3 in Show 2 (independent)
        when(showRepository.findByIdWithSeats(2L)).thenReturn(Optional.of(show2));
        when(seatRepository.findByIdInOrderByIdAscWithLock(List.of(seatC3_Show2.getId())))
                .thenReturn(List.of(seatC3_Show2));
        when(bookingSeatRepository.findActiveBySeatIdIn(List.of(seatC3_Show2.getId())))
                .thenReturn(Collections.emptyList()); // Free in show 2!
        when(pricingService.calculateTotal(List.of(seatC3_Show2), null))
                .thenReturn(new PricingResult(new BigDecimal("250.00"), BigDecimal.ZERO, new BigDecimal("250.00"), false, null, 0, null));

        Booking b2 = bookingService.bookSeats(2L, List.of("C3"), null);
        assertNotNull(b2);
        assertEquals(2L, b2.getShow().getId());
    }

    @Test
    @DisplayName("Verify non-existent booking throws BookingNotFoundException")
    void testNonExistentBookingThrows() {
        when(bookingRepository.findByIdWithDetails(9999L)).thenReturn(Optional.empty());

        assertThrows(BookingNotFoundException.class, () ->
                bookingService.cancelBooking(9999L));
    }
}

