package com.cinebook.model;

/**
 * Lifecycle status of a booking record in the Google Sheets outbox sync mechanism.
 * - PENDING: newly created or updated (e.g. cancelled) booking waiting to be synced.
 * - SYNCED: successfully written to Google Sheets.
 * - FAILED: last attempt encountered an error; will be retried after cooldown.
 */
public enum SheetSyncStatus {
    PENDING,
    SYNCED,
    FAILED
}
