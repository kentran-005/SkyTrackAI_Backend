package com.skytrack.ai.controller;

import com.skytrack.ai.entity.Flight;
import com.skytrack.ai.entity.FlightSubscription;
import com.skytrack.ai.repository.FlightSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final FlightSubscriptionRepository subscriptionRepository;

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<FlightSubscription>> getUserSubscriptions(@PathVariable Long userId) {
        return ResponseEntity.ok(subscriptionRepository.findByUserId(userId));
    }

    @PostMapping
    public ResponseEntity<?> subscribeFlight(@RequestBody Map<String, Long> body) {
        Long userId = body.get("userId");
        Long flightId = body.get("flightId");

        if (userId == null || flightId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "userId and flightId are required"));
        }

        if(subscriptionRepository.existsByUserIdAndFlightId(userId, flightId)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Already subscribed"));
        }

        FlightSubscription sub = new FlightSubscription();
        sub.setUserId(userId);
        Flight flight = new Flight();
        flight.setId(flightId);
        sub.setFlight(flight);

        return ResponseEntity.ok(subscriptionRepository.save(sub));
    }

    @DeleteMapping
    public ResponseEntity<Void> unsubscribeFlight(@RequestParam Long userId, @RequestParam Long flightId) {
        subscriptionRepository.deleteByUserIdAndFlightId(userId, flightId);
        return ResponseEntity.ok().build();
    }
}
