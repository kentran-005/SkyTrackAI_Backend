package com.skytrack.ai.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SystemSettingsDto(
        @NotBlank String systemName,
        @NotBlank String defaultAirport,
        @NotBlank String timezone,
        @NotBlank String language,
        @NotBlank String dateFormat,
        @NotBlank String timeFormat,
        @NotNull @Valid NotificationSettings notifications,
        @NotNull @Valid SecuritySettings security,
        @NotNull @Valid AiSettings ai
) {
    public record NotificationSettings(
            boolean flightDelay,
            boolean airport,
            boolean weather,
            boolean aiPrediction,
            boolean maintenance
    ) {
    }

    public record SecuritySettings(
            @Min(6) @Max(32) int minPasswordLength,
            boolean requireUppercase,
            boolean requireNumbers,
            boolean requireSpecial,
            @Min(5) @Max(240) int sessionTimeoutMinutes,
            @Min(3) @Max(10) int maxLoginAttempts
    ) {
    }

    public record AiSettings(
            boolean delayPrediction,
            boolean weatherAnalysis,
            boolean aiSummary,
            boolean smartRecommendations
    ) {
    }

    public static SystemSettingsDto defaults() {
        return new SystemSettingsDto(
                "SkyTrack AI",
                "SGN",
                "Asia/Ho_Chi_Minh",
                "en",
                "DD/MM/YYYY",
                "24h",
                new NotificationSettings(true, true, true, true, true),
                new SecuritySettings(8, true, true, true, 30, 5),
                new AiSettings(true, true, true, true)
        );
    }
}
