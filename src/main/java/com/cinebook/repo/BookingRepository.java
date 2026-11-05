package com.cinebook.repo;

import com.cinebook.model.Booking;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    @Query("SELECT DISTINCT b FROM Booking b " +
           "LEFT JOIN FETCH b.show s " +
           "LEFT JOIN FETCH b.bookingSeats bs " +
           "LEFT JOIN FETCH bs.seat " +
           "WHERE b.id = :id")
    Optional<Booking> findByIdWithDetails(@Param("id") Long id);

    List<Booking> findAllByOrderByCreatedAtDesc();

    List<Booking> findByShowId(Long showId);

    @Query("SELECT DISTINCT b FROM Booking b " +
           "LEFT JOIN FETCH b.show s " +
           "LEFT JOIN FETCH b.bookingSeats bs " +
           "LEFT JOIN FETCH bs.seat " +
           "WHERE b.sheetSyncStatus = com.cinebook.model.SheetSyncStatus.PENDING " +
           "   OR (b.sheetSyncStatus = com.cinebook.model.SheetSyncStatus.FAILED AND (b.sheetSyncedAt IS NULL OR b.sheetSyncedAt <= :failedBefore)) " +
           "ORDER BY b.id ASC")
    List<Booking> findBookingsForSheetSync(@Param("failedBefore") LocalDateTime failedBefore, Pageable pageable);
}

