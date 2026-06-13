package com.skytrack.ai.dto;

import java.time.LocalDateTime;

public record RealtimeFlightStatusDto(
        boolean live,
        boolean stale,
        int aircraftCount,
        LocalDateTime lastSuccessfulUpdate,
        LocalDateTime nextRefreshAllowedAt,
        String message
) {
}
