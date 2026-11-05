package com.cinebook.sheets;

import com.cinebook.model.Booking;
import com.cinebook.model.BookingSeat;
import com.cinebook.model.Seat;
import com.cinebook.model.SeatType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Immutable record representing a single 13-column ticket row in Google Sheets.
 * Columns A to M:
 * 1. Booking ID (e.g. BK1001)
 * 2. Movie
 * 3. Show Time
 * 4. Seats (e.g. "C3, C4")
 * 5. Seat Types (e.g. "Premium, Premium")
 * 6. Subtotal (Number, 2 decimals)
 * 7. Coupon
 * 8. Discount (Number, 2 decimals)
 * 9. Total (Number, 2 decimals)
 * 10. Status (CONFIRMED / CANCELLED)
 * 11. Refund (Number, 2 decimals or empty)
 * 12. Booked At (Asia/Kolkata, yyyy-MM-dd HH:mm:ss)
 * 13. Cancelled At (Asia/Kolkata, yyyy-MM-dd HH:mm:ss or empty)
 */
public record TicketRow(
        String bookingId,
        String movie,
        String showTime,
        String seats,
        String seatTypes,
        BigDecimal subtotal,
        String coupon,
        BigDecimal discount,
        BigDecimal total,
        String status,
        BigDecimal refund,
        String bookedAt,
        String cancelledAt
) {

    public static final List<String> HEADERS = List.of(
            "Booking ID",
            "Movie",
            "Show Time",
            "Seats",
            "Seat Types",
            "Subtotal",
            "Coupon",
            "Discount",
            "Total",
            "Status",
            "Refund",
            "Booked At",
            "Cancelled At"
    );

    public static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Factory method creating a TicketRow from a Booking entity.
     */
    public static TicketRow from(Booking booking, ZoneId zoneId) {
        Objects.requireNonNull(booking, "Booking must not be null");
        ZoneId zone = zoneId != null ? zoneId : DEFAULT_ZONE;

        String formattedId = booking.getFormattedBookingId();
        String movieTitle = booking.getShow() != null ? booking.getShow().getMovieName() : "";
        String showTimeFormatted = booking.getShow() != null && booking.getShow().getShowTime() != null
                ? booking.getShow().getFormattedShowTime().toUpperCase()
                : "";

        // Sort seats cleanly by code (e.g. C3, C4)
        List<Seat> sortedSeats = booking.getBookingSeats().stream()
                .map(BookingSeat::getSeat)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(Seat::getCode, String.CASE_INSENSITIVE_ORDER))
                .toList();

        String seatsFormatted = sortedSeats.stream()
                .map(Seat::getCode)
                .collect(Collectors.joining(", "));

        String seatTypesFormatted = sortedSeats.stream()
                .map(Seat::getSeatType)
                .filter(Objects::nonNull)
                .map(TicketRow::formatSeatType)
                .collect(Collectors.joining(", "));

        String bookedAtStr = formatDateTime(booking.getCreatedAt(), zone);
        String cancelledAtStr = formatDateTime(booking.getCancelledAt(), zone);

        return new TicketRow(
                formattedId,
                movieTitle,
                showTimeFormatted,
                seatsFormatted,
                seatTypesFormatted,
                booking.getSubtotal(),
                booking.getCouponCode(),
                booking.getDiscountAmount(),
                booking.getTotalAmount(),
                booking.getStatus() != null ? booking.getStatus().name() : "",
                booking.getRefundAmount(),
                bookedAtStr,
                cancelledAtStr
        );
    }

    /**
     * Converts fields into an ordered List of values for Google Sheets API (RAW mode).
     * Text cells are formula-sanitized to prevent CSV/Sheet formula injection.
     * Currency fields are serialized as Double numbers with 2 decimals.
     */
    public List<Object> toRowValues() {
        List<Object> row = new ArrayList<>(13);
        row.add(sanitizeText(bookingId));
        row.add(sanitizeText(movie));
        row.add(sanitizeText(showTime));
        row.add(sanitizeText(seats));
        row.add(sanitizeText(seatTypes));
        row.add(subtotal != null ? subtotal.setScale(2, RoundingMode.HALF_UP).doubleValue() : 0.00);
        row.add(coupon != null ? sanitizeText(coupon) : "");
        row.add(discount != null ? discount.setScale(2, RoundingMode.HALF_UP).doubleValue() : 0.00);
        row.add(total != null ? total.setScale(2, RoundingMode.HALF_UP).doubleValue() : 0.00);
        row.add(sanitizeText(status));
        row.add(refund != null ? refund.setScale(2, RoundingMode.HALF_UP).doubleValue() : "");
        row.add(sanitizeText(bookedAt));
        row.add(cancelledAt != null ? sanitizeText(cancelledAt) : "");
        return row;
    }

    /**
     * Sanitizes user-supplied or free text cells to prevent formula injection.
     * Any text starting with '=', '+', '-', or '@' is prefixed with a single quote.
     */
    public static String sanitizeText(String text) {
        if (text == null) {
            return "";
        }
        String trimmed = text.trim();
        if (!trimmed.isEmpty()) {
            char firstChar = trimmed.charAt(0);
            if (firstChar == '=' || firstChar == '+' || firstChar == '-' || firstChar == '@') {
                return "'" + text;
            }
        }
        return text;
    }

    public static String formatSeatType(SeatType type) {
        if (type == null) {
            return "";
        }
        return switch (type) {
            case REGULAR -> "Regular";
            case PREMIUM -> "Premium";
            case VIP -> "VIP";
        };
    }

    private static String formatDateTime(LocalDateTime dt, ZoneId zoneId) {
        if (dt == null) {
            return "";
        }
        return dt.format(DATE_TIME_FORMATTER);
    }
}
