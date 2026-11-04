package com.cinebook.repo;

import com.cinebook.model.BookingSeat;
import com.cinebook.model.BookingSeatId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Set;

@Repository
public interface BookingSeatRepository extends JpaRepository<BookingSeat, BookingSeatId> {

    List<BookingSeat> findByBookingId(Long bookingId);

    /**
     * Finds active booking seats for the given seat IDs where booking is CONFIRMED.
     */
    @Query("SELECT bs FROM BookingSeat bs " +
           "JOIN bs.booking b " +
           "WHERE bs.seat.id IN :seatIds " +
           "  AND bs.active = true " +
           "  AND b.status = com.cinebook.model.BookingStatus.CONFIRMED")
    List<BookingSeat> findActiveBySeatIdIn(@Param("seatIds") Collection<Long> seatIds);

    /**
     * Returns IDs of all actively booked seats for a particular show.
     */
    @Query("SELECT bs.seat.id FROM BookingSeat bs " +
           "JOIN bs.booking b " +
           "WHERE bs.seat.show.id = :showId " +
           "  AND bs.active = true " +
           "  AND b.status = com.cinebook.model.BookingStatus.CONFIRMED")
    Set<Long> findBookedSeatIdsByShowId(@Param("showId") Long showId);
}

