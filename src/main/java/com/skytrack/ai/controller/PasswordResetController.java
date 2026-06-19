package com.skytrack.ai.controller;

import com.skytrack.ai.dto.PasswordResetConfirmRequest;
import com.skytrack.ai.dto.PasswordResetRequest;
import com.skytrack.ai.service.PasswordResetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth/password-reset")
@RequiredArgsConstructor
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    @PostMapping("/request")
    public ResponseEntity<Map<String, String>> requestCode(@Valid @RequestBody PasswordResetRequest request) {
        passwordResetService.requestCode(request.email());
        return ResponseEntity.ok(Map.of(
                "message",
                "If the account exists, a reset code has been sent."
        ));
    }

    @PostMapping("/confirm")
    public ResponseEntity<Map<String, String>> confirmReset(
            @Valid @RequestBody PasswordResetConfirmRequest request
    ) {
        passwordResetService.confirmReset(request);
        return ResponseEntity.ok(Map.of("message", "Password changed successfully"));
    }
}
