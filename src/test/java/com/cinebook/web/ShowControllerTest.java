package com.cinebook.web;

import com.cinebook.exception.GlobalExceptionHandler;
import com.cinebook.exception.InvalidSeatException;
import com.cinebook.model.Booking;
import com.cinebook.model.Seat;
import com.cinebook.model.SeatType;
import com.cinebook.model.Show;
import com.cinebook.service.Theatre;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ShowController.class)
@Import(GlobalExceptionHandler.class)
class ShowControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private Theatre theatre;

    @Test
    @DisplayName("GET /shows/{id} displays seat map with 5x8 grid and tier prices")
    void testShowSeatMapPage() throws Exception {
        Show show = new Show(1L, "Interstellar", LocalTime.of(19, 30));
        Seat[][] grid = new Seat[5][8];
        for (int r = 0; r < 5; r++) {
            char rowChar = (char) ('A' + r);
            for (int c = 0; c < 8; c++) {
                grid[r][c] = new Seat((long) (r * 8 + c + 1), show, String.valueOf(rowChar), c + 1, SeatType.fromRowLabel(String.valueOf(rowChar)));
            }
        }

        when(theatre.getShowById(1L)).thenReturn(show);
        when(theatre.getSeatGridForShow(1L)).thenReturn(grid);
        when(theatre.getBookedSeatCount(1L)).thenReturn(0);
        when(theatre.getAvailableSeatCount(1L)).thenReturn(40);

        mockMvc.perform(get("/shows/1"))
                .andExpect(status().isOk())
                .andExpect(view().name("show"))
                .andExpect(model().attributeExists("show", "grid", "tierPrices", "availableCount"));
    }

    @Test
    @DisplayName("POST /shows/{id}/book redirects to booking summary on success (PRG pattern)")
    void testBookSeatsSuccessRedirect() throws Exception {
        Show show = new Show(1L, "Interstellar", LocalTime.of(19, 30));
        Booking booking = new Booking(show, "CINE10", new BigDecimal("500.00"), new BigDecimal("50.00"), new BigDecimal("450.00"));
        booking.setId(1001L);

        when(theatre.bookSeats(eq(1L), anyList(), eq("CINE10"))).thenReturn(booking);

        mockMvc.perform(post("/shows/1/book")
                        .param("seatCodes", "C3, C4")
                        .param("couponCode", "CINE10"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/bookings/1001"))
                .andExpect(flash().attributeExists("successMessage"));

        verify(theatre).bookSeats(eq(1L), eq(List.of("C3", "C4")), eq("CINE10"));
    }

    @Test
    @DisplayName("POST /shows/{id}/book with invalid seat redirects with error message")
    void testBookSeatsInvalidCodeRedirect() throws Exception {
        when(theatre.bookSeats(eq(1L), anyList(), any()))
                .thenThrow(new InvalidSeatException("Invalid seat code: Z9"));

        mockMvc.perform(post("/shows/1/book")
                        .header("Referer", "http://localhost:8080/shows/1")
                        .param("seatCodes", "Z9"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost:8080/shows/1"))
                .andExpect(flash().attributeExists("errorMessage"));
    }
}

