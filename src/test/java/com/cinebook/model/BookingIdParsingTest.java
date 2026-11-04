package com.cinebook.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test 9: Booking ID parsing accepts "BK1001", "bk1001" and "1001".
 */
class BookingIdParsingTest {

    @Test
    @DisplayName("Verify uppercase 'BK1001' parses to Long 1001")
    void testParseUppercaseBK() {
        assertEquals(1001L, Booking.parseBookingId("BK1001"));
    }

    @Test
    @DisplayName("Verify lowercase 'bk1001' parses to Long 1001")
    void testParseLowercaseBK() {
        assertEquals(1001L, Booking.parseBookingId("bk1001"));
    }

    @Test
    @DisplayName("Verify mixed-case 'Bk1001' parses to Long 1001")
    void testParseMixedCaseBK() {
        assertEquals(1001L, Booking.parseBookingId("Bk1001"));
    }

    @Test
    @DisplayName("Verify plain number '1001' parses to Long 1001")
    void testParseNumericOnly() {
        assertEquals(1001L, Booking.parseBookingId("1001"));
    }

    @Test
    @DisplayName("Verify leading and trailing whitespace is trimmed")
    void testParseWithWhitespace() {
        assertEquals(1001L, Booking.parseBookingId("   BK1001   "));
        assertEquals(1001L, Booking.parseBookingId("  1001  "));
        assertEquals(1042L, Booking.parseBookingId(" bk1042 "));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "BK", "ABC", "BKXYZ", "0", "-15", "BK-1001"})
    @DisplayName("Verify invalid booking ID strings throw IllegalArgumentException")
    void testInvalidBookingIdThrows(String invalid) {
        assertThrows(IllegalArgumentException.class, () -> Booking.parseBookingId(invalid));
    }
}

