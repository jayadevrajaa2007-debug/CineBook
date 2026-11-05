package com.cinebook.sheets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class NoOpTicketSheetClientTest {

    @Test
    @DisplayName("With sync disabled, NoOp client does not perform network operations and reports not ready")
    void testNoOpClientDoesNothingAndIsNotReady() {
        NoOpTicketSheetClient noOpClient = new NoOpTicketSheetClient();

        assertFalse(noOpClient.isReady(), "NoOp client must report isReady() = false");

        TicketRow dummyRow = new TicketRow(
                "BK1001", "Interstellar", "7:30 PM", "C3", "Premium",
                new BigDecimal("250.00"), null, BigDecimal.ZERO, new BigDecimal("250.00"),
                "CONFIRMED", null, "2026-11-06 19:30:12", ""
        );

        assertDoesNotThrow(() -> noOpClient.upsertBooking(dummyRow),
                "Calling upsertBooking on NoOp client must not throw any exception");
    }
}
