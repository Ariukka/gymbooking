package com.example.gymbooking.controller;

import com.example.gymbooking.model.Gym;
import com.example.gymbooking.model.Notification;
import com.example.gymbooking.model.User;
import com.example.gymbooking.repository.BookingRepository;
import com.example.gymbooking.repository.GymRepository;
import com.example.gymbooking.repository.SlotRepository;
import com.example.gymbooking.repository.UserRepository;
import com.example.gymbooking.service.NotificationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/admin")
@CrossOrigin(origins = "http://localhost:3000")
public class AdminController {

    private final GymRepository gymRepository;
    private final BookingRepository bookingRepository;
    private final SlotRepository slotRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public AdminController(GymRepository gymRepository,
                           BookingRepository bookingRepository,
                           SlotRepository slotRepository,
                           UserRepository userRepository,
                           NotificationService notificationService) {
        this.gymRepository = gymRepository;
        this.bookingRepository = bookingRepository;
        this.slotRepository = slotRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    // ================== GYM MANAGEMENT ==================

    /**
     * Батлагдсан бүх gym-үүдийг авах
     */
    @GetMapping("/gyms/approved")
    public ResponseEntity<List<Gym>> getApprovedGyms() {
        List<Gym> gyms = gymRepository.findByApprovedTrue();
        return ResponseEntity.ok(gyms);
    }

    /**
     * Хүлээгдэж буй (батлагдаагүй) gym-үүдийг авах
     */
    @GetMapping("/gyms/pending")
    public ResponseEntity<List<Gym>> getPendingGyms() {
        List<Gym> gyms = gymRepository.findByApprovedFalse();
        return ResponseEntity.ok(gyms);
    }

    /**
     * Бүх gym-үүдийг шүүлтүүртэйгээр авах
     */
    @GetMapping("/gyms")
    public ResponseEntity<List<Gym>> getAllGyms(@RequestParam(required = false) Boolean approved) {
        List<Gym> gyms;
        if (approved != null) {
            gyms = gymRepository.findByApproved(approved);
        } else {
            gyms = gymRepository.findAll();
        }
        return ResponseEntity.ok(gyms);
    }

    /**
     * Returns detailed gym stats for dashboards.
     */
    @GetMapping("/gyms/detailed")
    public ResponseEntity<List<Map<String, Object>>> getDetailedGyms() {
        List<Gym> gyms = gymRepository.findAll();
        List<Map<String, Object>> details = gyms.stream().map(gym -> {
            Map<String, Object> detail = new HashMap<>();
            detail.put("id", gym.getId());
            detail.put("name", gym.getName());
            detail.put("location", gym.getLocation());
            detail.put("description", gym.getDescription());
            detail.put("phone", gym.getPhone());
            detail.put("approved", gym.isApproved());
            detail.put("active", gym.isActive());
            detail.put("requestedAt", gym.getRequestedAt());
            detail.put("approvedAt", gym.getApprovedAt());
            detail.put("createdAt", gym.getCreatedAt());
            detail.put("slotsCount", slotRepository.countByGym(gym));
            detail.put("availableSlotsCount", slotRepository.countByGymAndAvailableTrue(gym));
            detail.put("bookingsCount", bookingRepository.countByGym(gym));
            detail.put("pendingBookings", bookingRepository.countByGymAndStatus(gym, "PENDING"));
            detail.put("confirmedBookings", bookingRepository.countByGymAndStatus(gym, "CONFIRMED"));
            detail.put("canceledBookings", bookingRepository.countByGymAndStatus(gym, "CANCELLED"));
            if (gym.getOwnerUser() != null) {
                Map<String, Object> ownerInfo = new HashMap<>();
                ownerInfo.put("id", gym.getOwnerUser().getId());
                ownerInfo.put("username", gym.getOwnerUser().getUsername());
                ownerInfo.put("email", gym.getOwnerUser().getEmail());
                ownerInfo.put("fullName", getFullName(gym.getOwnerUser()));
                detail.put("owner", ownerInfo);
            } else {
                detail.put("owner", null);
            }
            return detail;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(details);
    }

    private String getFullName(User user) {
        if (user == null) {
            return null;
        }
        String first = user.getFirstName();
        String last = user.getLastName();
        if (first == null && last == null) {
            return user.getUsername();
        }
        if (first == null) {
            return last;
        }
        if (last == null) {
            return first;
        }
        return first + " " + last;
    }
    /**
     * Тодорхой gym-ийн мэдээллийг авах
     */
    @GetMapping("/gyms/{id}")
    public ResponseEntity<Gym> getGymById(@PathVariable Long id) {
        return gymRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Gym-ийг батлах
     */
    @PutMapping("/gyms/{id}/approve")
    public ResponseEntity<?> approveGym(@PathVariable Long id,
                                        @AuthenticationPrincipal User admin) {
        Gym gym = gymRepository.findById(id).orElse(null);

        if (gym == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "message", "Gym олдсонгүй"));
        }

        // Хэрэв аль хэдийн батлагдсан бол
        if (gym.isApproved()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "Gym аль хэдийн батлагдсан"));
        }

        gym.setApproved(true);
        gym.setActive(true);
        gym.setApprovedAt(LocalDateTime.now());
        Gym savedGym = gymRepository.save(gym);

        // Gym эзэмшигчид мэдэгдэл илгээх
        if (gym.getOwnerUser() != null) {
            notificationService.createGymApprovedNotification(gym.getOwnerUser(), savedGym);
        } else {
            System.err.println("Warning: Gym " + gym.getId() + " has no owner user");
        }

        // Лог хадгалах
        if (admin != null) {
            System.out.println("Admin " + admin.getUsername() + " approved gym: " + gym.getName());
        }

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Gym амжилттай батлагдлаа");
        response.put("gym", savedGym);

        return ResponseEntity.ok(response);
    }

    /**
     * Gym-ийг татгалзах (шалтгаантай)
     */
    @PutMapping("/gyms/{id}/reject")
    public ResponseEntity<?> rejectGym(@PathVariable Long id,
                                       @RequestBody(required = false) Map<String, String> request,
                                       @AuthenticationPrincipal User admin) {
        Gym gym = gymRepository.findById(id).orElse(null);

        if (gym == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "message", "Gym олдсонгүй"));
        }

        // Хэрэв аль хэдийн батлагдсан бол татгалзах боломжгүй
        if (gym.isApproved()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "Аль хэдийн батлагдсан gym-ийг татгалзах боломжгүй"));
        }

        String reason = request != null ? request.get("reason") : null;

        gym.setApproved(false);
        gym.setActive(false);
        Gym savedGym = gymRepository.save(gym);

        // Gym эзэмшигчид шалтгаантай мэдэгдэл илгээх
        if (gym.getOwnerUser() != null) {
            notificationService.createGymRejectedNotification(gym.getOwnerUser(), savedGym, reason);
        } else {
            System.err.println("Warning: Gym " + gym.getId() + " has no owner user");
        }

        // Лог хадгалах
        if (admin != null) {
            System.out.println("Admin " + admin.getUsername() + " rejected gym: " + gym.getName() +
                    (reason != null ? " Reason: " + reason : ""));
        }

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Gym амжилттай татгалзлаа");
        response.put("gym", savedGym);
        response.put("reason", reason);

        return ResponseEntity.ok(response);
    }

    /**
     * Gym-ийг түр хаах (идэвхгүй болгох)
     */
    @PutMapping("/gyms/{id}/suspend")
    public ResponseEntity<?> suspendGym(@PathVariable Long id,
                                        @AuthenticationPrincipal User admin) {
        Gym gym = gymRepository.findById(id).orElse(null);

        if (gym == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "message", "Gym олдсонгүй"));
        }

        if (!gym.isActive()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "Gym аль хэдийн хаагдсан"));
        }

        gym.setActive(false);
        Gym savedGym = gymRepository.save(gym);

        // Лог хадгалах
        if (admin != null) {
            System.out.println("Admin " + admin.getUsername() + " suspended gym: " + gym.getName());
        }

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Gym амжилттай хаагдлаа");
        response.put("gym", savedGym);

        return ResponseEntity.ok(response);
    }

    /**
     * Gym-ийг дахин идэвхжүүлэх
     */
    @PutMapping("/gyms/{id}/activate")
    public ResponseEntity<?> activateGym(@PathVariable Long id,
                                         @AuthenticationPrincipal User admin) {
        Gym gym = gymRepository.findById(id).orElse(null);

        if (gym == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "message", "Gym олдсонгүй"));
        }

        if (gym.isActive()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "Gym аль хэдийн идэвхтэй"));
        }

        if (!gym.isApproved()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "Батлагдаагүй gym-ийг идэвхжүүлэх боломжгүй"));
        }

        gym.setActive(true);
        Gym savedGym = gymRepository.save(gym);

        // Лог хадгалах
        if (admin != null) {
            System.out.println("Admin " + admin.getUsername() + " activated gym: " + gym.getName());
        }

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Gym амжилттай идэвхжлээ");
        response.put("gym", savedGym);

        return ResponseEntity.ok(response);
    }

    /**
     * Gym-ийг бүрмөсөн устгах
     */
    @DeleteMapping("/gyms/{id}")
    public ResponseEntity<?> deleteGym(@PathVariable Long id,
                                       @AuthenticationPrincipal User admin) {
        Gym gym = gymRepository.findById(id).orElse(null);

        if (gym == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "message", "Gym олдсонгүй"));
        }

        String gymName = gym.getName();
        gymRepository.delete(gym);

        // Лог хадгалах
        if (admin != null) {
            System.out.println("Admin " + admin.getUsername() + " deleted gym: " + gymName);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Gym амжилттай устгагдлаа");

        return ResponseEntity.ok(response);
    }

    // ================== USER MANAGEMENT ==================

    /**
     * Бүх хэрэглэгчдийг авах
     */
    @GetMapping("/users")
    public ResponseEntity<List<User>> getAllUsers() {
        List<User> users = userRepository.findAll();
        return ResponseEntity.ok(users);
    }

    /**
     * Тодорхой хэрэглэгчийн мэдээллийг авах
     */
    @GetMapping("/users/{id}")
    public ResponseEntity<User> getUserById(@PathVariable Long id) {
        return userRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Хэрэглэгчийн эрхийг өөрчлөх
     */
    @PutMapping("/users/{id}/role")
    public ResponseEntity<?> updateUserRole(@PathVariable Long id,
                                            @RequestBody Map<String, String> request,
                                            @AuthenticationPrincipal User admin) {
        User user = userRepository.findById(id).orElse(null);

        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "message", "Хэрэглэгч олдсонгүй"));
        }

        String role = request.get("role");
        if (role == null || role.trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "Role хоосон байж болохгүй"));
        }

        // Зөвшөөрөгдсөн role-уудыг шалгах
        List<String> allowedRoles = List.of("ADMIN", "GYM_ADMIN", "USER", "SUPER_ADMIN");
        if (!allowedRoles.contains(role.toUpperCase())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "Буруу role утга"));
        }

        String oldRole = user.getRole();
        user.setRole(role.toUpperCase());
        userRepository.save(user);

        // Лог хадгалах
        if (admin != null) {
            System.out.println("Admin " + admin.getUsername() + " changed user " + user.getUsername() +
                    " role from " + oldRole + " to " + role);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Хэрэглэгчийн эрх амжилттай өөрчлөгдлөө");
        response.put("user", user);

        return ResponseEntity.ok(response);
    }

    /**
     * Хэрэглэгчийг устгах
     */
    /**
     * Хэрэглэгчийг устгах
     * ADMIN: бусдыг устгах боломжтой
     * Хэрэглэгч: өөрийгөө устгах боломжтой
     */
    @DeleteMapping("/users/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable Long id,
                                        @AuthenticationPrincipal User currentUser) {
        User userToDelete = userRepository.findById(id).orElse(null);

        if (userToDelete == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "message", "Хэрэглэгч олдсонгүй"));
        }

        // Эрх шалгах: ADMIN эсвэл өөрийгөө устгаж байгаа эсэх
        boolean isAdmin = currentUser.getRole() != null &&
                (currentUser.getRole().equals("ADMIN") ||
                        currentUser.getRole().equals("SUPER_ADMIN"));
        boolean isSelfDeletion = currentUser.getId().equals(id);

        if (!isAdmin && !isSelfDeletion) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("success", false, "message", "Та зөвхөн өөрийн account-оо устгах боломжтой"));
        }

        // SUPER_ADMIN-ийг устгахыг хориглох (системд хамгийн дээд эрхтэй хэрэглэгч)
        if ("SUPER_ADMIN".equals(userToDelete.getRole()) && !currentUser.getRole().equals("SUPER_ADMIN")) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "SUPER_ADMIN-ийг устгах боломжгүй"));
        }

        // Хамгийн сүүлийн ADMIN-ийг устгахыг хориглох
        if ("ADMIN".equals(userToDelete.getRole())) {
            long adminCount = userRepository.countByRole("ADMIN");
            if (adminCount <= 1 && !isSelfDeletion) {
                return ResponseEntity.badRequest()
                        .body(Map.of("success", false, "message", "Системд хамгийн сүүлийн ADMIN-ийг устгах боломжгүй"));
            }
        }

        String username = userToDelete.getUsername();
        userRepository.delete(userToDelete);

        // Лог хадгалах
        if (isSelfDeletion) {
            System.out.println("User " + currentUser.getUsername() + " deleted their own account");
        } else {
            System.out.println("Admin " + currentUser.getUsername() + " deleted user: " + username);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", isSelfDeletion ? "Таны account амжилттай устгагдлаа" : "Хэрэглэгч амжилттай устгагдлаа");

        return ResponseEntity.ok(response);
    }

    // ================== DASHBOARD STATISTICS ==================

    /**
     * Dashboard-д зориулсан статистик мэдээлэл авах
     */
    @GetMapping("/stats")
    public ResponseEntity<?> getDashboardStats() {
        long totalGyms = gymRepository.count();
        long approvedGyms = gymRepository.countByApprovedTrue();
        long pendingGyms = gymRepository.countByApprovedFalse();
        long totalUsers = userRepository.count();
        long adminUsers = userRepository.countByRole("ADMIN");
        long gymAdminUsers = userRepository.countByRole("GYM_ADMIN");
        long regularUsers = userRepository.countByRole("USER");
        long superAdminUsers = userRepository.countByRole("SUPER_ADMIN");

        Map<String, Object> gymStats = new HashMap<>();
        gymStats.put("total", totalGyms);
        gymStats.put("approved", approvedGyms);
        gymStats.put("pending", pendingGyms);

        Map<String, Object> userStats = new HashMap<>();
        userStats.put("total", totalUsers);
        userStats.put("admin", adminUsers);
        userStats.put("gymAdmin", gymAdminUsers);
        userStats.put("regular", regularUsers);
        userStats.put("superAdmin", superAdminUsers);

        Map<String, Object> stats = new HashMap<>();
        stats.put("gyms", gymStats);
        stats.put("users", userStats);
        stats.put("timestamp", LocalDateTime.now());

        return ResponseEntity.ok(stats);
    }

    /**
     * Admin notifications feed for dashboard panels.
     */
    @GetMapping("/notifications")
    public ResponseEntity<Map<String, Object>> getAdminNotifications(@AuthenticationPrincipal User admin) {
        if (admin == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        List<Notification> notifications = notificationService.getMyNotifications(admin);
        Map<String, Object> result = new HashMap<>();
        result.put("notifications", notifications);
        result.put("unreadCount", notificationService.getUnreadNotificationCount(admin.getId()));
        result.put("totalCount", notificationService.getTotalNotificationCount(admin.getId()));
        return ResponseEntity.ok(result);
    }

}
