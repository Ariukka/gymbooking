package com.example.gymbooking.controller;

import com.example.gymbooking.model.User;
import com.example.gymbooking.repository.UserRepository;
import com.example.gymbooking.service.OtpService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/otp")
@CrossOrigin(origins = "http://localhost:3000")
public class OtpController {

    @Autowired
    private OtpService otpService;

    @Autowired
    private UserRepository userRepository;

    // Temporary stores for OTP (in production, use database)
    private final Map<String, String> registerOtpStore = new ConcurrentHashMap<>();
    private final Map<String, User> tempUserStore = new ConcurrentHashMap<>();

    private static final SecureRandom random = new SecureRandom();

    // ==================== SEND OTP FOR REGISTRATION ====================
    @PostMapping("/register/send-otp")
    public ResponseEntity<?> sendRegisterOtp(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        String firstName = request.get("firstName");
        String lastName = request.get("lastName");
        String phone = request.get("phone");
        String password = request.get("password");

        System.out.println("=== SEND REGISTER OTP ===");
        System.out.println("Email: " + email);

        // Validation
        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Email xaayg shaardlagatai"
            ));
        }

        if (firstName == null || password == null) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Utas, nuuts ug shaardlagatai"
            ));
        }

        // Check if user already exists
        if (userRepository.findByEmail(email).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "success", false,
                "message", "Ene email al burtgeltei baina"
            ));
        }

        // Generate OTP
        String otp = otpService.generateOtp();
        registerOtpStore.put(email, otp);

        // Create temp user
        User tempUser = new User();
        tempUser.setFirstName(firstName);
        tempUser.setLastName(lastName != null ? lastName : "");
        tempUser.setEmail(email);
        tempUser.setPhone(phone);
        tempUser.setPassword(password); // Will be encoded later
        tempUser.setRole("USER");
        tempUser.setVerified(false);
        tempUserStore.put(email, tempUser);

        System.out.println("Generated OTP for " + email + ": " + otp);

        // Send OTP email
        try {
            otpService.sendRegistrationOtp(email, otp);
            System.out.println("OTP email sent to: " + email);
        } catch (Exception e) {
            System.err.println("Failed to send OTP email: " + e.getMessage());
            registerOtpStore.remove(email);
            tempUserStore.remove(email);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "success", false,
                "message", "Email ilgeehed aldaa garlaa"
            ));
        }

        // Set expiration (10 minutes)
        new Thread(() -> {
            try {
                Thread.sleep(10 * 60 * 1000);
                registerOtpStore.remove(email);
                tempUserStore.remove(email);
                System.out.println("OTP expired for: " + email);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();

        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "Batluulakh kod email ruu ilgeegdlee",
            "email", email,
            "otpForDev", otp // Development only
        ));
    }

    // ==================== VERIFY OTP FOR REGISTRATION ====================
    @PostMapping("/register/verify-otp")
    public ResponseEntity<?> verifyRegisterOtp(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        String otp = request.get("otp");

        System.out.println("=== VERIFY REGISTER OTP ===");
        System.out.println("Email: " + email + ", OTP: " + otp);

        if (email == null || otp == null) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Email bolon kod oruulna uu"
            ));
        }

        String savedOtp = registerOtpStore.get(email);
        if (savedOtp == null) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Kod hugatsaa duussan esvel buruu baina. Dahiin khuseelt ilgeene uu"
            ));
        }

        if (!savedOtp.equals(otp)) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Kod buruu baina. Dahiin oroodono uu"
            ));
        }

        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "Kod zov baina. Burtgelee duusgana uu"
        ));
    }

    // ==================== COMPLETE REGISTRATION ====================
    @PostMapping("/register/complete")
    public ResponseEntity<?> completeRegistration(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        String otp = request.get("otp");

        System.out.println("=== COMPLETE REGISTRATION ===");
        System.out.println("Email: " + email + ", OTP: " + otp);

        if (email == null || otp == null) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Email bolon kod oruulna uu"
            ));
        }

        String savedOtp = registerOtpStore.get(email);
        if (savedOtp == null || !savedOtp.equals(otp)) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Kod buruu baina. Dahiin oroodono uu"
            ));
        }

        User tempUser = tempUserStore.get(email);
        if (tempUser == null) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Hereglegchiin medeelel oldsonguui. Dahiin butguulnee uu"
            ));
        }

        try {
            // Save user
            userRepository.save(tempUser);

            // Clean up
            registerOtpStore.remove(email);
            tempUserStore.remove(email);

            System.out.println("User registered successfully: " + email);

            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Burtgel amjilttai batluula",
                "user", Map.of(
                    "id", tempUser.getId(),
                    "firstName", tempUser.getFirstName(),
                    "lastName", tempUser.getLastName(),
                    "email", tempUser.getEmail(),
                    "phone", tempUser.getPhone(),
                    "role", tempUser.getRole()
                )
            ));

        } catch (Exception e) {
            System.err.println("Registration error: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "success", false,
                "message", "Burtgel duusgaad aldaa garlaa: " + e.getMessage()
            ));
        }
    }
}
