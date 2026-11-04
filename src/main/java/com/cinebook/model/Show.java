package com.cinebook.model;

import jakarta.persistence.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "shows")
public class Show {

    public static final int GRID_ROWS = 5;
    public static final int GRID_COLS = 8;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("h:mm a");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "movie_name", length = 150, nullable = false)
    private String movieName;

    @Column(name = "show_time", nullable = false)
    private LocalTime showTime;

    @OneToMany(mappedBy = "show", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("rowLabel ASC, seatNo ASC")
    private List<Seat> seats = new ArrayList<>();

    public Show() {
    }

    public Show(Long id, String movieName, LocalTime showTime) {
        this.id = id;
        this.movieName = movieName;
        this.showTime = showTime;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getMovieName() {
        return movieName;
    }

    public void setMovieName(String movieName) {
        this.movieName = movieName;
    }

    public LocalTime getShowTime() {
        return showTime;
    }

    public void setShowTime(LocalTime showTime) {
        this.showTime = showTime;
    }

    public List<Seat> getSeats() {
        return seats;
    }

    public void setSeats(List<Seat> seats) {
        this.seats = seats;
    }

    public void addSeat(Seat seat) {
        seats.add(seat);
        seat.setShow(this);
    }

    /**
     * Formats the show time for display (e.g., "7:30 PM").
     */
    public String getFormattedShowTime() {
        return showTime != null ? showTime.format(TIME_FORMATTER) : "";
    }

    /**
     * Builds and returns a 5 x 8 2D array of Seat objects.
     * Rows are indexed 0..4 corresponding to A..E.
     * Columns are indexed 0..7 corresponding to 1..8.
     * Driven by OOP design using nested loops.
     */
    public Seat[][] buildSeatGrid() {
        Seat[][] grid = new Seat[GRID_ROWS][GRID_COLS];
        for (Seat seat : seats) {
            int row = seat.getRowIndex();
            int col = seat.getColIndex();
            if (row >= 0 && row < GRID_ROWS && col >= 0 && col < GRID_COLS) {
                grid[row][col] = seat;
            }
        }
        return grid;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Show show = (Show) o;
        return Objects.equals(id, show.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Show{" +
                "id=" + id +
                ", movieName='" + movieName + '\'' +
                ", showTime=" + showTime +
                '}';
    }
}

