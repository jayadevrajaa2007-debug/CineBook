package com.cinebook.sheets;

import com.cinebook.model.Booking;
import com.cinebook.model.SheetSyncStatus;
import com.cinebook.repo.BookingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Outbox processor and event listener for syncing CineBook bookings to Google Sheets.
 * Handles:
 * 1. Immediate asynchronous pushes after transaction commit (@TransactionalEventListener).
 * 2. Periodic scheduled retry and backfill (@Scheduled every 30s).
 * 3. Cooldown logic for FAILED items (2-minute cooldown before retry).
 * 4. Safe failure containment: Google API failures never roll back database operations.
 */
@Service
public class SheetSyncService {

    private static final Logger log = LoggerFactory.getLogger(SheetSyncService.class);
    private static final int BATCH_SIZE = 20;
    private static final int FAILED_RETRY_MINUTES = 2;

    private final BookingRepository bookingRepository;
    private final TicketSheetClient ticketSheetClient;
    private final Clock clock;
    private final boolean sheetsEnabled;

    public SheetSyncService(
            BookingRepository bookingRepository,
            TicketSheetClient ticketSheetClient,
            Clock clock,
            @Value("${google.sheets.enabled:false}") boolean sheetsEnabled
    ) {
        this.bookingRepository = bookingRepository;
        this.ticketSheetClient = ticketSheetClient;
        this.clock = clock;
        this.sheetsEnabled = sheetsEnabled;
    }

    /**
     * Immediate asynchronous sync triggered immediately after a booking transaction commits.
     */
    @Async("sheetSyncExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBookingSaved(BookingSavedEvent event) {
        if (!sheetsEnabled) {
            return;
        }
        if (event == null || event.bookingId() == null) {
            return;
        }
        try {
            syncSingleBooking(event.bookingId());
        } catch (Exception ex) {
            log.warn("Asynchronous sheet sync failed for booking ID {}: {}. Will be retried by scheduler.",
                    event.bookingId(), ex.getMessage());
        }
    }

    /**
     * Periodic background job processing pending and retriable failed bookings.
     * Runs every 30 seconds when Google Sheets sync is enabled.
     */
    @Scheduled(fixedDelay = 30000)
    public void processPendingSyncs() {
        if (!sheetsEnabled) {
            return;
        }

        LocalDateTime retryThreshold = LocalDateTime.now(clock).minusMinutes(FAILED_RETRY_MINUTES);
        List<Booking> batch = bookingRepository.findBookingsForSheetSync(retryThreshold, PageRequest.of(0, BATCH_SIZE));

        if (batch.isEmpty()) {
            return;
        }

        log.debug("Found {} bookings eligible for Google Sheets sync.", batch.size());
        for (Booking booking : batch) {
            syncSingleBooking(booking.getId());
        }
    }

    /**
     * Syncs a single booking inside its own isolated transaction boundary.
     * If an error occurs, records the error and sets status to FAILED without propagating exceptions.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void syncSingleBooking(Long bookingId) {
        if (bookingId == null) {
            return;
        }

        Booking booking = bookingRepository.findByIdWithDetails(bookingId).orElse(null);
        if (booking == null) {
            log.warn("Cannot sync non-existent booking ID {}", bookingId);
            return;
        }

        // If client is NoOp or not ready, leave as PENDING or mark appropriately
        if (!sheetsEnabled || !ticketSheetClient.isReady()) {
            log.trace("Sheet sync skipped for BK{}: sync is disabled or client not ready.", bookingId);
            return;
        }

        try {
            TicketRow row = TicketRow.from(booking, ZoneId.of(GoogleSheetsConfig.ZONE_KOLKATA));
            ticketSheetClient.upsertBooking(row);

            booking.setSheetSyncStatus(SheetSyncStatus.SYNCED);
            booking.setSheetSyncedAt(LocalDateTime.now(clock));
            booking.setSheetSyncError(null);
            bookingRepository.save(booking);

            log.info("Successfully synced booking {} to Google Sheets.", booking.getFormattedBookingId());
        } catch (Exception ex) {
            String rawMessage = ex.getMessage();
            String truncatedError = (rawMessage != null && rawMessage.length() > 255)
                    ? rawMessage.substring(0, 255)
                    : (rawMessage != null ? rawMessage : "Unknown sync error");

            booking.setSheetSyncStatus(SheetSyncStatus.FAILED);
            booking.setSheetSyncedAt(LocalDateTime.now(clock));
            booking.setSheetSyncError(truncatedError);
            bookingRepository.save(booking);

            log.warn("Google Sheets sync failed for booking {}: {}", booking.getFormattedBookingId(), truncatedError);
        }
    }
}
