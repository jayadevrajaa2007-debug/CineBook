package com.cinebook.service;

import com.cinebook.model.Seat;
import com.cinebook.model.SeatType;
import com.cinebook.model.Show;
import com.cinebook.repo.BookingRepository;
import com.cinebook.repo.BookingSeatRepository;
import com.cinebook.repo.ShowRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TheatreTest {

    @Mock
    private ShowRepository showRepository;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private BookingSeatRepository bookingSeatRepository;

    @Mock
    private BookingService bookingService;

    @InjectMocks
    private Theatre theatre;

    private Show show;

    @BeforeEach
    void setUp() {
        show = new Show(1L, "Interstellar", LocalTime.of(19, 30));
        long id = 1;
        for (char r : new char[]{'A', 'B', 'C', 'D', 'E'}) {
            String rowStr = String.valueOf(r);
            for (int s = 1; s <= 8; s++) {
                Seat seat = new Seat(id++, show, rowStr, s, SeatType.fromRowLabel(rowStr));
                show.addSeat(seat);
            }
        }
    }

    @Test
    @DisplayName("Verify Theatre.getSeatGridForShow marks booked seats properly")
    void testGetSeatGridForShow() {
        when(showRepository.findByIdWithSeats(1L)).thenReturn(Optional.of(show));
        // Simulate seat IDs 19 (C3) and 20 (C4) as booked
        when(bookingSeatRepository.findBookedSeatIdsByShowId(1L)).thenReturn(Set.of(19L, 20L));

        Seat[][] grid = theatre.getSeatGridForShow(1L);

        assertNotNull(grid);
        assertEquals(5, grid.length);
        assertEquals(8, grid[0].length);

        // Seat C3 is at grid[2][2] (0-based: C -> 2, 3 -> 2)
        Seat c3 = grid[2][2];
        assertTrue(c3.isBooked(), "Seat C3 must be marked booked");

        // Seat A1 is at grid[0][0]
        Seat a1 = grid[0][0];
        assertFalse(a1.isBooked(), "Seat A1 must remain free");
    }

    @Test
    @DisplayName("Verify Theatre available and booked seat counts")
    void testSeatCounts() {
        when(bookingSeatRepository.findBookedSeatIdsByShowId(1L)).thenReturn(Set.of(1L, 2L, 3L));

        assertEquals(3, theatre.getBookedSeatCount(1L));
        assertEquals(37, theatre.getAvailableSeatCount(1L));
    }
}

