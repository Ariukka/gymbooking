package com.example.gymbooking.controller;

import com.example.gymbooking.model.Gym;
import com.example.gymbooking.model.GymComment;
import com.example.gymbooking.model.User;
import com.example.gymbooking.repository.GymCommentRepository;
import com.example.gymbooking.repository.GymRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/gyms/{gymId}/comments")
@CrossOrigin(origins = "http://localhost:3000")
public class GymCommentController {

    private final GymCommentRepository gymCommentRepository;
    private final GymRepository gymRepository;

    public GymCommentController(GymCommentRepository gymCommentRepository, GymRepository gymRepository) {
        this.gymCommentRepository = gymCommentRepository;
        this.gymRepository = gymRepository;
    }

    @GetMapping
    public ResponseEntity<?> getGymComments(@PathVariable Long gymId) {
        if (!gymRepository.existsById(gymId)) {
            return ResponseEntity.notFound().build();
        }

        List<GymComment> comments = gymCommentRepository.findByGymIdOrderByCreatedAtDesc(gymId);
        return ResponseEntity.ok(comments);
    }

    @PostMapping
    public ResponseEntity<?> addComment(@PathVariable Long gymId,
                                        @AuthenticationPrincipal User user,
                                        @RequestBody Map<String, String> payload) {
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
        return ResponseEntity.ok(savedComment);
    }
}
