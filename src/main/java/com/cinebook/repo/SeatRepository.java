package com.cinebook.repo;

import com.cinebook.model.Seat;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SeatRepository extends JpaRepository<Seat, Long> {

    List<Seat> findByShowIdOrderByRowLabelAscSeatNoAsc(Long showId);

    Optional<Seat> findByShowIdAndRowLabelAndSeatNo(Long showId, String rowLabel, int seatNo);

    /**
     * Lock the selected seat rows with a pessimistic write lock, ordered by seat id
     * to prevent deadlocks and prevent double bookings across concurrent transactions.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Seat s WHERE s.id IN :seatIds ORDER BY s.id ASC")
    List<Seat> findByIdInOrderByIdAscWithLock(@Param("seatIds") List<Long> seatIds);
}

