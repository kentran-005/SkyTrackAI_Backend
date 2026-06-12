package com.skytrack.ai.repository;

import com.skytrack.ai.entity.Flight;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface FlightRepository extends JpaRepository<Flight, Long> {
    @Query("SELECT f FROM Flight f " +
            "JOIN f.airline a " +
            "JOIN f.departureAirport da " +
            "JOIN f.arrivalAirport aa " +
            "WHERE LOWER(f.flightCode) LIKE LOWER(CONCAT('%', :query, '%')) " +
            "OR LOWER(a.name) LIKE LOWER(CONCAT('%', :query, '%')) " +
            "OR LOWER(da.code) LIKE LOWER(CONCAT('%', :query, '%')) " +
            "OR LOWER(da.city) LIKE LOWER(CONCAT('%', :query, '%')) " +
            "OR LOWER(aa.code) LIKE LOWER(CONCAT('%', :query, '%')) " +
            "OR LOWER(aa.city) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<Flight> searchFlights(@Param("query") String query);
}