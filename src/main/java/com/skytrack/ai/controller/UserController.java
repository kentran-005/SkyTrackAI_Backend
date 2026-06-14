package com.skytrack.ai.controller;

import com.skytrack.ai.entity.User;
import com.skytrack.ai.entity.UserRole;
import com.skytrack.ai.service.UserService;
import com.skytrack.ai.service.SystemSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final SystemSettingsService settingsService;

    @GetMapping
    public ResponseEntity<List<User>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    @PostMapping("/register")
    public ResponseEntity<User> registerUser(@RequestBody User user) {
        settingsService.validatePassword(user.getPassword());
        user.setRole(UserRole.USER); // Mặc định là USER
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        return ResponseEntity.ok(userService.createUser(user));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateUser(
            Authentication authentication,
            @PathVariable Long id,
            @RequestBody Map<String, String> body
    ) {
        User user = userService.getUserById(id);
        boolean currentAdmin = authentication != null && user.getEmail().equalsIgnoreCase(authentication.getName());

        if (currentAdmin && (body.containsKey("blocked")
                || (body.containsKey("role") && !"ADMIN".equalsIgnoreCase(body.get("role"))))) {
            return ResponseEntity.badRequest().body(Map.of("error", "You cannot block or demote your own admin account"));
        }

        if (body.containsKey("name") && !body.get("name").isBlank()) {
            user.setName(body.get("name").trim());
        }
        if (body.containsKey("role")) {
            user.setRole(UserRole.valueOf(body.get("role").toUpperCase()));
        }
        if (body.containsKey("blocked")) {
            boolean blocked = Boolean.parseBoolean(body.get("blocked"));
            user.setLockedUntil(blocked ? LocalDateTime.now().plusYears(100) : null);
            if (!blocked) user.setFailedLoginAttempts(0);
        }

        return ResponseEntity.ok(userService.createUser(user));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteUser(Authentication authentication, @PathVariable Long id) {
        User user = userService.getUserById(id);
        if (authentication != null && user.getEmail().equalsIgnoreCase(authentication.getName())) {
            return ResponseEntity.badRequest().body(Map.of("error", "You cannot delete your own admin account"));
        }
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
}
