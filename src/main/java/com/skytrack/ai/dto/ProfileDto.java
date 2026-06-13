package com.skytrack.ai.dto;

import com.skytrack.ai.entity.UserRole;

import java.time.LocalDateTime;

public record ProfileDto(
        Long id,
        String name,
        String email,
        UserRole role,
        String location,
        String language,
        NotificationPreferences preferences,
        long trackedFlights,
        long activeAlerts,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String token
) {
    public record NotificationPreferences(
            boolean emailNotifications,
            boolean pushNotifications,
            boolean flightAlerts,
            boolean priceAlerts
    ) {
    }
}
