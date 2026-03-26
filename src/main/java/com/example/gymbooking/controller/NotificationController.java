package com.example.gymbooking.controller;

import com.example.gymbooking.model.Notification;
import com.example.gymbooking.model.User;
import com.example.gymbooking.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    @Autowired
    private NotificationService notificationService;

    /**
     * Хэрэглэгчийн өөрийн мэдэгдлүүдийг авах
     */
    @GetMapping({"/my-notifications", "/my"})
    public ResponseEntity<List<Notification>> getMyNotifications(@AuthenticationPrincipal User currentUser) {
        List<Notification> notifications = notificationService.getMyNotifications(currentUser);
        return ResponseEntity.ok(notifications);
    }

    /**
     * Хэрэглэгчийн уншигдаагүй мэдэгдлүүдийг авах
     */
    @GetMapping("/unread")
    public ResponseEntity<List<Notification>> getUnreadNotifications(@AuthenticationPrincipal User currentUser) {
        List<Notification> unreadNotifications = notificationService.getUnreadNotificationsByUserId(currentUser.getId());
        return ResponseEntity.ok(unreadNotifications);
    }

    /**
     * Мэдэгдлийг уншсан болгох
     */
    @PutMapping("/{notificationId}/read")
    public ResponseEntity<Notification> markAsRead(@PathVariable Long notificationId) {
        Notification notification = notificationService.markAsRead(notificationId);
        return ResponseEntity.ok(notification);
    }

    /**
     * Бүх мэдэгдлийг уншсан болгох
     */
    @PutMapping("/read-all")
    public ResponseEntity<Void> markAllAsRead(@AuthenticationPrincipal User currentUser) {
        notificationService.markAllAsRead(currentUser.getId());
        return ResponseEntity.ok().build();
    }

    /**
     * Уншигдаагүй мэдэгдлийн тоог авах
     */
    @GetMapping("/unread-count")
    public ResponseEntity<Long> getUnreadCount(@AuthenticationPrincipal User currentUser) {
        long count = notificationService.getUnreadNotificationCount(currentUser.getId());
        return ResponseEntity.ok(count);
    }

    /**
     * Мэдэгдэл устгах
     */
    @DeleteMapping("/{notificationId}")
    public ResponseEntity<Void> deleteNotification(@PathVariable Long notificationId) {
        notificationService.deleteNotification(notificationId);
        return ResponseEntity.ok().build();
    }
}