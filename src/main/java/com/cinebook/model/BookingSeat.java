package com.cinebook.model;

import jakarta.persistence.*;
import java.util.Objects;

@Entity
@Table(
    name = "booking_seats",
    indexes = {
        @Index(name = "idx_booking_seats_seat_id", columnList = "seat_id"),
        @Index(name = "idx_booking_seats_booking_id", columnList = "booking_id")
    }
)
public class BookingSeat {

    @EmbeddedId
    private BookingSeatId id = new BookingSeatId();

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("bookingId")
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("seatId")
    @JoinColumn(name = "seat_id", nullable = false)
    private Seat seat;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    public BookingSeat() {
    }

    public BookingSeat(Booking booking, Seat seat) {
        this.booking = booking;
        this.seat = seat;
        this.active = true;
        if (booking != null && booking.getId() != null) {
            this.id.setBookingId(booking.getId());
        }
        if (seat != null && seat.getId() != null) {
            this.id.setSeatId(seat.getId());
        }
    }

    public BookingSeatId getId() {
        return id;
    }

    public void setId(BookingSeatId id) {
        this.id = id;
    }

    public Booking getBooking() {
        return booking;
    }

    public void setBooking(Booking booking) {
        this.booking = booking;
        if (booking != null && booking.getId() != null) {
            this.id.setBookingId(booking.getId());
        }
    }

    public Seat getSeat() {
        return seat;
    }

    public void setSeat(Seat seat) {
        this.seat = seat;
        if (seat != null && seat.getId() != null) {
            this.id.setSeatId(seat.getId());
        }
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BookingSeat that = (BookingSeat) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

