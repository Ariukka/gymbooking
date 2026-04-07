package com.example.gymbooking.controller;

import com.example.gymbooking.model.Notification;
import com.example.gymbooking.model.User;
import com.example.gymbooking.repository.NotificationRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping({"/api/admin/notifications", "/api/notifications/admin"})
public class AdminNotificationController {

    private final NotificationRepository notificationRepository;

    public AdminNotificationController(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @GetMapping
    public ResponseEntity<List<Notification>> getAllNotificationsForAdmin(@AuthenticationPrincipal User currentUser) {
        if (!isAdmin(currentUser)) {
            return ResponseEntity.ok(List.of());
        }

        List<Notification> notifications = notificationRepository.findAll()
                .stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(
                        Notification::getCreatedAt,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                        .reversed())
                .toList();
        return ResponseEntity.ok(notifications);
    }

    @PostMapping
    public ResponseEntity<List<Notification>> getAllNotificationsForAdminPost(@AuthenticationPrincipal User currentUser) {
        return getAllNotificationsForAdmin(currentUser);
    }

    private boolean isAdmin(User user) {
        if (user == null || user.getRole() == null) {
            return false;
        }

        String role = user.getRole().trim().toUpperCase();
        if (role.startsWith("ROLE_")) {
            role = role.substring(5);
        }

        return "ADMIN".equals(role)
                || "SUPER_ADMIN".equals(role)
                || "GYM_ADMIN".equals(role);
    }
}
