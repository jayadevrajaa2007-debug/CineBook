package com.cinebook.sheets;

import com.cinebook.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for TicketRow:
 * 1. Factory method formatting of seats, seat types, 2-decimal money, timestamps, and refund.
 * 2. Sanitization of formulas starting with =, +, -, @ to prevent spreadsheet formula injection.
 */
class TicketRowTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

    @Test
    @DisplayName("TicketRow factory formats confirmed booking with correct seat types, sorted codes, and 2-decimal money")
    void testTicketRowFactoryConfirmed() {
        Show show = new Show(1L, "Interstellar", LocalTime.of(19, 30));
        Booking booking = new Booking(show, "CINE10", new BigDecimal("500.00"), new BigDecimal("50.00"), new BigDecimal("450.00"));
        booking.setId(1001L);
        booking.setCreatedAt(LocalDateTime.of(2026, 11, 6, 19, 30, 12));

        Seat seat1 = new Seat(show, "C", 4, SeatType.PREMIUM);
        Seat seat2 = new Seat(show, "C", 3, SeatType.PREMIUM);
        booking.addBookingSeat(new BookingSeat(booking, seat1));
        booking.addBookingSeat(new BookingSeat(booking, seat2));

        TicketRow row = TicketRow.from(booking, ZONE);

        assertEquals("BK1001", row.bookingId());
        assertEquals("Interstellar", row.movie());
        assertEquals("7:30 PM", row.showTime());
        // Must be sorted C3, C4
        assertEquals("C3, C4", row.seats());
        assertEquals("Premium, Premium", row.seatTypes());
        assertEquals(new BigDecimal("500.00"), row.subtotal());
        assertEquals("CINE10", row.coupon());
        assertEquals(new BigDecimal("50.00"), row.discount());
        assertEquals(new BigDecimal("450.00"), row.total());
        assertEquals("CONFIRMED", row.status());
        assertNull(row.refund());
        assertEquals("2026-11-06 19:30:12", row.bookedAt());
        assertEquals("", row.cancelledAt());

        List<Object> values = row.toRowValues();
        assertEquals(13, values.size());
        assertEquals("BK1001", values.get(0));
        assertEquals(500.00, values.get(5));
        assertEquals("CINE10", values.get(6));
        assertEquals(50.00, values.get(7));
        assertEquals(450.00, values.get(8));
        assertEquals("CONFIRMED", values.get(9));
        assertEquals("", values.get(10)); // Empty cell for refund
        assertEquals("2026-11-06 19:30:12", values.get(11));
        assertEquals("", values.get(12)); // Empty cell for cancelledAt
    }

    @Test
    @DisplayName("TicketRow factory formats cancelled booking with 80% refund and cancellation timestamp")
    void testTicketRowFactoryCancelled() {
        Show show = new Show(2L, "The Dark Knight", LocalTime.of(21, 0));
        Booking booking = new Booking(show, null, new BigDecimal("400.00"), BigDecimal.ZERO, new BigDecimal("400.00"));
        booking.setId(1005L);
        booking.setCreatedAt(LocalDateTime.of(2026, 11, 6, 15, 0, 0));
        booking.setStatus(BookingStatus.CANCELLED);
        booking.setRefundAmount(new BigDecimal("320.00"));
        booking.setCancelledAt(LocalDateTime.of(2026, 11, 6, 16, 45, 30));

        Seat seat = new Seat(show, "E", 1, SeatType.VIP);
        booking.addBookingSeat(new BookingSeat(booking, seat));

        TicketRow row = TicketRow.from(booking, ZONE);

        assertEquals("BK1005", row.bookingId());
        assertEquals("VIP", row.seatTypes());
        assertEquals("CANCELLED", row.status());
        assertEquals(new BigDecimal("320.00"), row.refund());
        assertEquals("2026-11-06 16:45:30", row.cancelledAt());

        List<Object> values = row.toRowValues();
        assertEquals("CANCELLED", values.get(9));
        assertEquals(320.00, values.get(10));
        assertEquals("2026-11-06 16:45:30", values.get(12));
    }

    @Test
    @DisplayName("Sanitization neutralizes formula injection (=, +, -, @) with a single quote prefix")
    void testSanitizationNeutralizesFormulas() {
        assertEquals("'=HYPERLINK(\"http://evil.com\")", TicketRow.sanitizeText("=HYPERLINK(\"http://evil.com\")"));
        assertEquals("'+cmd|' /C calc'!A0", TicketRow.sanitizeText("+cmd|' /C calc'!A0"));
        assertEquals("'-2+5+cmd", TicketRow.sanitizeText("-2+5+cmd"));
        assertEquals("'@SUM(A1:A10)", TicketRow.sanitizeText("@SUM(A1:A10)"));
        assertEquals("CINE10", TicketRow.sanitizeText("CINE10"));
        assertEquals("Interstellar", TicketRow.sanitizeText("Interstellar"));
        assertEquals("", TicketRow.sanitizeText(""));
        assertEquals("", TicketRow.sanitizeText(null));
    }
}
