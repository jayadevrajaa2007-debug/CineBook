package com.cinebook.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Entity
@Table(
    name = "bookings",
    indexes = {
        @Index(name = "idx_bookings_show_id", columnList = "show_id")
    }
)
public class Booking {

    public static final int REFUND_PERCENT = 80;
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "show_id", nullable = false)
    private Show show;

    @Column(name = "coupon_code", length = 50)
    private String couponCode;

    @Column(name = "subtotal", precision = 10, scale = 2, nullable = false)
    private BigDecimal subtotal;

    @Column(name = "discount_amount", precision = 10, scale = 2, nullable = false)
    private BigDecimal discountAmount;

    @Column(name = "total_amount", precision = 10, scale = 2, nullable = false)
    private BigDecimal totalAmount;

    @Column(name = "refund_amount", precision = 10, scale = 2)
    private BigDecimal refundAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private BookingStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<BookingSeat> bookingSeats = new ArrayList<>();

    public Booking() {
    }

    public Booking(Show show, String couponCode, BigDecimal subtotal, BigDecimal discountAmount, BigDecimal totalAmount) {
        this.show = show;
        this.couponCode = couponCode;
        this.subtotal = subtotal != null ? subtotal.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        this.discountAmount = discountAmount != null ? discountAmount.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        this.totalAmount = totalAmount != null ? totalAmount.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        this.status = BookingStatus.CONFIRMED;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Show getShow() {
        return show;
    }

    public void setShow(Show show) {
        this.show = show;
    }

    public String getCouponCode() {
        return couponCode;
    }

    public void setCouponCode(String couponCode) {
        this.couponCode = couponCode;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(BigDecimal subtotal) {
        this.subtotal = subtotal != null ? subtotal.setScale(2, RoundingMode.HALF_UP) : null;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public void setDiscountAmount(BigDecimal discountAmount) {
        this.discountAmount = discountAmount != null ? discountAmount.setScale(2, RoundingMode.HALF_UP) : null;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount != null ? totalAmount.setScale(2, RoundingMode.HALF_UP) : null;
    }

    public BigDecimal getRefundAmount() {
        return refundAmount;
    }

    public void setRefundAmount(BigDecimal refundAmount) {
        this.refundAmount = refundAmount != null ? refundAmount.setScale(2, RoundingMode.HALF_UP) : null;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public void setStatus(BookingStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getCancelledAt() {
        return cancelledAt;
    }

    public void setCancelledAt(LocalDateTime cancelledAt) {
        this.cancelledAt = cancelledAt;
    }

    public List<BookingSeat> getBookingSeats() {
        return bookingSeats;
    }

    public void setBookingSeats(List<BookingSeat> bookingSeats) {
        this.bookingSeats = bookingSeats;
    }

    public void addBookingSeat(BookingSeat bookingSeat) {
        bookingSeats.add(bookingSeat);
        bookingSeat.setBooking(this);
    }

    /**
     * Returns formatted booking code, e.g. "BK1001".
     */
    public String getFormattedBookingId() {
        return id != null ? "BK" + id : "N/A";
    }

    public String getFormattedCreatedAt() {
        return createdAt != null ? createdAt.format(DATE_TIME_FORMATTER) : "";
    }

    public String getFormattedCancelledAt() {
        return cancelledAt != null ? cancelledAt.format(DATE_TIME_FORMATTER) : "";
    }

    /**
     * Returns comma-separated seat codes, e.g. "C3, C4".
     */
    public String getSeatCodesFormatted() {
        return bookingSeats.stream()
                .map(bs -> bs.getSeat().getCode())
                .sorted()
                .collect(Collectors.joining(", "));
    }

    /**
     * Returns seats with their tier summary, e.g. "C3, C4 (Premium)".
     */
    public String getSeatsWithTierSummary() {
        if (bookingSeats.isEmpty()) {
            return "";
        }
        String codes = getSeatCodesFormatted();
        String tiers = bookingSeats.stream()
                .map(bs -> bs.getSeat().getSeatType().name())
                .distinct()
                .collect(Collectors.joining("/"));
        return codes + " (" + tiers + ")";
    }

    /**
     * Parses a booking ID from user input.
     * Accepts: "BK1001", "bk1001", "1001".
     *
     * @param input Raw user string
     * @return numeric Long ID
     * @throws IllegalArgumentException if format is invalid
     */
    public static Long parseBookingId(String input) {
        if (input == null || input.trim().isEmpty()) {
            throw new IllegalArgumentException("Booking ID cannot be empty");
        }
        String clean = input.trim();
        if (clean.toUpperCase().startsWith("BK")) {
            clean = clean.substring(2).trim();
        }
        try {
            long parsed = Long.parseLong(clean);
            if (parsed <= 0) {
                throw new IllegalArgumentException("Booking ID must be a positive number: " + input);
            }
            return parsed;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid booking ID format: '" + input + "'. Expected format e.g. BK1001 or 1001.");
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Booking booking = (Booking) o;
        return Objects.equals(id, booking.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Booking{" +
                "id=" + id +
                ", formattedId='" + getFormattedBookingId() + '\'' +
                ", status=" + status +
                ", totalAmount=" + totalAmount +
                '}';
    }
}

