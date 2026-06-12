package com.skytrack.ai.controller;

import com.skytrack.ai.repository.AirlineRepository;
import com.skytrack.ai.repository.AirportRepository;
import com.skytrack.ai.repository.FlightRepository;
import com.skytrack.ai.repository.PassengerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final FlightRepository flightRepository;
    private final AirportRepository airportRepository;
    private final PassengerRepository passengerRepository;
    private final AirlineRepository airlineRepository;

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Long>> getStats() {
        Map<String, Long> stats = new HashMap<>();
        stats.put("totalFlights", flightRepository.count());
        stats.put("totalAirports", airportRepository.count());
        stats.put("totalPassengers", passengerRepository.count());
        stats.put("totalAirlines", airlineRepository.count());

        // Đếm số chuyến bay bị delay (Giả sử status lưu chữ "DELAYED")
        long delayedFlights = flightRepository.findAll().stream()
                .filter(f -> "DELAYED".equals(String.valueOf(f.getStatus())))
                .count();
        stats.put("delayedFlights", delayedFlights);

        return ResponseEntity.ok(stats);
    }
}