package com.example.gymbooking.controller;

import com.example.gymbooking.config.JwtUtil;
import com.example.gymbooking.model.User;
import com.example.gymbooking.repository.UserRepository;
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

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

@RestController
@RequestMapping({"/auth", "/api/auth"})
@CrossOrigin(origins = "http://localhost:3000")
public class AuthController {

    private final OtpService otpService;
    private final AuthService authService;
    private final AuditLogService auditLogService;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;

    @Autowired
    private JavaMailSender mailSender;

    // OTP хадгалах stores
    private final Map<String, String> emailOtpStore = new HashMap<>();
    private final Map<String, String> emailResetStore = new HashMap<>();
    private final Map<String, String> phoneOtpStore = new HashMap<>();
    private final Map<String, String> registerOtpStore = new HashMap<>();
    private final Map<String, User> tempUserStore = new HashMap<>();

    public AuthController(OtpService otpService, AuthService authService,
                          AuditLogService auditLogService,
                          PasswordEncoder passwordEncoder, UserRepository userRepository,
                          JwtUtil jwtUtil) {
        this.otpService = otpService;
        this.authService = authService;
        this.auditLogService = auditLogService;
        this.passwordEncoder = passwordEncoder;
        this.userRepository = userRepository;
        this.jwtUtil = jwtUtil;
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

    // ==================== LOGIN ====================
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> request) {
        String identifier = request.getOrDefault("identifier", request.get("phone"));
        String password = request.get("password");

        System.out.println("=== LOGIN ATTEMPT ===");
        System.out.println("Identifier: " + identifier);

        if (identifier == null || password == null) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Утасны дугаар болон нууц үгээ оруулна уу");
            return ResponseEntity.badRequest().body(response);
        }

        try {
            Optional<User> userOpt = userRepository.findByUsername(identifier);
            if (userOpt.isEmpty()) {
                System.out.println("User not found with identifier: " + identifier);
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "Утас эсвэл нууц үг буруу байна");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            }

            User user = userOpt.get();

            if (!passwordEncoder.matches(password, user.getPassword())) {
                System.out.println("Invalid password for user: " + identifier);
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "Утас эсвэл нууц үг буруу байна");
return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            }

            String token = jwtUtil.generateToken(user.getUsername());

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
            response.put("message", "Нэвтрэлт амжилттай");
            response.put("token", token);
            response.put("user", userMap);

return ResponseEntity.ok(response);

        } catch (Exception e) {
            System.err.println("Login error: " + e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Системийн алдаа гарлаа");
return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
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
            response.put("message", "Нэр, утас, нууц үг шаардлагатай");
            return ResponseEntity.badRequest().body(response);
        }

        // Check if user already exists by phone
        if (userRepository.findByPhone(phone).isPresent()) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Энэ утасны дугаар аль хэдийн бүртгэлтэй байна");
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }

        // Check if user already exists by email
        if (email != null && !email.isBlank() && userRepository.findByEmail(email).isPresent()) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Энэ имэйл хаяг аль хэдийн бүртгэлтэй байна");
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
            response.put("message", "Бүртгэл амжилттай");
            response.put("token", token);
            response.put("user", userMap);

            System.out.println("✅ User registered successfully (direct): " + phone);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            System.err.println("Registration error: " + e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Бүртгэл үүсгэхэд алдаа гарлаа: " + e.getMessage());
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
            response.put("message", "Нэр, утас, нууц үг шаардлагатай");
            return ResponseEntity.badRequest().body(response);
        }

        if (email == null || email.isBlank()) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Имэйл хаяг шаардлагатай");
            return ResponseEntity.badRequest().body(response);
        }

        // Check if user already exists
        if (userRepository.findByPhone(phone).isPresent()) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Энэ утасны дугаар аль хэдийн бүртгэлтэй байна");
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }

        if (userRepository.findByEmail(email).isPresent()) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Энэ имэйл хаяг аль хэдийн бүртгэлтэй байна");
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
            System.out.println("✅ OTP email sent to: " + email);
        } catch (Exception e) {
            System.err.println("❌ Failed to send OTP email: " + e.getMessage());
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
        response.put("message", "Баталгаажуулах код имэйл хаяг руу илгээгдлээ");
        response.put("email", email);
        response.put("otpForDev", otp); // Development-д зориулж

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
                response.put("message", "Бүртгэл амжилттай баталгаажлаа");
                response.put("token", token);
                response.put("user", userMap);

                return ResponseEntity.ok(response);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Имэйл болон кодыг оруулна уу");
            return ResponseEntity.badRequest().body(response);
        }

        String savedOtp = registerOtpStore.get(email);
        if (savedOtp == null) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Код хугацаа дууссан эсвэл буруу байна. Дахин хүсэлт илгээнэ үү.");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        if (!savedOtp.equals(otp)) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Код буруу байна. Дахин оролдоно уу.");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        User tempUser = tempUserStore.get(email);
        if (tempUser == null) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Хэрэглэгчийн мэдээлэл олдсонгүй. Дахин бүртгүүлнэ үү.");
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
            response.put("message", "Бүртгэл амжилттай баталгаажлаа");
            response.put("token", token);
            response.put("user", userMap);

            System.out.println("✅ User registered successfully: " + tempUser.getPhone());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            System.err.println("Registration completion error: " + e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Бүртгэл дуусгахад алдаа гарлаа: " + e.getMessage());
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
            response.put("message", "Имэйл болон кодыг оруулна уу");
            return ResponseEntity.badRequest().body(response);
        }

        String savedOtp = registerOtpStore.get(email);
        if (savedOtp == null) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Код хугацаа дууссан эсвэл буруу байна. Дахин хүсэлт илгээнэ үү.");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        if (!savedOtp.equals(otp)) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Код буруу байна. Дахин оролдоно уу.");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        User tempUser = tempUserStore.get(email);
        if (tempUser == null) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Хэрэглэгчийн мэдээлэл олдсонгүй. Дахин бүртгүүлнэ үү.");
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
            response.put("message", "Бүртгэл амжилттай баталгаажлаа");
            response.put("token", token);
            response.put("user", userMap);

            System.out.println("✅ User registered successfully: " + tempUser.getPhone());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            System.err.println("Registration error: " + e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Бүртгэл үүсгэхэд алдаа гарлаа");
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
            response.put("message", "Имэйл хаягаа оруулна уу");
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
            sendEmailOtp(email, otp, "Хэрэглэгч");
            System.out.println("✅ OTP email sent to: " + email);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Баталгаажуулах код имэйл хаяг руу илгээгдлээ.");
            response.put("otpForDev", otp);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            System.err.println("❌ Failed to send email: " + e.getMessage());

            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Имэйл илгээхэд алдаа гарлаа. Дахин оролдоно уу.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // ==================== EMAIL SENDER METHOD ====================
    private void sendEmailOtp(String email, String otp, String name) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(email);
            message.setSubject("GymBooking - Баталгаажуулах код");
            message.setText("Сайн уу " + name + ",\n\nТаны баталгаажуулах код: " + otp + "\n\nЭнэ код 5 минутын хугацаанд хүчинтэй.\n\nХэрэв та энэ хүсэлт илгээгээгүй бол энэ мэйлийг үл тоомсорлоно уу.\n\nGymBooking баг");
            message.setFrom("gymbooking@gmail.com");

            mailSender.send(message);
            System.out.println("✅ Email sent successfully to: " + email);
        } catch (Exception e) {
            System.err.println("❌ Email send failed: " + e.getMessage());
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
            response.put("message", "Утасны дугаараа оруулна уу");
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
        response.put("message", "Баталгаажуулах код утас руу илгээгдлээ.");
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
            response.put("message", "Утас болон кодыг оруулна уу");
            return ResponseEntity.badRequest().body(response);
        }

        String savedOtp = phoneOtpStore.get(phone);
        if (savedOtp == null) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Код хугацаа дууссан эсвэл буруу байна. Дахин хүсэлт илгээнэ үү.");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        if (!savedOtp.equals(otp)) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Код буруу байна. Дахин оролдоно уу.");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        phoneOtpStore.remove(phone);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Баталгаажуулалт амжилттай");
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
            response.put("message", "Имэйл хаягаа оруулна уу");
            return ResponseEntity.badRequest().body(response);
        }

        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Хэрэв имэйл бүртгэлтэй бол баталгаажуулах код илгээгдсэн.");
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
            response.put("message", "Баталгаажуулах код имэйл хаяг руу илгээгдлээ.");
            response.put("otpForDev", otp);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            System.err.println("[OTP] Failed to send: " + e.getMessage());
            emailOtpStore.remove(email);

            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Имэйл илгээхэд алдаа гарлаа. Дахин оролдоно уу.");
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
            response.put("message", "Имэйл болон кодыг оруулна уу");
            return ResponseEntity.badRequest().body(response);
        }

        String savedOtp = emailOtpStore.get(email);
        if (savedOtp == null) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Код хугацаа дууссан эсвэл буруу байна. Дахин хүсэлт илгээнэ үү.");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        if (!savedOtp.equals(otp)) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Код буруу байна. Дахин оролдоно уу.");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        String resetToken = java.util.UUID.randomUUID().toString();
        emailResetStore.put(resetToken, email);
        emailOtpStore.remove(email);

        System.out.println("[OTP] Verified successfully for: " + email);
        System.out.println("[OTP] Reset token: " + resetToken);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Код зөв байна. Шинэ нууц үг оруулна уу.");
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
            response.put("message", "Хүчингүй хүсэлт");
            return ResponseEntity.badRequest().body(response);
        }

        if (newPassword == null || newPassword.isBlank() || newPassword.length() < 6) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Нууц үг хамгийн багадаа 6 тэмдэгт байх ёстой");
            return ResponseEntity.badRequest().body(response);
        }

        String email = emailResetStore.get(resetToken);
        if (email == null) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Хүчингүй эсвэл хугацаа дууссан хүсэлт. Дахин код авах хүсэлт илгээнэ үү.");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Хэрэглэгч олдсонгүй");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }

        User user = userOpt.get();
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        emailResetStore.remove(resetToken);

        System.out.println("[OTP] Password reset successfully for: " + email);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Нууц үг амжилттай шинэчлэгдлээ. Одоо шинэ нууц үгээр нэвтрэх боломжтой.");
        return ResponseEntity.ok(response);
    }

    // ==================== VERIFY RESET TOKEN ====================
    @GetMapping("/verify-reset-token-otp")
    public ResponseEntity<?> verifyResetTokenOtp(@RequestParam String token) {
        if (token == null || token.isBlank()) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Token оруулна уу");
            return ResponseEntity.badRequest().body(response);
        }

        String email = emailResetStore.get(token);
        if (email == null) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Token хүчингүй эсвэл хугацаа дууссан байна");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Token хүчинтэй байна");
        response.put("email", email);
        return ResponseEntity.ok(response);
    }
}




