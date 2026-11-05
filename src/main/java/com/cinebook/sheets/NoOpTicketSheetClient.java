package com.cinebook.sheets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * No-Op implementation of TicketSheetClient used when Google Sheets sync is disabled.
 * Ensures the application starts and behaves normally with zero external network requests.
 */
@Component
@ConditionalOnProperty(name = "google.sheets.enabled", havingValue = "false", matchIfMissing = true)
public class NoOpTicketSheetClient implements TicketSheetClient {

    private static final Logger log = LoggerFactory.getLogger(NoOpTicketSheetClient.class);

    @Override
    public void upsertBooking(TicketRow row) {
        log.trace("Google Sheets sync is disabled; ignoring upsert for booking: {}", row != null ? row.bookingId() : "null");
    }

    @Override
    public boolean isReady() {
        return false;
    }
}
