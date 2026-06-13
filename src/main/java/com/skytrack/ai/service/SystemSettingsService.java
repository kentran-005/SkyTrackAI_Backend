package com.skytrack.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skytrack.ai.dto.SystemSettingsDto;
import com.skytrack.ai.entity.SystemSetting;
import com.skytrack.ai.repository.AirportRepository;
import com.skytrack.ai.repository.SystemSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SystemSettingsService {

    private final SystemSettingRepository repository;
    private final AirportRepository airportRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public SystemSettingsDto getSettings() {
        return repository.findAll().stream()
                .findFirst()
                .map(this::deserialize)
                .orElseGet(SystemSettingsDto::defaults);
    }

    @Transactional
    public SystemSettingsDto saveSettings(SystemSettingsDto settings) {
        boolean airportExists = airportRepository.findAll().stream()
                .anyMatch(airport -> settings.defaultAirport().equalsIgnoreCase(airport.getCode()));
        if (!airportExists) {
            throw new IllegalArgumentException("Default airport is not managed by SkyTrack");
        }

        SystemSetting entity = repository.findAll().stream().findFirst().orElseGet(SystemSetting::new);
        try {
            entity.setConfigurationJson(objectMapper.writeValueAsString(settings));
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Cannot serialize system settings", ex);
        }
        repository.save(entity);
        return settings;
    }

    @Transactional
    public com.skytrack.ai.dto.SettingsBackupDto createBackup() {
        SystemSettingsDto settings = getSettings();
        SystemSetting entity = repository.findAll().stream().findFirst().orElseGet(SystemSetting::new);
        if (entity.getConfigurationJson() == null) {
            try {
                entity.setConfigurationJson(objectMapper.writeValueAsString(settings));
            } catch (JsonProcessingException ex) {
                throw new IllegalArgumentException("Cannot serialize system settings", ex);
            }
        }
        entity.setLastBackupAt(LocalDateTime.now());
        repository.save(entity);
        return new com.skytrack.ai.dto.SettingsBackupDto(
                "skytrack-settings-v1",
                Instant.now(),
                settings
        );
    }

    @Transactional
    public SystemSettingsDto restoreBackup(com.skytrack.ai.dto.SettingsBackupDto backup) {
        if (!"skytrack-settings-v1".equals(backup.format())) {
            throw new IllegalArgumentException("Unsupported SkyTrack backup format");
        }
        return saveSettings(backup.settings());
    }

    @Transactional(readOnly = true)
    public Optional<LocalDateTime> getLastBackupAt() {
        return repository.findAll().stream().findFirst().map(SystemSetting::getLastBackupAt);
    }

    @Transactional(readOnly = true)
    public void validatePassword(String password) {
        SystemSettingsDto.SecuritySettings security = getSettings().security();
        if (password == null || password.length() < security.minPasswordLength()) {
            throw new IllegalArgumentException(
                    "Password must contain at least " + security.minPasswordLength() + " characters"
            );
        }
        if (security.requireUppercase() && password.chars().noneMatch(Character::isUpperCase)) {
            throw new IllegalArgumentException("Password must contain an uppercase letter");
        }
        if (security.requireNumbers() && password.chars().noneMatch(Character::isDigit)) {
            throw new IllegalArgumentException("Password must contain a number");
        }
        if (security.requireSpecial() && password.chars().allMatch(Character::isLetterOrDigit)) {
            throw new IllegalArgumentException("Password must contain a special character");
        }
    }

    private SystemSettingsDto deserialize(SystemSetting entity) {
        try {
            return objectMapper.readValue(entity.getConfigurationJson(), SystemSettingsDto.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Stored system settings are invalid", ex);
        }
    }
}
