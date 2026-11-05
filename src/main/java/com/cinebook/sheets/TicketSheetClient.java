package com.cinebook.sheets;

/**
 * Client abstraction for syncing ticket booking records into Google Sheets.
 * Implementations must provide idempotent upsert logic: updating existing rows
 * or appending new rows so retries never create duplicates.
 */
public interface TicketSheetClient {

    /**
     * Idempotently upserts a booking row.
     * If a row with matching Booking ID exists in column A, updates that row;
     * otherwise appends a new row.
     *
     * @param row Formatted 13-column ticket row
     */
    void upsertBooking(TicketRow row);

    /**
     * Returns true if the client is configured, authenticated, and ready to sync.
     */
    boolean isReady();
}
