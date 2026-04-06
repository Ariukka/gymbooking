package com.example.gymbooking.controller;

import com.example.gymbooking.model.Notification;
import com.example.gymbooking.repository.NotificationRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping({"/api/admin/notifications", "/api/notifications/admin"})
public class AdminNotificationController {

    private final NotificationRepository notificationRepository;

    public AdminNotificationController(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @GetMapping
    public ResponseEntity<List<Notification>> getAllNotificationsForAdmin() {
        List<Notification> notifications = notificationRepository.findAll()
                .stream()
                .sorted(Comparator.comparing(Notification::getCreatedAt).reversed())
                .toList();
        return ResponseEntity.ok(notifications);
    }

    @PostMapping
    public ResponseEntity<List<Notification>> getAllNotificationsForAdminPost() {
        return getAllNotificationsForAdmin();
    }
}
