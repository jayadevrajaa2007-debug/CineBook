package com.cinebook.model;

import jakarta.persistence.*;
import java.util.Objects;

@Entity
@Table(
    name = "seats",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_show_row_seat", columnNames = {"show_id", "row_label", "seat_no"})
    },
    indexes = {
        @Index(name = "idx_seats_show_id", columnList = "show_id")
    }
)
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "show_id", nullable = false)
    private Show show;

    @Column(name = "row_label", length = 1, nullable = false)
    private String rowLabel;

    @Column(name = "seat_no", nullable = false)
    private int seatNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "seat_type", length = 20, nullable = false)
    private SeatType seatType;

    @Transient
    private boolean booked = false;

    public Seat() {
    }

    public Seat(Long id, Show show, String rowLabel, int seatNo, SeatType seatType) {
        this.id = id;
        this.show = show;
        this.rowLabel = rowLabel;
        this.seatNo = seatNo;
        this.seatType = seatType;
    }

    public Seat(Show show, String rowLabel, int seatNo, SeatType seatType) {
        this(null, show, rowLabel, seatNo, seatType);
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

    public String getRowLabel() {
        return rowLabel;
    }

    public void setRowLabel(String rowLabel) {
        this.rowLabel = rowLabel;
    }

    public int getSeatNo() {
        return seatNo;
    }

    public void setSeatNo(int seatNo) {
        this.seatNo = seatNo;
    }

    public SeatType getSeatType() {
        return seatType;
    }

    public void setSeatType(SeatType seatType) {
        this.seatType = seatType;
    }

    public boolean isBooked() {
        return booked;
    }

    public void setBooked(boolean booked) {
        this.booked = booked;
    }

    /**
     * Returns CSS classes for the seat button: 'booked' or 'free tier-regular|tier-premium|tier-vip'.
     */
    public String getCssClass() {
        if (booked) {
            return "booked";
        }
        return "free tier-" + (seatType != null ? seatType.name().toLowerCase() : "regular");
    }

    /**
     * Returns icon symbol: 'X' if booked, 'O' if free.
     */
    public String getIcon() {
        if (booked) {
            return "X";
        }
        return "O";
    }

    /**
     * Returns accessible aria-label, e.g. "Seat C3, PREMIUM, Rs. 250, available".
     */
    public String getAriaLabel() {
        String tierName = seatType != null ? seatType.name() : "Regular";
        int price = seatType != null ? seatType.getPrice() : 0;
        String status = booked ? "booked, unavailable" : "available";
        return "Seat " + getCode() + ", " + tierName + ", Rs. " + price + ", " + status;
    }

    /**
     * Returns descriptive hover title for accessibility and tooltips.
     */
    public String getDisplayTitle() {
        String base = getCode() + " (" + (seatType != null ? seatType.name() : "") + " - Rs. " + (seatType != null ? seatType.getPrice() : 0) + ")";
        return booked ? base + " [Booked]" : base + " [Available]";
    }

    /**
     * Returns the normalized seat code, e.g. "C3".
     */
    public String getCode() {
        return (rowLabel != null ? rowLabel.toUpperCase() : "") + seatNo;
    }

    /**
     * Returns row index 0..4 for rows A..E.
     */
    public int getRowIndex() {
        if (rowLabel == null || rowLabel.isEmpty()) {
            return -1;
        }
        return Character.toUpperCase(rowLabel.charAt(0)) - 'A';
    }

    /**
     * Returns column index 0..7 for seat numbers 1..8.
     */
    public int getColIndex() {
        return seatNo - 1;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Seat seat = (Seat) o;
        if (id != null && seat.id != null) {
            return Objects.equals(id, seat.id);
        }
        return seatNo == seat.seatNo &&
                Objects.equals(rowLabel, seat.rowLabel) &&
                Objects.equals(show != null ? show.getId() : null, seat.show != null ? seat.show.getId() : null);
    }

    @Override
    public int hashCode() {
        if (id != null) {
            return Objects.hash(id);
        }
        return Objects.hash(show != null ? show.getId() : null, rowLabel, seatNo);
    }

    @Override
    public String toString() {
        return "Seat{" +
                "id=" + id +
                ", code='" + getCode() + '\'' +
                ", seatType=" + seatType +
                ", booked=" + booked +
                '}';
    }
}

