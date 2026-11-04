package com.cinebook.repo;

import com.cinebook.model.Show;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ShowRepository extends JpaRepository<Show, Long> {

    List<Show> findAllByOrderByIdAsc();

    @Query("SELECT DISTINCT s FROM Show s LEFT JOIN FETCH s.seats WHERE s.id = :id")
    Optional<Show> findByIdWithSeats(@Param("id") Long id);
}

