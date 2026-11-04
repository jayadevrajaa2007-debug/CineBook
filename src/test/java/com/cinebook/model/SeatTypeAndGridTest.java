package com.cinebook.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test 8: SeatType prices and row-letter mapping are correct (A-B, C-D, E);
 * buildSeatGrid() returns a 5 x 8 array in the right order.
 */
class SeatTypeAndGridTest {

    @Test
    @DisplayName("Verify SeatType prices are fixed and strictly defined")
    void testSeatTypePrices() {
        assertEquals(150, SeatType.REGULAR.getPrice(), "Regular seats must cost Rs. 150");
        assertEquals(250, SeatType.PREMIUM.getPrice(), "Premium seats must cost Rs. 250");
        assertEquals(400, SeatType.VIP.getPrice(), "VIP seats must cost Rs. 400");
    }

    @Test
    @DisplayName("Verify row letter mapping to SeatType")
    void testRowToSeatTypeMapping() {
        assertEquals(SeatType.REGULAR, SeatType.fromRowLabel("A"));
        assertEquals(SeatType.REGULAR, SeatType.fromRowLabel("a"));
        assertEquals(SeatType.REGULAR, SeatType.fromRowLabel("B"));
        assertEquals(SeatType.REGULAR, SeatType.fromRowLabel("b"));

        assertEquals(SeatType.PREMIUM, SeatType.fromRowLabel("C"));
        assertEquals(SeatType.PREMIUM, SeatType.fromRowLabel("c"));
        assertEquals(SeatType.PREMIUM, SeatType.fromRowLabel("D"));
        assertEquals(SeatType.PREMIUM, SeatType.fromRowLabel("d"));

        assertEquals(SeatType.VIP, SeatType.fromRowLabel("E"));
        assertEquals(SeatType.VIP, SeatType.fromRowLabel("e"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"F", "Z", "1", "", " "})
    @DisplayName("Verify invalid row letters throw IllegalArgumentException")
    void testInvalidRowLetterThrows(String invalidRow) {
        assertThrows(IllegalArgumentException.class, () -> SeatType.fromRowLabel(invalidRow));
    }

    @Test
    @DisplayName("Verify buildSeatGrid() returns a 5 x 8 array in the exact row and column order")
    void testBuildSeatGrid() {
        Show show = new Show(1L, "Interstellar", LocalTime.of(19, 30));

        // Create 40 seats (A-E x 1-8)
        char[] rows = {'A', 'B', 'C', 'D', 'E'};
        for (char r : rows) {
            String rowStr = String.valueOf(r);
            SeatType type = SeatType.fromRowLabel(rowStr);
            for (int s = 1; s <= 8; s++) {
                Seat seat = new Seat(show, rowStr, s, type);
                seat.setId((long) (show.getSeats().size() + 1));
                show.addSeat(seat);
            }
        }

        assertEquals(40, show.getSeats().size());

        // Build 5 x 8 grid
        Seat[][] grid = show.buildSeatGrid();

        assertNotNull(grid);
        assertEquals(5, grid.length, "Grid must have exactly 5 rows");
        assertEquals(8, grid[0].length, "Grid must have exactly 8 columns");

        // Verify each cell in 2D array
        for (int r = 0; r < 5; r++) {
            char expectedRow = rows[r];
            for (int c = 0; c < 8; c++) {
                int expectedSeatNo = c + 1;
                Seat seat = grid[r][c];
                assertNotNull(seat, "Seat at [" + r + "][" + c + "] must not be null");
                assertEquals(String.valueOf(expectedRow), seat.getRowLabel());
                assertEquals(expectedSeatNo, seat.getSeatNo());
                assertEquals(expectedRow + "" + expectedSeatNo, seat.getCode());
                assertEquals(SeatType.fromRowLabel(String.valueOf(expectedRow)), seat.getSeatType());
            }
        }
    }
}

