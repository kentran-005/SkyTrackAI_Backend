package com.skytrack.ai.controller;

import com.skytrack.ai.entity.Notification;
import com.skytrack.ai.entity.User;
import com.skytrack.ai.exception.ResourceNotFoundException;
import com.skytrack.ai.repository.NotificationRepository;
import com.skytrack.ai.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    @GetMapping("/me")
    public ResponseEntity<List<Notification>> getMyNotifications(Authentication authentication) {
        return ResponseEntity.ok(
                notificationRepository.findByUserIdOrderByCreatedAtDesc(currentUser(authentication).getId())
        );
    }

    @PutMapping("/me/{id}/read")
    public ResponseEntity<Void> markMyNotificationAsRead(
            Authentication authentication,
            @PathVariable Long id
    ) {
        Notification notification = ownedNotification(authentication, id);
        notification.setRead(true);
        notificationRepository.save(notification);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/me/read-all")
    public ResponseEntity<Void> markAllMyNotificationsAsRead(Authentication authentication) {
        List<Notification> notifications = notificationRepository.findByUserIdOrderByCreatedAtDesc(
                currentUser(authentication).getId()
        );
        notifications.forEach(notification -> notification.setRead(true));
        notificationRepository.saveAll(notifications);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/me/{id}")
    public ResponseEntity<Void> deleteMyNotification(
            Authentication authentication,
            @PathVariable Long id
    ) {
        notificationRepository.delete(ownedNotification(authentication, id));
        return ResponseEntity.noContent().build();
    }

    private Notification ownedNotification(Authentication authentication, Long id) {
        Long userId = currentUser(authentication).getId();
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));
        if (!userId.equals(notification.getUserId())) {
            throw new ResourceNotFoundException("Notification not found");
        }
        return notification;
    }

    private User currentUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new IllegalArgumentException("Authenticated user is required");
        }
        return userRepository.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("User account not found"));
    }
}
