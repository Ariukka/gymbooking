package com.example.gymbooking.controller;

import com.example.gymbooking.model.Gym;
import com.example.gymbooking.model.User;
import com.example.gymbooking.repository.GymRepository;
import com.example.gymbooking.repository.UserRepository;
import com.example.gymbooking.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/gyms")
@CrossOrigin(origins = "http://localhost:3000")
public class GymController {

    private final GymRepository gymRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public GymController(GymRepository gymRepository,
                         UserRepository userRepository,
                         NotificationService notificationService) {
        this.gymRepository = gymRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    // Get all approved gyms
    @GetMapping
    public List<Gym> getAllGyms() {
        return gymRepository.findByApprovedTrueAndActiveTrue();
    }

    // Get gym by ID
    @GetMapping("/{id}")
    public ResponseEntity<Gym> getGymById(@PathVariable Long id) {
        return gymRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // GymController.java - createGym method
    @PostMapping
    public ResponseEntity<?> createGym(@AuthenticationPrincipal User user,
                                       @RequestBody Gym gym) {
        System.out.println("=== CREATE GYM REQUEST ===");
        System.out.println("User: " + (user != null ? user.getUsername() : "null"));
        System.out.println("Gym data: " + gym);
        
        try {
            gym.setOwnerUser(user);
            gym.setApproved(false);
            gym.setActive(true);
            gym.setRequestedAt(LocalDateTime.now());

            Gym savedGym = gymRepository.save(gym);

            // ✅ Админуудад мэдэгдэл илгээх
            List<User> admins = userRepository.findByRole("ADMIN");
            for (User admin : admins) {
                notificationService.createGymRequestNotification(admin, user, savedGym);
            }

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Gym request submitted. Waiting for admin approval.",
                    "gym", savedGym
            ));
        } catch (Exception e) {
            System.err.println("Create gym error: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage(),
                "message", "Gym creation failed: " + e.getMessage()
            ));
        }
    }

    // Update gym
    @PutMapping("/{id}")
    public ResponseEntity<?> updateGym(@PathVariable Long id,
                                       @RequestBody Gym updatedGym,
                                       @AuthenticationPrincipal User user) {
        Gym gym = gymRepository.findById(id).orElse(null);

        if (gym == null) {
            return ResponseEntity.notFound().build();
        }

        // Check if user is owner
        if (!gym.getOwnerUser().getId().equals(user.getId())) {
            return ResponseEntity.status(403).body(Map.of("error", "Unauthorized"));
        }

        // Update fields
        if (updatedGym.getName() != null) {
            gym.setName(updatedGym.getName());
        }
        if (updatedGym.getLocation() != null) {
            gym.setLocation(updatedGym.getLocation());
        }
        if (updatedGym.getDescription() != null) {
            gym.setDescription(updatedGym.getDescription());
        }
        if (updatedGym.getPhone() != null) {
            gym.setPhone(updatedGym.getPhone());
        }

        Gym savedGym = gymRepository.save(gym);
        return ResponseEntity.ok(savedGym);
    }

    // Get gyms by owner
    @GetMapping("/my-gyms")
    public ResponseEntity<List<Gym>> getMyGyms(@AuthenticationPrincipal User user) {
        List<Gym> gyms = gymRepository.findByOwnerUser(user);
        return ResponseEntity.ok(gyms);
    }
}