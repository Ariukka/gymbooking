package com.example.gymbooking.controller;

import com.example.gymbooking.model.Gym;
import com.example.gymbooking.model.GymComment;
import com.example.gymbooking.model.User;
import com.example.gymbooking.repository.GymCommentRepository;
import com.example.gymbooking.repository.GymRepository;
import com.example.gymbooking.service.NotificationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@CrossOrigin(origins = "http://localhost:3000")
public class GymCommentController {

    private final GymCommentRepository gymCommentRepository;
    private final GymRepository gymRepository;
    private final NotificationService notificationService;

    public GymCommentController(GymCommentRepository gymCommentRepository,
                                GymRepository gymRepository,
                                NotificationService notificationService) {
        this.gymCommentRepository = gymCommentRepository;
        this.gymRepository = gymRepository;
        this.notificationService = notificationService;
    }

    @GetMapping({"/api/gyms/{gymId}/comments", "/api/gyms/{gymId}/comments/", "/gyms/{gymId}/comments", "/gyms/{gymId}/comments/"})
    public ResponseEntity<?> getGymComments(@PathVariable Long gymId) {
        if (!gymRepository.existsById(gymId)) {
            return ResponseEntity.ok(List.of());
        }

        List<GymComment> comments = gymCommentRepository.findByGymIdOrderByCreatedAtDesc(gymId);
        return ResponseEntity.ok(comments);
    }

    @GetMapping({"/api/comments", "/api/comments/", "/comments", "/comments/"})
    public ResponseEntity<?> getGymCommentsByQuery(@RequestParam(name = "gymId", required = false) String rawGymId) {
        Long gymId = parseGymId(rawGymId);
        if (gymId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "gymId is required"));
        }
        return getGymComments(gymId);
    }

    @PostMapping({"/api/gyms/{gymId}/comments", "/api/gyms/{gymId}/comments/", "/gyms/{gymId}/comments", "/gyms/{gymId}/comments/"})
    public ResponseEntity<?> addCommentByGym(@PathVariable Long gymId,
                                             @AuthenticationPrincipal User user,
                                             @RequestBody Map<String, String> payload) {
        return createComment(gymId, user, payload);
    }

    @PostMapping({"/api/comments", "/api/comments/", "/comments", "/comments/"})
    @CrossOrigin(origins = "*", allowedHeaders = "*")
    public ResponseEntity<?> addComment(@AuthenticationPrincipal User user,
                                        @RequestBody Map<String, String> payload) {
        if (payload == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Request body is required"));
        }
        Long gymId = parseGymId(payload.get("gymId"));
        if (gymId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "gymId is required"));
        }
        return createComment(gymId, user, payload);
    }

    @DeleteMapping({
            "/api/gyms/{gymId}/comments/{commentId}",
            "/api/gyms/{gymId}/comments/{commentId}/",
            "/gyms/{gymId}/comments/{commentId}",
            "/gyms/{gymId}/comments/{commentId}/"
    })
    public ResponseEntity<?> deleteOwnComment(@PathVariable Long gymId,
                                              @PathVariable Long commentId,
                                              @AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Authentication required"));
        }

        GymComment comment = gymCommentRepository.findByIdAndGymId(commentId, gymId).orElse(null);
        if (comment == null) {
            return ResponseEntity.notFound().build();
        }

        if (!comment.getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "You can only delete your own comment"));
        }

        gymCommentRepository.delete(comment);
        return ResponseEntity.ok(Map.of("message", "Comment deleted successfully"));
    }

    @DeleteMapping({"/api/comments/{commentId}", "/api/comments/{commentId}/", "/comments/{commentId}", "/comments/{commentId}/"})
    public ResponseEntity<?> deleteOwnComment(@PathVariable Long commentId,
                                              @AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Authentication required"));
        }

        GymComment comment = gymCommentRepository.findByIdAndUserId(commentId, user.getId()).orElse(null);
        if (comment == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Comment not found or not owned by user"));
        }

        gymCommentRepository.delete(comment);
        return ResponseEntity.ok(Map.of("message", "Comment deleted successfully"));
    }

    private ResponseEntity<?> createComment(Long gymId, User user, Map<String, String> payload) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Authentication required"));
        }
        if (payload == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Request body is required"));
        }

        Gym gym = gymRepository.findById(gymId).orElse(null);
        if (gym == null) {
            return ResponseEntity.notFound().build();
        }

        String commentText = payload.get("comment");
        if (commentText == null || commentText.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Comment is required"));
        }

        GymComment comment = new GymComment();
        comment.setGym(gym);
        comment.setUser(user);
        comment.setComment(commentText.trim());

        GymComment savedComment = gymCommentRepository.save(comment);

        try {
            notificationService.createGymCommentNotificationForAdmins(savedComment);
        } catch (RuntimeException ignored) {
            // Comment үүсэх урсгалыг notification алдаанаас болж таслахгүй.
        }

        return ResponseEntity.ok(savedComment);
    }

    private Long parseGymId(String rawGymId) {
        if (rawGymId == null || rawGymId.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(rawGymId);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
