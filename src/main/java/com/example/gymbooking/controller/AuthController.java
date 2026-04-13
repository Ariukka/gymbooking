package com.example.gymbooking.controller;

import com.example.gymbooking.config.JwtUtil;
import com.example.gymbooking.model.Booking;
import com.example.gymbooking.model.User;
import com.example.gymbooking.repository.BookingRepository;
import com.example.gymbooking.repository.NotificationRepository;
import com.example.gymbooking.repository.UserRepository;
import com.example.gymbooking.security.LoginAttemptService;
import com.example.gymbooking.service.AuthService;
import com.example.gymbooking.service.AuditLogService;
import com.example.gymbooking.service.OtpService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

@RestController
@RequestMapping({"/api/auth", "/auth"})
@CrossOrigin(origins = "http://localhost:3000")
public class AuthController {

    private final OtpService otpService;
    private final AuthService authService;
    private final AuditLogService auditLogService;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;
    private final NotificationRepository notificationRepository;
    private final JwtUtil jwtUtil;
    private final LoginAttemptService loginAttemptService;

    @Autowired
    private JavaMailSender mailSender;

    // OTP Ñ…Ð°Ð´Ð³Ð°Ð»Ð°Ñ… stores
    private final Map<String, String> emailOtpStore = new HashMap<>();
    private final Map<String, String> emailResetStore = new HashMap<>();
    private final Map<String, String> phoneOtpStore = new HashMap<>();
    private final Map<String, String> registerOtpStore = new HashMap<>();
    private final Map<String, User> tempUserStore = new HashMap<>();

    public AuthController(OtpService otpService, AuthService authService,
                          AuditLogService auditLogService,
                          PasswordEncoder passwordEncoder, UserRepository userRepository,
                          BookingRepository bookingRepository,
                          NotificationRepository notificationRepository,
                          JwtUtil jwtUtil,
                          LoginAttemptService loginAttemptService) {
        this.otpService = otpService;
        this.authService = authService;
        this.auditLogService = auditLogService;
        this.passwordEncoder = passwordEncoder;
        this.userRepository = userRepository;
        this.bookingRepository = bookingRepository;
        this.notificationRepository = notificationRepository;
        this.jwtUtil = jwtUtil;
        this.loginAttemptService = loginAttemptService;
    }

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(@AuthenticationPrincipal User currentUser) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "success", false,
                    "message", "Unauthorized"
            ));
        }

        return ResponseEntity.ok(Map.of(
                "id", currentUser.getId(),
                "username", currentUser.getUsername(),
                "phone", currentUser.getPhone(),
                "email", currentUser.getEmail(),
                "firstName", currentUser.getFirstName(),
                "lastName", currentUser.getLastName(),
                "role", currentUser.getRole()
        ));
    }

    @DeleteMapping("/me")
    public ResponseEntity<?> deleteCurrentUser(@AuthenticationPrincipal User currentUser) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "success", false,
                    "message", "Unauthorized"
            ));
        }

        notificationRepository.deleteByUser_Id(currentUser.getId());

        List<Booking> userBookings = bookingRepository.findByUser_Id(currentUser.getId());
        for (Booking booking : userBookings) {
            booking.setStatus("CANCELLED");
            bookingRepository.save(booking);
        }

        userRepository.delete(currentUser);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Account deleted successfully"
        ));
    }

    // ==================== LOGIN ====================
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> payload,
                                   HttpServletRequest request) {
        String identifier = payload.getOrDefault("identifier", payload.get("phone"));
        String password = payload.get("password");
        String clientIp = request.getRemoteAddr();
        String loginKey = (identifier != null && !identifier.isBlank()) ? identifier : clientIp;

        System.out.println("=== LOGIN ATTEMPT ===");
        System.out.println("Identifier: " + identifier);
        System.out.println("Client IP: " + clientIp);

        if (identifier == null || password == null) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Ð£Ñ‚Ð°ÑÐ½Ñ‹ Ð´ÑƒÐ³Ð°Ð°Ñ€ Ð±Ð¾Ð»Ð¾Ð½ Ð½ÑƒÑƒÑ† Ò¯Ð³ÑÑ Ð¾Ñ€ÑƒÑƒÐ»Ð½Ð° ÑƒÑƒ");
            return ResponseEntity.badRequest().body(response);
        }

        if (loginAttemptService.isBlocked(loginKey)) {
            auditLogService.log(null, "LOGIN_BLOCKED", "Authentication", null,
                    String.format("Blocked login for '%s' from %s after multiple failures",
                            safeIdentifier(identifier), clientIp));
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", String.format("Ð¢Ð¸Ð¹Ð¼ Ð¾Ð»Ð¾Ð½ ÑƒÐ´Ð°Ð³Ð¸Ð¹Ð½ Ð½ÑÐ²Ñ‚Ñ€ÑÐ»Ñ‚ Ð¾Ñ€Ð¾Ð»Ð´Ð»Ð¾Ð³Ð¾ Ð¸Ð»ÑÑÑÐ½ ÑÑƒÐ» %d Ð¼Ð¸Ð½ÑƒÑ‚ Ð´Ð°Ñ€Ð°Ð° Ð´Ð°Ñ…Ð¸Ð½ Ð¾Ñ€Ð¾Ð»Ð´Ð¾Ð½Ð¾ ÑƒÑƒ.",
                    loginAttemptService.getBlockDurationMinutes()));
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(response);
        }

        try {
            Optional<User> userOpt = authService.findByUsername(identifier);
            if (userOpt.isEmpty()) {
                System.out.println("User not found with identifier: " + identifier);
                recordFailedLogin(loginKey, identifier, clientIp, "user not found");
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "Ð£Ñ‚Ð°Ñ ÑÑÐ²ÑÐ» Ð½ÑƒÑƒÑ† Ò¯Ð³ Ð±ÑƒÑ€ÑƒÑƒ Ð±Ð°Ð¹Ð½Ð°");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            }

            User user = userOpt.get();

            if (!passwordEncoder.matches(password, user.getPassword())) {
                System.out.println("Invalid password for user: " + identifier);
                recordFailedLogin(loginKey, identifier, clientIp, "wrong password");
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "Ð£Ñ‚Ð°Ñ ÑÑÐ²ÑÐ» Ð½ÑƒÑƒÑ† Ò¯Ð³ Ð±ÑƒÑ€ÑƒÑƒ Ð±Ð°Ð¹Ð½Ð°");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            }

            String tokenSubject = user.getUsername();
            if (tokenSubject == null || tokenSubject.isBlank()) {
                tokenSubject = (user.getEmail() != null && !user.getEmail().isBlank())
                        ? user.getEmail()
                        : user.getPhone();
            }

            if (tokenSubject == null || tokenSubject.isBlank()) {
                System.out.println("Missing login identifier for user id: " + user.getId());
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "Ð¥ÑÑ€ÑÐ³Ð»ÑÐ³Ñ‡Ð¸Ð¹Ð½ Ð¼ÑÐ´ÑÑÐ»Ð»ÑÐ´ Ð´ÑƒÑ‚ÑƒÑƒ Ð±Ð°Ð¹Ð½Ð°");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            }

            loginAttemptService.loginSucceeded(loginKey);

            String token = jwtUtil.generateToken(tokenSubject);

            Map<String, Object> userMap = new HashMap<>();
            userMap.put("id", user.getId());
            userMap.put("name", (user.getFirstName() != null ? user.getFirstName() : "") +
                    (user.getLastName() != null ? " " + user.getLastName() : ""));
            userMap.put("firstName", user.getFirstName() != null ? user.getFirstName() : "");
            userMap.put("lastName", user.getLastName() != null ? user.getLastName() : "");
            userMap.put("phone", user.getPhone());
            userMap.put("email", user.getEmail() != null ? user.getEmail() : "");
            userMap.put("role", user.getRole());

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "ÐÑÐ²Ñ‚Ñ€ÑÐ»Ñ‚ Ð°Ð¼Ð¶Ð¸Ð»Ñ‚ÑÐ°Ð¹");
            response.put("token", token);
            response.put("user", userMap);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            System.err.println("Login error: " + e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Ð¡Ð¸ÑÑ‚ÐµÐ¼Ð¸Ð¹Ð½ Ð°Ð»Ð´Ð°Ð° Ð³Ð°Ñ€Ð»Ð°Ð°");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    private void recordFailedLogin(String loginKey, String identifier, String clientIp, String reason) {
        boolean justBlocked = loginAttemptService.loginFailed(loginKey);
        if (justBlocked) {
            auditLogService.log(null, "LOGIN_BRUTE_FORCE", "Authentication", null,
                    String.format("Identifier '%s' from %s blocked after repeated failures (%s)",
                            safeIdentifier(identifier), clientIp, reason));
        }
    }

    private String safeIdentifier(String identifier) {
        return (identifier == null || identifier.isBlank()) ? "unknown" : identifier;
    }

    // ==================== REGISTER (DIRECT - NO OTP) ====================
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> request) {
        String firstName = request.get("firstName");
        String lastName = request.get("lastName");
        String phone = request.get("phone");
        String email = request.get("email");
        String password = request.get("password");

        System.out.println("=== REGISTER (DIRECT) ===");
        System.out.println("Phone: " + phone);
        System.out.println("Email: " + email);

        // Validation
        if (firstName == null || phone == null || password == null) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "ÐÑÑ€, ÑƒÑ‚Ð°Ñ, Ð½ÑƒÑƒÑ† Ò¯Ð³ ÑˆÐ°Ð°Ñ€Ð´Ð»Ð°Ð³Ð°Ñ‚Ð°Ð¹");
            return ResponseEntity.badRequest().body(response);
        }

        // Check if user already exists by phone
        if (userRepository.findByPhone(phone).isPresent()) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Ð­Ð½Ñ ÑƒÑ‚Ð°ÑÐ½Ñ‹ Ð´ÑƒÐ³Ð°Ð°Ñ€ Ð°Ð»ÑŒ Ñ…ÑÐ´Ð¸Ð¹Ð½ Ð±Ò¯Ñ€Ñ‚Ð³ÑÐ»Ñ‚ÑÐ¹ Ð±Ð°Ð¹Ð½Ð°");
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }

        // Check if user already exists by email
        if (email != null && !email.isBlank() && userRepository.findByEmail(email).isPresent()) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Ð­Ð½Ñ Ð¸Ð¼ÑÐ¹Ð» Ñ…Ð°ÑÐ³ Ð°Ð»ÑŒ Ñ…ÑÐ´Ð¸Ð¹Ð½ Ð±Ò¯Ñ€Ñ‚Ð³ÑÐ»Ñ‚ÑÐ¹ Ð±Ð°Ð¹Ð½Ð°");
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }

        try {
            // Create new user
            User user = new User();
            user.setFirstName(firstName);
            user.setLastName(lastName != null ? lastName : "");
            user.setPhone(phone);
            user.setEmail(email != null ? email : "");
            user.setPassword(passwordEncoder.encode(password));
            user.setRole("USER");
            user.setUsername(phone);
            user.setVerified(true); // Direct registration - verified immediately

            userRepository.save(user);

            // Generate token
            String token = jwtUtil.generateToken(user.getUsername());

            Map<String, Object> userMap = new HashMap<>();
            userMap.put("id", user.getId());
            userMap.put("name", (firstName != null ? firstName : "") +
                    (lastName != null ? " " + lastName : ""));
            userMap.put("firstName", firstName != null ? firstName : "");
            userMap.put("lastName", lastName != null ? lastName : "");
            userMap.put("phone", user.getPhone());
            userMap.put("email", user.getEmail() != null ? user.getEmail() : "");
            userMap.put("role", user.getRole());

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Ð‘Ò¯Ñ€Ñ‚Ð³ÑÐ» Ð°Ð¼Ð¶Ð¸Ð»Ñ‚Ñ‚Ð°Ð¹");
            response.put("token", token);
            response.put("user", userMap);

            System.out.println("âœ… User registered successfully (direct): " + phone);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            System.err.println("Registration error: " + e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Ð‘Ò¯Ñ€Ñ‚Ð³ÑÐ» Ò¯Ò¯ÑÐ³ÑÑ…ÑÐ´ Ð°Ð»Ð´Ð°Ð° Ð³Ð°Ñ€Ð»Ð°Ð°: " + e.getMessage());
            auditLogService.log(null, "LOGIN_ERROR", "Authentication", null, "Login exception for " + phone + ": " + e.getMessage());
            auditLogService.log(null, "LOGIN_ERROR", "Authentication", null, "Login exception for " + phone + ": " + e.getMessage());
return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // ==================== REGISTER - SEND OTP TO EMAIL ====================
    @PostMapping("/register/send-otp")
    public ResponseEntity<?> sendRegisterOtp(@RequestBody Map<String, String> request) {
        String phone = request.get("phone");
        String firstName = request.get("firstName");
        String lastName = request.get("lastName");
        String email = request.get("email");
        String password = request.get("password");

        System.out.println("=== SEND REGISTER OTP TO EMAIL ===");
        System.out.println("Phone: " + phone);
        System.out.println("Email: " + email);

        if (phone == null || firstName == null || password == null) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "ÐÑÑ€, ÑƒÑ‚Ð°Ñ, Ð½ÑƒÑƒÑ† Ò¯Ð³ ÑˆÐ°Ð°Ñ€Ð´Ð»Ð°Ð³Ð°Ñ‚Ð°Ð¹");
            return ResponseEntity.badRequest().body(response);
        }

        if (email == null || email.isBlank()) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Ð˜Ð¼ÑÐ¹Ð» Ñ…Ð°ÑÐ³ ÑˆÐ°Ð°Ñ€Ð´Ð»Ð°Ð³Ð°Ñ‚Ð°Ð¹");
            return ResponseEntity.badRequest().body(response);
        }

        // Check if user already exists
        if (userRepository.findByPhone(phone).isPresent()) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Ð­Ð½Ñ ÑƒÑ‚Ð°ÑÐ½Ñ‹ Ð´ÑƒÐ³Ð°Ð°Ñ€ Ð°Ð»ÑŒ Ñ…ÑÐ´Ð¸Ð¹Ð½ Ð±Ò¯Ñ€Ñ‚Ð³ÑÐ»Ñ‚ÑÐ¹ Ð±Ð°Ð¹Ð½Ð°");
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }

        if (userRepository.findByEmail(email).isPresent()) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Ð­Ð½Ñ Ð¸Ð¼ÑÐ¹Ð» Ñ…Ð°ÑÐ³ Ð°Ð»ÑŒ Ñ…ÑÐ´Ð¸Ð¹Ð½ Ð±Ò¯Ñ€Ñ‚Ð³ÑÐ»Ñ‚ÑÐ¹ Ð±Ð°Ð¹Ð½Ð°");
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }

        // Create temp user
        User tempUser = new User();
        tempUser.setFirstName(firstName);
        tempUser.setLastName(lastName != null ? lastName : "");
        tempUser.setPhone(phone);
        tempUser.setEmail(email);
        tempUser.setPassword(passwordEncoder.encode(password));
        tempUser.setRole("USER");
        tempUser.setUsername(phone);
        tempUser.setVerified(false);

        // Generate OTP
        Random random = new Random();
        String otp = String.format("%06d", random.nextInt(1000000));

        // Store by email
        registerOtpStore.put(email, otp);
        tempUserStore.put(email, tempUser);

        System.out.println("[REGISTER OTP] Email: " + email + " - OTP: " + otp);
        System.out.println("[REGISTER OTP] Phone: " + phone);

        // SEND EMAIL WITH OTP
        try {
            sendEmailOtp(email, otp, firstName);
            System.out.println("âœ… OTP email sent to: " + email);
        } catch (Exception e) {
            System.err.println("âŒ Failed to send OTP email: " + e.getMessage());
        }

        // Set expiration (10 minutes for development, 5 for production)
        new Thread(() -> {
            try {
                // 10 minutes = 600,000ms for development
                Thread.sleep(10 * 60 * 1000);
                registerOtpStore.remove(email);
                tempUserStore.remove(email);
                System.out.println("[REGISTER OTP] Expired for email: " + email);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Ð‘Ð°Ñ‚Ð°Ð»Ð³Ð°Ð°Ð¶ÑƒÑƒÐ»Ð°Ñ… ÐºÐ¾Ð´ Ð¸Ð¼ÑÐ¹Ð» Ñ…Ð°ÑÐ³ Ñ€ÑƒÑƒ Ð¸Ð»Ð³ÑÑÐ³Ð´Ð»ÑÑ");
        response.put("email", email);
        response.put("otpForDev", otp); // Development-Ð´ Ð·Ð¾Ñ€Ð¸ÑƒÐ»Ð¶

        return ResponseEntity.ok(response);
    }

    // ==================== COMPLETE REGISTRATION ====================
    @PostMapping("/register/complete")
    public ResponseEntity<?> completeRegistration(@RequestBody Map<String, String> request) {
        System.out.println("=== COMPLETE REGISTRATION ===");
        System.out.println("Request body: " + request);
        
        String email = request.get("email");
        String otp = request.getOrDefault("otp", request.get("code"));
        String tempToken = request.getOrDefault("tempToken", request.get("token"));
        String phone = request.get("phone");

        System.out.println("Email: " + email);
        System.out.println("OTP: " + otp);
        System.out.println("TempToken: " + tempToken);

        // Some clients call this endpoint after successful /register/verify-otp.
        // In that flow, OTP data may be missing, but user is already created.
        if (email == null || otp == null) {
            if (email == null && phone == null && tempToken != null) {
                try {
                    phone = jwtUtil.getUsernameFromToken(tempToken);
                    System.out.println("Resolved phone from token: " + phone);
                } catch (Exception e) {
                    System.out.println("Failed to resolve phone from token: " + e.getMessage());
                }
            }

            Optional<User> existingUserOpt = Optional.empty();
            if (email != null && !email.isBlank()) {
                existingUserOpt = userRepository.findByEmail(email);
            } else if (phone != null && !phone.isBlank()) {
                existingUserOpt = userRepository.findByPhone(phone);
            }

            if (existingUserOpt.isPresent() && existingUserOpt.get().isVerified()) {
                User existingUser = existingUserOpt.get();
                String token = jwtUtil.generateToken(existingUser.getUsername());

                Map<String, Object> userMap = new HashMap<>();
                userMap.put("id", existingUser.getId());
                userMap.put("name", (existingUser.getFirstName() != null ? existingUser.getFirstName() : "") +
                        (existingUser.getLastName() != null ? " " + existingUser.getLastName() : ""));
                userMap.put("firstName", existingUser.getFirstName() != null ? existingUser.getFirstName() : "");
                userMap.put("lastName", existingUser.getLastName() != null ? existingUser.getLastName() : "");
                userMap.put("phone", existingUser.getPhone());
                userMap.put("email", existingUser.getEmail() != null ? existingUser.getEmail() : "");
                userMap.put("role", existingUser.getRole());

                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "Ð‘Ò¯Ñ€Ñ‚Ð³ÑÐ» Ð°Ð¼Ð¶Ð¸Ð»Ñ‚Ñ‚Ð°Ð¹ Ð±Ð°Ñ‚Ð°Ð»Ð³Ð°Ð°Ð¶Ð»Ð°Ð°");
                response.put("token", token);
                response.put("user", userMap);

                return ResponseEntity.ok(response);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Ð˜Ð¼ÑÐ¹Ð» Ð±Ð¾Ð»Ð¾Ð½ ÐºÐ¾Ð´Ñ‹Ð³ Ð¾Ñ€ÑƒÑƒÐ»Ð½Ð° ÑƒÑƒ");
            return ResponseEntity.badRequest().body(response);
        }

        String savedOtp = registerOtpStore.get(email);
        if (savedOtp == null) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "ÐšÐ¾Ð´ Ñ…ÑƒÐ³Ð°Ñ†Ð°Ð° Ð´ÑƒÑƒÑÑÐ°Ð½ ÑÑÐ²ÑÐ» Ð±ÑƒÑ€ÑƒÑƒ Ð±Ð°Ð¹Ð½Ð°. Ð”Ð°Ñ…Ð¸Ð½ Ñ…Ò¯ÑÑÐ»Ñ‚ Ð¸Ð»Ð³ÑÑÐ½Ñ Ò¯Ò¯.");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        if (!savedOtp.equals(otp)) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "ÐšÐ¾Ð´ Ð±ÑƒÑ€ÑƒÑƒ Ð±Ð°Ð¹Ð½Ð°. Ð”Ð°Ñ…Ð¸Ð½ Ð¾Ñ€Ð¾Ð»Ð´Ð¾Ð½Ð¾ ÑƒÑƒ.");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        User tempUser = tempUserStore.get(email);
        if (tempUser == null) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Ð¥ÑÑ€ÑÐ³Ð»ÑÐ³Ñ‡Ð¸Ð¹Ð½ Ð¼ÑÐ´ÑÑÐ»ÑÐ» Ð¾Ð»Ð´ÑÐ¾Ð½Ð³Ò¯Ð¹. Ð”Ð°Ñ…Ð¸Ð½ Ð±Ò¯Ñ€Ñ‚Ð³Ò¯Ò¯Ð»Ð½Ñ Ò¯Ò¯.");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        try {
            tempUser.setVerified(true);
            userRepository.save(tempUser);

            registerOtpStore.remove(email);
            tempUserStore.remove(email);

            String token = jwtUtil.generateToken(tempUser.getUsername());

            Map<String, Object> userMap = new HashMap<>();
            userMap.put("id", tempUser.getId());
            userMap.put("name", (tempUser.getFirstName() != null ? tempUser.getFirstName() : "") +
                    (tempUser.getLastName() != null ? " " + tempUser.getLastName() : ""));
            userMap.put("firstName", tempUser.getFirstName() != null ? tempUser.getFirstName() : "");
            userMap.put("lastName", tempUser.getLastName() != null ? tempUser.getLastName() : "");
            userMap.put("phone", tempUser.getPhone());
            userMap.put("email", tempUser.getEmail() != null ? tempUser.getEmail() : "");
            userMap.put("role", tempUser.getRole());

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Ð‘Ò¯Ñ€Ñ‚Ð³ÑÐ» Ð°Ð¼Ð¶Ð¸Ð»Ñ‚Ñ‚Ð°Ð¹ Ð±Ð°Ñ‚Ð°Ð»Ð³Ð°Ð°Ð¶Ð»Ð°Ð°");
            response.put("token", token);
            response.put("user", userMap);

            System.out.println("âœ… User registered successfully: " + tempUser.getPhone());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            System.err.println("Registration completion error: " + e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Ð‘Ò¯Ñ€Ñ‚Ð³ÑÐ» Ð´ÑƒÑƒÑÐ³Ð°Ñ…Ð°Ð´ Ð°Ð»Ð´Ð°Ð° Ð³Ð°Ñ€Ð»Ð°Ð°: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // ==================== VERIFY REGISTER OTP ====================
    @PostMapping("/register/verify-otp")
    public ResponseEntity<?> verifyRegisterOtp(@RequestBody Map<String, String> request) {
        System.out.println("=== VERIFY REGISTER OTP ===");
        System.out.println("Request body: " + request);
        
        String email = request.get("email");
        String otp = request.get("otp");

        System.out.println("Email: " + email);
        System.out.println("OTP: " + otp);

        if (email == null || otp == null) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Ð˜Ð¼ÑÐ¹Ð» Ð±Ð¾Ð»Ð¾Ð½ ÐºÐ¾Ð´Ñ‹Ð³ Ð¾Ñ€ÑƒÑƒÐ»Ð½Ð° ÑƒÑƒ");
            return ResponseEntity.badRequest().body(response);
        }

        String savedOtp = registerOtpStore.get(email);
        if (savedOtp == null) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "ÐšÐ¾Ð´ Ñ…ÑƒÐ³Ð°Ñ†Ð°Ð° Ð´ÑƒÑƒÑÑÐ°Ð½ ÑÑÐ²ÑÐ» Ð±ÑƒÑ€ÑƒÑƒ Ð±Ð°Ð¹Ð½Ð°. Ð”Ð°Ñ…Ð¸Ð½ Ñ…Ò¯ÑÑÐ»Ñ‚ Ð¸Ð»Ð³ÑÑÐ½Ñ Ò¯Ò¯.");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        if (!savedOtp.equals(otp)) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "ÐšÐ¾Ð´ Ð±ÑƒÑ€ÑƒÑƒ Ð±Ð°Ð¹Ð½Ð°. Ð”Ð°Ñ…Ð¸Ð½ Ð¾Ñ€Ð¾Ð»Ð´Ð¾Ð½Ð¾ ÑƒÑƒ.");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        User tempUser = tempUserStore.get(email);
        if (tempUser == null) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Ð¥ÑÑ€ÑÐ³Ð»ÑÐ³Ñ‡Ð¸Ð¹Ð½ Ð¼ÑÐ´ÑÑÐ»ÑÐ» Ð¾Ð»Ð´ÑÐ¾Ð½Ð³Ò¯Ð¹. Ð”Ð°Ñ…Ð¸Ð½ Ð±Ò¯Ñ€Ñ‚Ð³Ò¯Ò¯Ð»Ð½Ñ Ò¯Ò¯.");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        try {
            tempUser.setVerified(true);
            userRepository.save(tempUser);

            registerOtpStore.remove(email);
            tempUserStore.remove(email);

            String token = jwtUtil.generateToken(tempUser.getUsername());

            Map<String, Object> userMap = new HashMap<>();
            userMap.put("id", tempUser.getId());
            userMap.put("name", (tempUser.getFirstName() != null ? tempUser.getFirstName() : "") +
                    (tempUser.getLastName() != null ? " " + tempUser.getLastName() : ""));
            userMap.put("firstName", tempUser.getFirstName() != null ? tempUser.getFirstName() : "");
            userMap.put("lastName", tempUser.getLastName() != null ? tempUser.getLastName() : "");
            userMap.put("phone", tempUser.getPhone());
            userMap.put("email", tempUser.getEmail() != null ? tempUser.getEmail() : "");
            userMap.put("role", tempUser.getRole());

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Ð‘Ò¯Ñ€Ñ‚Ð³ÑÐ» Ð°Ð¼Ð¶Ð¸Ð»Ñ‚Ñ‚Ð°Ð¹ Ð±Ð°Ñ‚Ð°Ð»Ð³Ð°Ð°Ð¶Ð»Ð°Ð°");
            response.put("token", token);
            response.put("user", userMap);

            System.out.println("âœ… User registered successfully: " + tempUser.getPhone());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            System.err.println("Registration error: " + e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Ð‘Ò¯Ñ€Ñ‚Ð³ÑÐ» Ò¯Ò¯ÑÐ³ÑÑ…ÑÐ´ Ð°Ð»Ð´Ð°Ð° Ð³Ð°Ñ€Ð»Ð°Ð°");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // ==================== SEND OTP TO EMAIL (For reset password) ====================
    @PostMapping("/send-email-otp")
    public ResponseEntity<?> sendEmailOtpForReset(@RequestBody Map<String, String> request) {
        String email = request.get("email");

        System.out.println("=== SEND EMAIL OTP ===");
        System.out.println("Email: " + email);

        if (email == null || email.isBlank()) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Ð˜Ð¼ÑÐ¹Ð» Ñ…Ð°ÑÐ³Ð°Ð° Ð¾Ñ€ÑƒÑƒÐ»Ð½Ð° ÑƒÑƒ");
            return ResponseEntity.badRequest().body(response);
        }

        Random random = new Random();
        String otp = String.format("%06d", random.nextInt(1000000));
        emailOtpStore.put(email, otp);

        System.out.println("[EMAIL OTP] Sent to: " + email + " - OTP: " + otp);

        new Thread(() -> {
            try {
                Thread.sleep(10 * 60 * 1000);
                emailOtpStore.remove(email);
                System.out.println("[EMAIL OTP] Expired for: " + email);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();

        try {
            sendEmailOtp(email, otp, "Ð¥ÑÑ€ÑÐ³Ð»ÑÐ³Ñ‡");
            System.out.println("âœ… OTP email sent to: " + email);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Ð‘Ð°Ñ‚Ð°Ð»Ð³Ð°Ð°Ð¶ÑƒÑƒÐ»Ð°Ñ… ÐºÐ¾Ð´ Ð¸Ð¼ÑÐ¹Ð» Ñ…Ð°ÑÐ³ Ñ€ÑƒÑƒ Ð¸Ð»Ð³ÑÑÐ³Ð´Ð»ÑÑ.");
            response.put("otpForDev", otp);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            System.err.println("âŒ Failed to send email: " + e.getMessage());

            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Ð˜Ð¼ÑÐ¹Ð» Ð¸Ð»Ð³ÑÑÑ…ÑÐ´ Ð°Ð»Ð´Ð°Ð° Ð³Ð°Ñ€Ð»Ð°Ð°. Ð”Ð°Ñ…Ð¸Ð½ Ð¾Ñ€Ð¾Ð»Ð´Ð¾Ð½Ð¾ ÑƒÑƒ.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // ==================== EMAIL SENDER METHOD ====================
    private void sendEmailOtp(String email, String otp, String name) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(email);
            message.setSubject("GymBooking - Ð‘Ð°Ñ‚Ð°Ð»Ð³Ð°Ð°Ð¶ÑƒÑƒÐ»Ð°Ñ… ÐºÐ¾Ð´");
            message.setText("Ð¡Ð°Ð¹Ð½ ÑƒÑƒ " + name + ",\n\nÐ¢Ð°Ð½Ñ‹ Ð±Ð°Ñ‚Ð°Ð»Ð³Ð°Ð°Ð¶ÑƒÑƒÐ»Ð°Ñ… ÐºÐ¾Ð´: " + otp + "\n\nÐ­Ð½Ñ ÐºÐ¾Ð´ 5 Ð¼Ð¸Ð½ÑƒÑ‚Ñ‹Ð½ Ñ…ÑƒÐ³Ð°Ñ†Ð°Ð°Ð½Ð´ Ñ…Ò¯Ñ‡Ð¸Ð½Ñ‚ÑÐ¹.\n\nÐ¥ÑÑ€ÑÐ² Ñ‚Ð° ÑÐ½Ñ Ñ…Ò¯ÑÑÐ»Ñ‚ Ð¸Ð»Ð³ÑÑÐ³ÑÑÐ³Ò¯Ð¹ Ð±Ð¾Ð» ÑÐ½Ñ Ð¼ÑÐ¹Ð»Ð¸Ð¹Ð³ Ò¯Ð» Ñ‚Ð¾Ð¾Ð¼ÑÐ¾Ñ€Ð»Ð¾Ð½Ð¾ ÑƒÑƒ.\n\nGymBooking Ð±Ð°Ð³");
            message.setFrom("gymbooking@gmail.com");

            mailSender.send(message);
            System.out.println("âœ… Email sent successfully to: " + email);
        } catch (Exception e) {
            System.err.println("âŒ Email send failed: " + e.getMessage());
            throw new RuntimeException("Email send failed: " + e.getMessage());
        }
    }

    // ==================== SEND OTP (SMS - legacy) ====================
    @PostMapping("/send-otp")
    public ResponseEntity<?> sendOtp(@RequestParam String phone) {
        System.out.println("=== SEND OTP (SMS) ===");
        System.out.println("Phone: " + phone);

        if (phone == null || phone.isBlank()) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Ð£Ñ‚Ð°ÑÐ½Ñ‹ Ð´ÑƒÐ³Ð°Ð°Ñ€Ð°Ð° Ð¾Ñ€ÑƒÑƒÐ»Ð½Ð° ÑƒÑƒ");
            return ResponseEntity.badRequest().body(response);
        }

        Random random = new Random();
        String otp = String.format("%06d", random.nextInt(1000000));
        phoneOtpStore.put(phone, otp);

        new Thread(() -> {
            try {
                Thread.sleep(10 * 60 * 1000);
                phoneOtpStore.remove(phone);
                System.out.println("[OTP] Expired for phone: " + phone);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();

        System.out.println("[OTP] Sent to phone: " + phone + " - OTP: " + otp);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Ð‘Ð°Ñ‚Ð°Ð»Ð³Ð°Ð°Ð¶ÑƒÑƒÐ»Ð°Ñ… ÐºÐ¾Ð´ ÑƒÑ‚Ð°Ñ Ñ€ÑƒÑƒ Ð¸Ð»Ð³ÑÑÐ³Ð´Ð»ÑÑ.");
        response.put("otpForDev", otp);

        return ResponseEntity.ok(response);
    }

    // ==================== VERIFY OTP ====================
    @PostMapping("/verify-otp")
    public ResponseEntity<?> verifyOtp(@RequestParam String phone, @RequestParam String otp) {
        System.out.println("=== VERIFY OTP ===");
        System.out.println("Phone: " + phone);
        System.out.println("OTP: " + otp);

        if (phone == null || otp == null) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Ð£Ñ‚Ð°Ñ Ð±Ð¾Ð»Ð¾Ð½ ÐºÐ¾Ð´Ñ‹Ð³ Ð¾Ñ€ÑƒÑƒÐ»Ð½Ð° ÑƒÑƒ");
            return ResponseEntity.badRequest().body(response);
        }

        String savedOtp = phoneOtpStore.get(phone);
        if (savedOtp == null) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "ÐšÐ¾Ð´ Ñ…ÑƒÐ³Ð°Ñ†Ð°Ð° Ð´ÑƒÑƒÑÑÐ°Ð½ ÑÑÐ²ÑÐ» Ð±ÑƒÑ€ÑƒÑƒ Ð±Ð°Ð¹Ð½Ð°. Ð”Ð°Ñ…Ð¸Ð½ Ñ…Ò¯ÑÑÐ»Ñ‚ Ð¸Ð»Ð³ÑÑÐ½Ñ Ò¯Ò¯.");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        if (!savedOtp.equals(otp)) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "ÐšÐ¾Ð´ Ð±ÑƒÑ€ÑƒÑƒ Ð±Ð°Ð¹Ð½Ð°. Ð”Ð°Ñ…Ð¸Ð½ Ð¾Ñ€Ð¾Ð»Ð´Ð¾Ð½Ð¾ ÑƒÑƒ.");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        phoneOtpStore.remove(phone);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Ð‘Ð°Ñ‚Ð°Ð»Ð³Ð°Ð°Ð¶ÑƒÑƒÐ»Ð°Ð»Ñ‚ Ð°Ð¼Ð¶Ð¸Ð»Ñ‚Ñ‚Ð°Ð¹");
        return ResponseEntity.ok(response);
    }

    // ==================== SEND RESET OTP ====================
    @PostMapping("/send-reset-otp")
    public ResponseEntity<?> sendResetOtp(@RequestBody Map<String, String> request) {
        String email = request.get("email");

        System.out.println("=== SEND RESET OTP ===");
        System.out.println("Email: " + email);

        if (email == null || email.isBlank()) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Ð˜Ð¼ÑÐ¹Ð» Ñ…Ð°ÑÐ³Ð°Ð° Ð¾Ñ€ÑƒÑƒÐ»Ð½Ð° ÑƒÑƒ");
            return ResponseEntity.badRequest().body(response);
        }

        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Ð¥ÑÑ€ÑÐ² Ð¸Ð¼ÑÐ¹Ð» Ð±Ò¯Ñ€Ñ‚Ð³ÑÐ»Ñ‚ÑÐ¹ Ð±Ð¾Ð» Ð±Ð°Ñ‚Ð°Ð»Ð³Ð°Ð°Ð¶ÑƒÑƒÐ»Ð°Ñ… ÐºÐ¾Ð´ Ð¸Ð»Ð³ÑÑÐ³Ð´ÑÑÐ½.");
            return ResponseEntity.ok(response);
        }

        Random random = new Random();
        String otp = String.format("%06d", random.nextInt(1000000));
        emailOtpStore.put(email, otp);

        new Thread(() -> {
            try {
                Thread.sleep(10 * 60 * 1000);
                emailOtpStore.remove(email);
                System.out.println("[OTP] Expired for: " + email);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();

        try {
            sendEmailOtp(email, otp, userOpt.get().getFirstName());
            System.out.println("[OTP] Sent to: " + email + " - OTP: " + otp);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Ð‘Ð°Ñ‚Ð°Ð»Ð³Ð°Ð°Ð¶ÑƒÑƒÐ»Ð°Ñ… ÐºÐ¾Ð´ Ð¸Ð¼ÑÐ¹Ð» Ñ…Ð°ÑÐ³ Ñ€ÑƒÑƒ Ð¸Ð»Ð³ÑÑÐ³Ð´Ð»ÑÑ.");
            response.put("otpForDev", otp);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            System.err.println("[OTP] Failed to send: " + e.getMessage());
            emailOtpStore.remove(email);

            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Ð˜Ð¼ÑÐ¹Ð» Ð¸Ð»Ð³ÑÑÑ…ÑÐ´ Ð°Ð»Ð´Ð°Ð° Ð³Ð°Ñ€Ð»Ð°Ð°. Ð”Ð°Ñ…Ð¸Ð½ Ð¾Ñ€Ð¾Ð»Ð´Ð¾Ð½Ð¾ ÑƒÑƒ.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // ==================== VERIFY RESET OTP ====================
    @PostMapping("/verify-reset-otp")
    public ResponseEntity<?> verifyResetOtp(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        String otp = request.get("otp");

        System.out.println("=== VERIFY RESET OTP ===");
        System.out.println("Email: " + email);
        System.out.println("OTP: " + otp);

        if (email == null || otp == null) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Ð˜Ð¼ÑÐ¹Ð» Ð±Ð¾Ð»Ð¾Ð½ ÐºÐ¾Ð´Ñ‹Ð³ Ð¾Ñ€ÑƒÑƒÐ»Ð½Ð° ÑƒÑƒ");
            return ResponseEntity.badRequest().body(response);
        }

        String savedOtp = emailOtpStore.get(email);
        if (savedOtp == null) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "ÐšÐ¾Ð´ Ñ…ÑƒÐ³Ð°Ñ†Ð°Ð° Ð´ÑƒÑƒÑÑÐ°Ð½ ÑÑÐ²ÑÐ» Ð±ÑƒÑ€ÑƒÑƒ Ð±Ð°Ð¹Ð½Ð°. Ð”Ð°Ñ…Ð¸Ð½ Ñ…Ò¯ÑÑÐ»Ñ‚ Ð¸Ð»Ð³ÑÑÐ½Ñ Ò¯Ò¯.");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        if (!savedOtp.equals(otp)) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "ÐšÐ¾Ð´ Ð±ÑƒÑ€ÑƒÑƒ Ð±Ð°Ð¹Ð½Ð°. Ð”Ð°Ñ…Ð¸Ð½ Ð¾Ñ€Ð¾Ð»Ð´Ð¾Ð½Ð¾ ÑƒÑƒ.");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        String resetToken = java.util.UUID.randomUUID().toString();
        emailResetStore.put(resetToken, email);
        emailOtpStore.remove(email);

        System.out.println("[OTP] Verified successfully for: " + email);
        System.out.println("[OTP] Reset token: " + resetToken);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "ÐšÐ¾Ð´ Ð·Ó©Ð² Ð±Ð°Ð¹Ð½Ð°. Ð¨Ð¸Ð½Ñ Ð½ÑƒÑƒÑ† Ò¯Ð³ Ð¾Ñ€ÑƒÑƒÐ»Ð½Ð° ÑƒÑƒ.");
        response.put("resetToken", resetToken);
        return ResponseEntity.ok(response);
    }

    // ==================== RESET PASSWORD ====================
    @PostMapping("/reset-password-with-otp")
    public ResponseEntity<?> resetPasswordWithOtp(@RequestBody Map<String, String> request) {
        String resetToken = request.get("resetToken");
        String newPassword = request.get("newPassword");

        System.out.println("=== RESET PASSWORD WITH OTP ===");
        System.out.println("Reset token: " + resetToken);

        if (resetToken == null || resetToken.isBlank()) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Ð¥Ò¯Ñ‡Ð¸Ð½Ð³Ò¯Ð¹ Ñ…Ò¯ÑÑÐ»Ñ‚");
            return ResponseEntity.badRequest().body(response);
        }

        if (newPassword == null || newPassword.isBlank() || newPassword.length() < 6) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "ÐÑƒÑƒÑ† Ò¯Ð³ Ñ…Ð°Ð¼Ð³Ð¸Ð¹Ð½ Ð±Ð°Ð³Ð°Ð´Ð°Ð° 6 Ñ‚ÑÐ¼Ð´ÑÐ³Ñ‚ Ð±Ð°Ð¹Ñ… Ñ‘ÑÑ‚Ð¾Ð¹");
            return ResponseEntity.badRequest().body(response);
        }

        String email = emailResetStore.get(resetToken);
        if (email == null) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Ð¥Ò¯Ñ‡Ð¸Ð½Ð³Ò¯Ð¹ ÑÑÐ²ÑÐ» Ñ…ÑƒÐ³Ð°Ñ†Ð°Ð° Ð´ÑƒÑƒÑÑÐ°Ð½ Ñ…Ò¯ÑÑÐ»Ñ‚. Ð”Ð°Ñ…Ð¸Ð½ ÐºÐ¾Ð´ Ð°Ð²Ð°Ñ… Ñ…Ò¯ÑÑÐ»Ñ‚ Ð¸Ð»Ð³ÑÑÐ½Ñ Ò¯Ò¯.");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Ð¥ÑÑ€ÑÐ³Ð»ÑÐ³Ñ‡ Ð¾Ð»Ð´ÑÐ¾Ð½Ð³Ò¯Ð¹");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }

        User user = userOpt.get();
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        emailResetStore.remove(resetToken);

        System.out.println("[OTP] Password reset successfully for: " + email);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "ÐÑƒÑƒÑ† Ò¯Ð³ Ð°Ð¼Ð¶Ð¸Ð»Ñ‚Ñ‚Ð°Ð¹ ÑˆÐ¸Ð½ÑÑ‡Ð»ÑÐ³Ð´Ð»ÑÑ. ÐžÐ´Ð¾Ð¾ ÑˆÐ¸Ð½Ñ Ð½ÑƒÑƒÑ† Ò¯Ð³ÑÑÑ€ Ð½ÑÐ²Ñ‚Ñ€ÑÑ… Ð±Ð¾Ð»Ð¾Ð¼Ð¶Ñ‚Ð¾Ð¹.");
        return ResponseEntity.ok(response);
    }

    // ==================== VERIFY RESET TOKEN ====================
    @GetMapping("/verify-reset-token-otp")
    public ResponseEntity<?> verifyResetTokenOtp(@RequestParam String token) {
        if (token == null || token.isBlank()) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Token Ð¾Ñ€ÑƒÑƒÐ»Ð½Ð° ÑƒÑƒ");
            return ResponseEntity.badRequest().body(response);
        }

        String email = emailResetStore.get(token);
        if (email == null) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Token Ñ…Ò¯Ñ‡Ð¸Ð½Ð³Ò¯Ð¹ ÑÑÐ²ÑÐ» Ñ…ÑƒÐ³Ð°Ñ†Ð°Ð° Ð´ÑƒÑƒÑÑÐ°Ð½ Ð±Ð°Ð¹Ð½Ð°");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Token Ñ…Ò¯Ñ‡Ð¸Ð½Ñ‚ÑÐ¹ Ð±Ð°Ð¹Ð½Ð°");
        response.put("email", email);
        return ResponseEntity.ok(response);
    }
}
