package com.cinebook.web;

import com.cinebook.exception.GlobalExceptionHandler;
import com.cinebook.model.Booking;
import com.cinebook.model.BookingSeat;
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

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BookingController.class)
@Import(GlobalExceptionHandler.class)
class BookingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private Theatre theatre;

    @Test
    @DisplayName("GET /bookings/{id} displays booking summary")
    void testGetBookingSummary() throws Exception {
        Show show = new Show(1L, "Interstellar", LocalTime.of(19, 30));
        Booking booking = new Booking(show, "CINE10", new BigDecimal("500.00"), new BigDecimal("50.00"), new BigDecimal("450.00"));
        booking.setId(1001L);

        Seat c3 = new Seat(1L, show, "C", 3, SeatType.PREMIUM);
        Seat c4 = new Seat(2L, show, "C", 4, SeatType.PREMIUM);
        booking.addBookingSeat(new BookingSeat(booking, c3));
        booking.addBookingSeat(new BookingSeat(booking, c4));

        when(theatre.getBookingById(1001L)).thenReturn(booking);

        mockMvc.perform(get("/bookings/1001"))
                .andExpect(status().isOk())
                .andExpect(view().name("booking-summary"))
                .andExpect(model().attributeExists("booking"));
    }

    @Test
    @DisplayName("GET /bookings/lookup?code=BK1001 redirects to /bookings/1001")
    void testLookupBookingRedirect() throws Exception {
        Show show = new Show(1L, "Interstellar", LocalTime.of(19, 30));
        Booking booking = new Booking(show, null, new BigDecimal("250.00"), BigDecimal.ZERO, new BigDecimal("250.00"));
        booking.setId(1001L);

        when(theatre.findBookingByCodeOrId("BK1001")).thenReturn(booking);

        mockMvc.perform(get("/bookings/lookup").param("code", "BK1001"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/bookings/1001"));
    }

    @Test
    @DisplayName("POST /bookings/{id}/cancel redirects to summary with refund flash message")
    void testCancelBookingRedirect() throws Exception {
        Show show = new Show(1L, "Interstellar", LocalTime.of(19, 30));
        Booking cancelled = new Booking(show, "CINE10", new BigDecimal("500.00"), new BigDecimal("50.00"), new BigDecimal("450.00"));
        cancelled.setId(1001L);
        cancelled.setRefundAmount(new BigDecimal("360.00"));

        when(theatre.cancelBooking(1001L)).thenReturn(cancelled);

        mockMvc.perform(post("/bookings/1001/cancel"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/bookings/1001"))
                .andExpect(flash().attributeExists("successMessage"));
    }
}

