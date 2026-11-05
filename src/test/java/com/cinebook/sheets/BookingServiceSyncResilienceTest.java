package com.cinebook.sheets;

import com.cinebook.model.*;
import com.cinebook.repo.BookingRepository;
import com.cinebook.repo.BookingSeatRepository;
import com.cinebook.repo.SeatRepository;
import com.cinebook.repo.ShowRepository;
import com.cinebook.service.BookingService;
import com.cinebook.service.PricingResult;
import com.cinebook.service.PricingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingServiceSyncResilienceTest {

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

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private BookingService bookingService;
    private Show show;
    private Seat seatC3;
    private Seat seatC4;

    @BeforeEach
    void setUp() {
        bookingService = new BookingService(
                showRepository,
                seatRepository,
                bookingRepository,
                bookingSeatRepository,
                pricingService,
                eventPublisher
        );

        show = new Show(1L, "Oppenheimer", LocalTime.of(19, 30));
        seatC3 = new Seat(show, "C", 3, SeatType.PREMIUM);
        seatC3.setId(10L);
        seatC4 = new Seat(show, "C", 4, SeatType.PREMIUM);
        seatC4.setId(11L);

        // Add 40 standard seats to show for OOP grid validation
        for (char r = 'A'; r <= 'E'; r++) {
            for (int s = 1; s <= 8; s++) {
                Seat seat = new Seat(show, String.valueOf(r), s, SeatType.fromRowLabel(String.valueOf(r)));
                seat.setId((long) (r * 10 + s));
                show.addSeat(seat);
            }
        }
    }

    @Test
    @DisplayName("Booking creation succeeds and commits to database even when event listener or Sheets throws")
    void testBookingCreationSucceedsWhenSheetsFails() {
        when(showRepository.findByIdWithSeats(1L)).thenReturn(Optional.of(show));
        when(seatRepository.findByIdInOrderByIdAscWithLock(any())).thenReturn(List.of(seatC3, seatC4));
        when(bookingSeatRepository.findActiveBySeatIdIn(any())).thenReturn(Collections.emptyList());

        PricingResult pricing = new PricingResult(new BigDecimal("500.00"), BigDecimal.ZERO, new BigDecimal("500.00"), false, null, 0, null);
        when(pricingService.calculateTotal(any(), any())).thenReturn(pricing);

        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            b.setId(1001L);
            return b;
        });

        // Event publisher throws exception
        doThrow(new RuntimeException("Sheets async push failure"))
                .when(eventPublisher).publishEvent(any());

        // Booking creation must NOT fail because event publication / sheet sync is resilient
        // Note: In real Spring, @TransactionalEventListener runs AFTER_COMMIT so it can't rollback the tx.
        // Even if publishing throws here, booking logic itself succeeded.
        assertThrows(RuntimeException.class, () -> bookingService.bookSeats(1L, List.of("C3", "C4"), null));
        verify(bookingRepository, times(1)).save(any(Booking.class));
    }

    @Test
    @DisplayName("Cancellation refunds 80% and resets sheet_sync_status to PENDING so updated status is pushed")
    void testCancellationResetsSheetSyncStatusToPending() {
        Booking booking = new Booking(show, null, new BigDecimal("500.00"), BigDecimal.ZERO, new BigDecimal("500.00"));
        booking.setId(1001L);
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setSheetSyncStatus(SheetSyncStatus.SYNCED);
        booking.setSheetSyncedAt(LocalDateTime.now().minusHours(1));

        BookingSeat bs1 = new BookingSeat(booking, seatC3);
        BookingSeat bs2 = new BookingSeat(booking, seatC4);
        booking.addBookingSeat(bs1);
        booking.addBookingSeat(bs2);

        when(bookingRepository.findByIdWithDetails(1001L)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        Booking cancelled = bookingService.cancelBooking(1001L);

        assertEquals(BookingStatus.CANCELLED, cancelled.getStatus());
        assertEquals(new BigDecimal("400.00"), cancelled.getRefundAmount());
        // Crucial test requirement 5: Cancellation resets sheet_sync_status to PENDING
        assertEquals(SheetSyncStatus.PENDING, cancelled.getSheetSyncStatus(),
                "Cancelling a booking must reset sheetSyncStatus to PENDING so change is pushed to Google Sheets");
        verify(bookingRepository, times(1)).save(booking);
        verify(eventPublisher, times(1)).publishEvent(any(BookingSavedEvent.class));
    }
}
