package com.skytrack.ai.controller;

import com.skytrack.ai.repository.AirlineRepository;
import com.skytrack.ai.repository.AirportRepository;
import com.skytrack.ai.repository.FlightRepository;
import com.skytrack.ai.repository.PassengerRepository;
import com.skytrack.ai.repository.UserRepository;
import com.skytrack.ai.entity.FlightStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final FlightRepository flightRepository;
    private final AirportRepository airportRepository;
    private final PassengerRepository passengerRepository;
    private final AirlineRepository airlineRepository;
    private final UserRepository userRepository;

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Long>> getStats() {
        Map<String, Long> stats = new HashMap<>();
        stats.put("totalFlights", flightRepository.count());
        stats.put("totalAirports", airportRepository.count());
        stats.put("totalPassengers", passengerRepository.count());
        stats.put("totalAirlines", airlineRepository.count());
        stats.put("totalUsers", userRepository.count());

        // Đếm số chuyến bay bị delay (Giả sử status lưu chữ "DELAYED")
        long delayedFlights = flightRepository.countByStatus(FlightStatus.DELAYED);
        stats.put("delayedFlights", delayedFlights);
        long cancelledFlights = flightRepository.countByStatus(FlightStatus.CANCELLED);
        long onTimeFlights = flightRepository.countByStatusIn(List.of(
                FlightStatus.ON_TIME,
                FlightStatus.BOARDING,
                FlightStatus.SCHEDULED
        ));
        stats.put("cancelledFlights", cancelledFlights);
        stats.put("onTimeFlights", onTimeFlights);

        return ResponseEntity.ok(stats);
    }
}
