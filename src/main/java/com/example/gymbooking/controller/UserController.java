package com.example.gymbooking.controller;

import com.example.gymbooking.model.Booking;
import com.example.gymbooking.model.Notification;
import com.example.gymbooking.model.User;
import com.example.gymbooking.repository.BookingRepository;
import com.example.gymbooking.repository.NotificationRepository;
import com.example.gymbooking.repository.UserRepository;
import com.example.gymbooking.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = "http://localhost:3000")
public class UserController {

    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;

    public UserController(UserRepository userRepository,
                          BookingRepository bookingRepository,
                          NotificationRepository notificationRepository,
                          NotificationService notificationService) {
        this.userRepository = userRepository;
        this.bookingRepository = bookingRepository;
        this.notificationRepository = notificationRepository;
        this.notificationService = notificationService;
    }

    // Get current user profile
    @GetMapping("/me")
    public ResponseEntity<User> getCurrentUser(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(user);
    }

    // Get user by ID
    @GetMapping("/{id}")
    public ResponseEntity<User> getUserById(@PathVariable Long id) {
        return userRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // Update current user profile
    @PutMapping("/me")
    public ResponseEntity<User> updateCurrentUser(@AuthenticationPrincipal User currentUser,
                                                  @RequestBody User updatedUser) {
        if (updatedUser.getFirstName() != null) {
            currentUser.setFirstName(updatedUser.getFirstName());
        }
        if (updatedUser.getLastName() != null) {
            currentUser.setLastName(updatedUser.getLastName());
        }
        if (updatedUser.getEmail() != null) {
            currentUser.setEmail(updatedUser.getEmail());
        }
        if (updatedUser.getPhone() != null) {
            currentUser.setPhone(updatedUser.getPhone());
        }

        User savedUser = userRepository.save(currentUser);
        return ResponseEntity.ok(savedUser);
    }

    // Get current user's bookings
    @GetMapping("/me/bookings")
    public ResponseEntity<List<Booking>> getMyBookings(@AuthenticationPrincipal User user) {
        // FIX: Use the correct method name
        List<Booking> bookings = bookingRepository.findByUser_IdOrderByCreatedAtDesc(user.getId());
        return ResponseEntity.ok(bookings);
    }

    // Get current user's notifications
    @GetMapping("/me/notifications")
    public ResponseEntity<List<Notification>> getMyNotifications(@AuthenticationPrincipal User user) {
        List<Notification> notifications = notificationRepository.findByUser_IdOrderByCreatedAtDesc(user.getId());
        return ResponseEntity.ok(notifications);
    }

    // Get current user's unread notifications count
    @GetMapping("/me/notifications/unread-count")
    public ResponseEntity<Map<String, Long>> getUnreadNotificationsCount(@AuthenticationPrincipal User user) {
        long count = notificationRepository.countByUser_IdAndReadFalse(user.getId());
        return ResponseEntity.ok(Map.of("count", count));
    }

    // Mark notification as read
    @PutMapping("/me/notifications/{notificationId}/read")
    public ResponseEntity<?> markNotificationAsRead(@AuthenticationPrincipal User user,
                                                    @PathVariable Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId).orElse(null);

        if (notification == null) {
            return ResponseEntity.notFound().build();
        }

        if (!notification.getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(403).body(Map.of("error", "Unauthorized"));
        }

        notification.setRead(true);
        notificationRepository.save(notification);

        return ResponseEntity.ok(Map.of("success", true, "message", "Notification marked as read"));
    }

    // Mark all notifications as read
    @PutMapping("/me/notifications/read-all")
    public ResponseEntity<?> markAllNotificationsAsRead(@AuthenticationPrincipal User user) {
        List<Notification> unreadNotifications = notificationRepository.findByUser_IdAndReadFalseOrderByCreatedAtDesc(user.getId());

        for (Notification notification : unreadNotifications) {
            notification.setRead(true);
        }

        notificationRepository.saveAll(unreadNotifications);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "All notifications marked as read",
                "count", unreadNotifications.size()
        ));
    }

    // Delete notification
    @DeleteMapping("/me/notifications/{notificationId}")
    public ResponseEntity<?> deleteNotification(@AuthenticationPrincipal User user,
                                                @PathVariable Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId).orElse(null);

        if (notification == null) {
            return ResponseEntity.notFound().build();
        }

        if (!notification.getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(403).body(Map.of("error", "Unauthorized"));
        }

        notificationRepository.delete(notification);

        return ResponseEntity.ok(Map.of("success", true, "message", "Notification deleted"));
    }

    // Delete user account
    @DeleteMapping("/me")
    public ResponseEntity<?> deleteAccount(@AuthenticationPrincipal User user) {
        // Delete user's notifications
        notificationRepository.deleteByUser_Id(user.getId());

        // Delete user's bookings (or set to cancelled)
        List<Booking> userBookings = bookingRepository.findByUser_Id(user.getId());
        for (Booking booking : userBookings) {
            booking.setStatus("CANCELLED");
            bookingRepository.save(booking);
        }

        // Delete user
        userRepository.delete(user);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Account deleted successfully"
        ));
    }
}