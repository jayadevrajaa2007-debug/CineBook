package com.cinebook.sheets;

/**
 * Domain event published when a booking is created or cancelled,
 * triggering an immediate asynchronous outbox push to Google Sheets.
 */
public record BookingSavedEvent(Long bookingId) {
}
