-- =============================================================================
-- CineBook Schema Migration V3: Google Sheets Outbox Sync Columns
-- =============================================================================

ALTER TABLE bookings
    ADD COLUMN sheet_sync_status VARCHAR(10) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN sheet_synced_at DATETIME NULL,
    ADD COLUMN sheet_sync_error VARCHAR(255) NULL;

ALTER TABLE bookings
    ADD CONSTRAINT chk_sheet_sync_status CHECK (sheet_sync_status IN ('PENDING', 'SYNCED', 'FAILED'));

CREATE INDEX idx_bookings_sheet_sync_status ON bookings (sheet_sync_status);

UPDATE bookings SET sheet_sync_status = 'PENDING' WHERE sheet_sync_status IS NULL;
