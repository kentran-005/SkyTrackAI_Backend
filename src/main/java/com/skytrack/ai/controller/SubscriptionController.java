package com.skytrack.ai.controller;

import com.skytrack.ai.entity.Flight;
import com.skytrack.ai.entity.FlightSubscription;
import com.skytrack.ai.entity.User;
import com.skytrack.ai.exception.ResourceNotFoundException;
import com.skytrack.ai.repository.FlightRepository;
import com.skytrack.ai.repository.FlightSubscriptionRepository;
import com.skytrack.ai.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final FlightSubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final FlightRepository flightRepository;

    @GetMapping("/me")
    public ResponseEntity<List<FlightSubscription>> getMySubscriptions(Authentication authentication) {
        return ResponseEntity.ok(subscriptionRepository.findByUserId(currentUser(authentication).getId()));
    }

    @PostMapping("/me/{flightId}")
    public ResponseEntity<?> subscribeCurrentUser(
            Authentication authentication,
            @PathVariable Long flightId
    ) {
        User user = currentUser(authentication);
        if (subscriptionRepository.existsByUserIdAndFlightId(user.getId(), flightId)) {
            return ResponseEntity.ok(Map.of("message", "Already following this flight"));
        }

        Flight flight = flightRepository.findById(flightId)
                .orElseThrow(() -> new ResourceNotFoundException("Flight not found"));
        FlightSubscription subscription = new FlightSubscription();
        subscription.setUserId(user.getId());
        subscription.setFlight(flight);
        return ResponseEntity.ok(subscriptionRepository.save(subscription));
    }

    @DeleteMapping("/me/{flightId}")
    public ResponseEntity<Void> unsubscribeCurrentUser(
            Authentication authentication,
            @PathVariable Long flightId
    ) {
        User user = currentUser(authentication);
        subscriptionRepository.deleteByUserIdAndFlightId(user.getId(), flightId);
        return ResponseEntity.noContent().build();
    }

    private User currentUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new IllegalArgumentException("Authenticated user is required");
        }
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("User account not found"));
    }
}
