package com.skytrack.ai.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record SettingsBackupDto(
        @NotBlank String format,
        @NotNull Instant createdAt,
        @NotNull @Valid SystemSettingsDto settings
) {
}
