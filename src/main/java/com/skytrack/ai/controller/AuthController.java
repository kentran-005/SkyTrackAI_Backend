package com.skytrack.ai.controller;

import com.skytrack.ai.dto.RegisterRequest;
import com.skytrack.ai.entity.User;
import com.skytrack.ai.entity.UserRole;
import com.skytrack.ai.repository.UserRepository;
import com.skytrack.ai.security.JwtUtil;
import com.skytrack.ai.service.SystemSettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.time.LocalDateTime;
import java.util.Locale;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final SystemSettingsService settingsService;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
        String email = normalizeEmail(body.get("email"));
        String password = body.get("password");

        if (email.isBlank() || password == null || password.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email and password are required"));
        }

        User user = userRepository.findByEmailIgnoreCase(email).orElse(null);

        if (user != null && user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now())) {
            return ResponseEntity.status(423).body(Map.of(
                    "error",
                    "Account is temporarily locked. Try again later."
            ));
        }

        if (user == null || !passwordEncoder.matches(password, user.getPassword())) {
            if (user != null) {
                int attempts = user.getFailedLoginAttempts() + 1;
                int maxAttempts = settingsService.getSettings().security().maxLoginAttempts();
                user.setFailedLoginAttempts(attempts);
                if (attempts >= maxAttempts) {
                    user.setLockedUntil(LocalDateTime.now().plusMinutes(15));
                    user.setFailedLoginAttempts(0);
                }
                userRepository.save(user);
            }
            return ResponseEntity.status(401).body(Map.of("error", "Invalid email or password"));
        }

        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);

        long sessionTimeoutMs = settingsService.getSettings().security().sessionTimeoutMinutes() * 60_000L;
        String token = jwtUtil.generateToken(
                user.getEmail(),
                String.valueOf(user.getRole()),
                sessionTimeoutMs
        );
        return ResponseEntity.ok(Map.of(
                "token", token,
                "email", user.getEmail(),
                "role", user.getRole(),
                "name", user.getName()
        ));
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest req) { // Dùng DTO

        String email = normalizeEmail(req.getEmail());
        if (userRepository.findByEmailIgnoreCase(email).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email already exists"));
        }

        settingsService.validatePassword(req.getPassword());

        User user = new User();
        user.setName(req.getName().trim());
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(req.getPassword())); // Bcrypt
        user.setRole(UserRole.USER); // An toàn tuyệt đối, không sợ ghi đè

        userRepository.save(user);
        return ResponseEntity.ok(Map.of("message", "User registered successfully"));
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
