package com.skytrack.ai.dto;

public record UpdateProfilePreferencesRequest(
        boolean emailNotifications,
        boolean pushNotifications,
        boolean flightAlerts,
        boolean priceAlerts
) {
}
