package com.cinebook.web;

import com.cinebook.model.Booking;
import com.cinebook.model.Seat;
import com.cinebook.model.SeatType;
import com.cinebook.model.Show;
import com.cinebook.service.Theatre;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.*;

/**
 * Controller handling show seat map display and ticket booking actions.
 */
@Controller
@RequestMapping("/shows")
public class ShowController {

    private final Theatre theatre;

    public ShowController(Theatre theatre) {
        this.theatre = theatre;
    }

    @GetMapping("/{id}")
    public String showSeatMap(@PathVariable("id") Long id, Model model) {
        Show show = theatre.getShowById(id);
        Seat[][] grid = theatre.getSeatGridForShow(id);

        int bookedCount = theatre.getBookedSeatCount(id);
        int availableCount = theatre.getAvailableSeatCount(id);

        // Map of tier prices strictly from SeatType enum
        Map<String, Integer> tierPrices = new LinkedHashMap<>();
        for (SeatType type : SeatType.values()) {
            tierPrices.put(type.name(), type.getPrice());
        }

        model.addAttribute("show", show);
        model.addAttribute("grid", grid);
        model.addAttribute("bookedCount", bookedCount);
        model.addAttribute("availableCount", availableCount);
        model.addAttribute("seatTypes", SeatType.values());
        model.addAttribute("tierPrices", tierPrices);

        return "show";
    }

    @PostMapping("/{id}/book")
    public String bookSeats(@PathVariable("id") Long id,
                            @RequestParam(value = "seatCodes", required = false) String seatCodesParam,
                            @RequestParam(value = "couponCode", required = false) String couponCode,
                            RedirectAttributes redirectAttributes) {

        List<String> seatCodeList = new ArrayList<>();
        if (seatCodesParam != null && !seatCodesParam.trim().isEmpty()) {
            String[] tokens = seatCodesParam.split("[,\\s]+");
            for (String token : tokens) {
                if (!token.trim().isEmpty()) {
                    seatCodeList.add(token.trim());
                }
            }
        }

        Booking booking = theatre.bookSeats(id, seatCodeList, couponCode);

        redirectAttributes.addFlashAttribute("successMessage",
                "Booking successful! Your booking ID is " + booking.getFormattedBookingId());

        return "redirect:/bookings/" + booking.getId();
    }
}

