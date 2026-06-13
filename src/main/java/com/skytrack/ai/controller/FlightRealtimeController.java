package com.skytrack.ai.controller;

import com.skytrack.ai.dto.FlightStateDto;
import com.skytrack.ai.dto.RealtimeFlightSnapshotDto;
import com.skytrack.ai.dto.RealtimeFlightStatusDto;
import com.skytrack.ai.service.OpenSkyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/realtime-flights")
@RequiredArgsConstructor
public class FlightRealtimeController {

    private final OpenSkyService openSkyService;

    @GetMapping
    public ResponseEntity<List<FlightStateDto>> getRealtimeFlights() {
        // Service tự động kiểm tra DB, tự gọi API nếu cần và tự lưu vào DB
        List<FlightStateDto> flights = openSkyService.getFlights();

        if (flights.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(flights);
    }

    @GetMapping("/status")
    public ResponseEntity<RealtimeFlightStatusDto> getRealtimeFlightStatus() {
        return ResponseEntity.ok(openSkyService.getStatus());
    }

    @GetMapping("/snapshot")
    public ResponseEntity<RealtimeFlightSnapshotDto> getRealtimeFlightSnapshot() {
        List<FlightStateDto> flights = openSkyService.getFlights();
        return ResponseEntity.ok(new RealtimeFlightSnapshotDto(flights, openSkyService.getStatus()));
    }
}
