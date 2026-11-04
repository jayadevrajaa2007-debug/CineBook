package com.cinebook.exception;

import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Centralized exception handler for CineBook.
 * Converts domain exceptions into friendly user messages without exposing stack traces.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(InvalidSeatException.class)
    public String handleInvalidSeat(InvalidSeatException ex, HttpServletRequest request, RedirectAttributes redirectAttributes, Model model) {
        log.warn("InvalidSeatException: {}", ex.getMessage());
        String referer = request.getHeader("Referer");
        if (referer != null && referer.contains("/shows/")) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:" + referer;
        }
        model.addAttribute("errorTitle", "Invalid Seat Selection");
        model.addAttribute("errorMessage", ex.getMessage());
        return "error";
    }

    @ExceptionHandler(SeatUnavailableException.class)
    public String handleSeatUnavailable(SeatUnavailableException ex, HttpServletRequest request, RedirectAttributes redirectAttributes, Model model) {
        log.warn("SeatUnavailableException: {}", ex.getMessage());
        String referer = request.getHeader("Referer");
        if (referer != null && referer.contains("/shows/")) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:" + referer;
        }
        model.addAttribute("errorTitle", "Seat Unavailable");
        model.addAttribute("errorMessage", ex.getMessage());
        return "error";
    }

    @ExceptionHandler(BookingNotFoundException.class)
    public String handleBookingNotFound(BookingNotFoundException ex, Model model) {
        log.warn("BookingNotFoundException: {}", ex.getMessage());
        model.addAttribute("errorTitle", "Booking Not Found");
        model.addAttribute("errorMessage", ex.getMessage());
        return "error";
    }

    @ExceptionHandler(BookingAlreadyCancelledException.class)
    public String handleBookingAlreadyCancelled(BookingAlreadyCancelledException ex, HttpServletRequest request, RedirectAttributes redirectAttributes, Model model) {
        log.warn("BookingAlreadyCancelledException: {}", ex.getMessage());
        String referer = request.getHeader("Referer");
        if (referer != null && referer.contains("/bookings/")) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:" + referer;
        }
        model.addAttribute("errorTitle", "Action Not Allowed");
        model.addAttribute("errorMessage", ex.getMessage());
        return "error";
    }

    @ExceptionHandler(ShowNotFoundException.class)
    public String handleShowNotFound(ShowNotFoundException ex, Model model) {
        log.warn("ShowNotFoundException: {}", ex.getMessage());
        model.addAttribute("errorTitle", "Show Not Found");
        model.addAttribute("errorMessage", ex.getMessage());
        return "error";
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public String handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request, RedirectAttributes redirectAttributes, Model model) {
        log.warn("IllegalArgumentException: {}", ex.getMessage());
        String referer = request.getHeader("Referer");
        if (referer != null && !referer.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:" + referer;
        }
        model.addAttribute("errorTitle", "Invalid Request");
        model.addAttribute("errorMessage", ex.getMessage());
        return "error";
    }

    @ExceptionHandler(Exception.class)
    public String handleGenericException(Exception ex, Model model) {
        log.error("Unhandled Exception caught in GlobalExceptionHandler", ex);
        model.addAttribute("errorTitle", "Unexpected Error");
        model.addAttribute("errorMessage", "An unexpected error occurred while processing your request: " + ex.getMessage());
        return "error";
    }
}

