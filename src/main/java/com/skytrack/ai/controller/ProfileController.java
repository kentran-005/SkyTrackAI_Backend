package com.skytrack.ai.controller;

import com.skytrack.ai.dto.ChangePasswordRequest;
import com.skytrack.ai.dto.ProfileDto;
import com.skytrack.ai.dto.UpdateProfilePreferencesRequest;
import com.skytrack.ai.dto.UpdateProfileRequest;
import com.skytrack.ai.entity.User;
import com.skytrack.ai.repository.FlightSubscriptionRepository;
import com.skytrack.ai.repository.NotificationRepository;
import com.skytrack.ai.repository.UserRepository;
import com.skytrack.ai.security.JwtUtil;
import com.skytrack.ai.service.SystemSettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final UserRepository userRepository;
    private final FlightSubscriptionRepository subscriptionRepository;
    private final NotificationRepository notificationRepository;
    private final PasswordEncoder passwordEncoder;
    private final SystemSettingsService settingsService;
    private final JwtUtil jwtUtil;

    @GetMapping
    public ResponseEntity<ProfileDto> getProfile(Authentication authentication) {
        return ResponseEntity.ok(toDto(currentUser(authentication), null));
    }

    @PutMapping
    public ResponseEntity<?> updateProfile(
            Authentication authentication,
            @Valid @RequestBody UpdateProfileRequest request
    ) {
        User user = currentUser(authentication);
        String email = request.email().trim().toLowerCase();
        if (!email.equalsIgnoreCase(user.getEmail()) && userRepository.findByEmailIgnoreCase(email).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email is already in use"));
        }

        user.setName(request.name().trim());
        user.setEmail(email);
        user.setLocation(normalize(request.location(), "Vietnam"));
        user.setLanguage(normalize(request.language(), "English"));
        userRepository.save(user);

        long timeoutMs = settingsService.getSettings().security().sessionTimeoutMinutes() * 60_000L;
        String token = jwtUtil.generateToken(user.getEmail(), String.valueOf(user.getRole()), timeoutMs);
        return ResponseEntity.ok(toDto(user, token));
    }

    @PutMapping("/preferences")
    public ResponseEntity<ProfileDto> updatePreferences(
            Authentication authentication,
            @RequestBody UpdateProfilePreferencesRequest request
    ) {
        User user = currentUser(authentication);
        user.setEmailNotifications(request.emailNotifications());
        user.setPushNotifications(request.pushNotifications());
        user.setFlightAlerts(request.flightAlerts());
        user.setPriceAlerts(request.priceAlerts());
        return ResponseEntity.ok(toDto(userRepository.save(user), null));
    }

    @PutMapping("/password")
    public ResponseEntity<?> changePassword(
            Authentication authentication,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        User user = currentUser(authentication);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Current password is incorrect"));
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPassword())) {
            return ResponseEntity.badRequest().body(Map.of("error", "New password must be different"));
        }

        settingsService.validatePassword(request.newPassword());
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("message", "Password changed successfully"));
    }

    private User currentUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new IllegalStateException("Authenticated user is required");
        }
        return userRepository.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new IllegalArgumentException("User account was not found"));
    }

    private ProfileDto toDto(User user, String token) {
        return new ProfileDto(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                normalize(user.getLocation(), "Vietnam"),
                normalize(user.getLanguage(), "English"),
                new ProfileDto.NotificationPreferences(
                        user.isEmailNotifications(),
                        user.isPushNotifications(),
                        user.isFlightAlerts(),
                        user.isPriceAlerts()
                ),
                subscriptionRepository.countByUserId(user.getId()),
                notificationRepository.countByUserIdAndReadFalse(user.getId()),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                token
        );
    }

    private String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
