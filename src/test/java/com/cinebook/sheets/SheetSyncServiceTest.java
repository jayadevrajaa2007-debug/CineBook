package com.cinebook.sheets;

import com.cinebook.model.*;
import com.cinebook.repo.BookingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SheetSyncServiceTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private TicketSheetClient ticketSheetClient;

    private Clock fixedClock;
    private SheetSyncService sheetSyncService;
    private Show show;
    private Booking booking;

    @BeforeEach
    void setUp() {
        fixedClock = Clock.fixed(Instant.parse("2026-11-06T14:00:00Z"), ZoneId.of("Asia/Kolkata"));
        sheetSyncService = new SheetSyncService(bookingRepository, ticketSheetClient, fixedClock, true);

        lenient().when(ticketSheetClient.isReady()).thenReturn(true);

        show = new Show(1L, "Inception", LocalTime.of(18, 0));
        booking = new Booking(show, null, new BigDecimal("300.00"), BigDecimal.ZERO, new BigDecimal("300.00"));
        booking.setId(1001L);
        booking.setSheetSyncStatus(SheetSyncStatus.PENDING);
    }

    @Test
    @DisplayName("PENDING booking is pushed to TicketSheetClient and marked SYNCED with timestamp")
    void testPendingBookingPushedAndMarkedSynced() {
        when(bookingRepository.findByIdWithDetails(1001L)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        sheetSyncService.syncSingleBooking(1001L);

        verify(ticketSheetClient, times(1)).upsertBooking(any(TicketRow.class));
        assertEquals(SheetSyncStatus.SYNCED, booking.getSheetSyncStatus());
        assertNotNull(booking.getSheetSyncedAt());
        assertNull(booking.getSheetSyncError());
        verify(bookingRepository, times(1)).save(booking);
    }

    @Test
    @DisplayName("Client exception marks booking FAILED with short error message and does not throw")
    void testClientExceptionMarksFailedWithoutThrowing() {
        when(bookingRepository.findByIdWithDetails(1001L)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new RuntimeException("Google Sheets API 503 Service Unavailable"))
                .when(ticketSheetClient).upsertBooking(any(TicketRow.class));

        // Must not throw exception
        assertDoesNotThrow(() -> sheetSyncService.syncSingleBooking(1001L));

        assertEquals(SheetSyncStatus.FAILED, booking.getSheetSyncStatus());
        assertNotNull(booking.getSheetSyncedAt());
        assertTrue(booking.getSheetSyncError().contains("503 Service Unavailable"));
        verify(bookingRepository, times(1)).save(booking);
    }

    @Test
    @DisplayName("FAILED booking older than 2 minutes is retried by scheduled background processor")
    void testProcessPendingSyncsRetriesFailedOlderThanTwoMinutes() {
        Booking failedBooking = new Booking(show, null, new BigDecimal("150.00"), BigDecimal.ZERO, new BigDecimal("150.00"));
        failedBooking.setId(1002L);
        failedBooking.setSheetSyncStatus(SheetSyncStatus.FAILED);
        failedBooking.setSheetSyncedAt(LocalDateTime.now(fixedClock).minusMinutes(3));

        when(bookingRepository.findBookingsForSheetSync(any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(List.of(failedBooking));
        when(bookingRepository.findByIdWithDetails(1002L)).thenReturn(Optional.of(failedBooking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        sheetSyncService.processPendingSyncs();

        verify(ticketSheetClient, times(1)).upsertBooking(any(TicketRow.class));
        assertEquals(SheetSyncStatus.SYNCED, failedBooking.getSheetSyncStatus());
    }

    @Test
    @DisplayName("SYNCED booking is not pushed again by processPendingSyncs")
    void testSyncedBookingIsNotPushedAgain() {
        when(bookingRepository.findBookingsForSheetSync(any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(Collections.emptyList());

        sheetSyncService.processPendingSyncs();

        verify(ticketSheetClient, never()).upsertBooking(any(TicketRow.class));
    }
}
