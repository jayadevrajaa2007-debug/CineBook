package com.cinebook.web;

import com.cinebook.exception.BookingNotFoundException;
import com.cinebook.model.Booking;
import com.cinebook.service.Theatre;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Controller handling booking summary, lookup, and cancellation.
 */
@Controller
@RequestMapping("/bookings")
public class BookingController {

    private final Theatre theatre;

    public BookingController(Theatre theatre) {
        this.theatre = theatre;
    }

    @GetMapping("/{id}")
    public String bookingSummary(@PathVariable("id") Long id, Model model) {
        Booking booking = theatre.getBookingById(id);
        model.addAttribute("booking", booking);
        return "booking-summary";
    }

    @GetMapping("/lookup")
    public String lookupBooking(@RequestParam(value = "code", required = false) String code,
                                Model model,
                                RedirectAttributes redirectAttributes) {
        if (code == null || code.trim().isEmpty()) {
            return "booking-lookup";
        }

        try {
            Booking booking = theatre.findBookingByCodeOrId(code.trim());
            return "redirect:/bookings/" + booking.getId();
        } catch (IllegalArgumentException | BookingNotFoundException ex) {
            model.addAttribute("searchCode", code.trim());
            model.addAttribute("errorMessage", ex.getMessage());
            return "booking-lookup";
        }
    }

    @PostMapping("/{id}/cancel")
    public String cancelBooking(@PathVariable("id") Long id,
                                RedirectAttributes redirectAttributes) {
        Booking cancelled = theatre.cancelBooking(id);
        redirectAttributes.addFlashAttribute("successMessage",
                "Booking " + cancelled.getFormattedBookingId() +
                " has been cancelled. A refund of Rs. " + cancelled.getRefundAmount() + " has been processed.");
        return "redirect:/bookings/" + id;
    }
}

