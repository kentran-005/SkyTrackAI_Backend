package com.skytrack.ai.controller;

import com.skytrack.ai.dto.SettingsBackupDto;
import com.skytrack.ai.dto.SystemSettingsDto;
import com.skytrack.ai.service.SystemSettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminSystemController {

    private final SystemSettingsService settingsService;

    @GetMapping("/settings")
    public ResponseEntity<SystemSettingsDto> getSettings() {
        return ResponseEntity.ok(settingsService.getSettings());
    }

    @PutMapping("/settings")
    public ResponseEntity<SystemSettingsDto> updateSettings(
            @Valid @RequestBody SystemSettingsDto settings
    ) {
        return ResponseEntity.ok(settingsService.saveSettings(settings));
    }

    @GetMapping("/system-info")
    public ResponseEntity<Map<String, Object>> getSystemInfo() {
        Runtime runtime = Runtime.getRuntime();
        File root = new File(".");
        long uptimeMillis = ManagementFactory.getRuntimeMXBean().getUptime();
        double cpuUsage = -1;

        if (ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean os) {
            cpuUsage = Math.max(0, os.getCpuLoad() * 100);
        }

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("version", "1.0.0");
        info.put("environment", "Spring Boot");
        info.put("database", "MySQL");
        info.put("backend", "Spring Boot 3.5");
        info.put("frontend", "Next.js 15");
        info.put("uptimeSeconds", uptimeMillis / 1000);
        info.put("cpuUsage", Math.round(cpuUsage * 10) / 10.0);
        info.put("memoryUsage", percentage(runtime.totalMemory() - runtime.freeMemory(), runtime.maxMemory()));
        info.put("storageUsage", percentage(root.getTotalSpace() - root.getFreeSpace(), root.getTotalSpace()));
        info.put("availableProcessors", runtime.availableProcessors());
        info.put("lastBackupAt", settingsService.getLastBackupAt().orElse(null));
        return ResponseEntity.ok(info);
    }

    @PostMapping("/backup")
    public ResponseEntity<SettingsBackupDto> createBackup() {
        SettingsBackupDto backup = settingsService.createBackup();
        String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
                .withZone(ZoneId.systemDefault())
                .format(Instant.now());
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"skytrack-settings-" + timestamp + ".json\""
                )
                .body(backup);
    }

    @PostMapping("/backup/restore")
    public ResponseEntity<SystemSettingsDto> restoreBackup(
            @Valid @RequestBody SettingsBackupDto backup
    ) {
        return ResponseEntity.ok(settingsService.restoreBackup(backup));
    }

    private double percentage(long used, long total) {
        if (total <= 0) return 0;
        return Math.round((used * 1000.0 / total)) / 10.0;
    }
}
