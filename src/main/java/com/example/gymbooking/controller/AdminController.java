package com.example.gymbooking.controller;

import com.example.gymbooking.model.Gym;
import com.example.gymbooking.model.Notification;
import com.example.gymbooking.model.Payment;
import com.example.gymbooking.model.User;
import com.example.gymbooking.repository.BookingRepository;
import com.example.gymbooking.repository.GymRepository;
import com.example.gymbooking.repository.PaymentRepository;
import com.example.gymbooking.repository.SlotRepository;
import com.example.gymbooking.repository.UserRepository;
import com.example.gymbooking.service.DatabaseMaintenanceService;
import com.example.gymbooking.service.GymDataService;
import com.example.gymbooking.service.NotificationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping({"/api/admin", "/admin"})
@CrossOrigin(origins = "http://localhost:3000")
public class AdminController {

    private final GymRepository gymRepository;
    private final BookingRepository bookingRepository;
    private final SlotRepository slotRepository;
    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;
    private final GymDataService gymDataService;
    private final NotificationService notificationService;
    private final DatabaseMaintenanceService databaseMaintenanceService;

    public AdminController(GymRepository gymRepository,
                            BookingRepository bookingRepository,
                            SlotRepository slotRepository,
                            UserRepository userRepository,
                            PaymentRepository paymentRepository,
                            GymDataService gymDataService,
                            NotificationService notificationService,
                            DatabaseMaintenanceService databaseMaintenanceService) {
        this.gymRepository = gymRepository;
        this.bookingRepository = bookingRepository;
        this.slotRepository = slotRepository;
        this.userRepository = userRepository;
        this.paymentRepository = paymentRepository;
        this.gymDataService = gymDataService;
        this.notificationService = notificationService;
        this.databaseMaintenanceService = databaseMaintenanceService;
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

            try {
                detail.put("slotsCount", slotRepository.countByGym(gym));
                detail.put("availableSlotsCount", slotRepository.countByGymAndAvailableTrue(gym));
                detail.put("bookingsCount", bookingRepository.countByGym(gym));
                detail.put("pendingBookings", bookingRepository.countByGymAndStatus(gym, "PENDING"));
                detail.put("confirmedBookings", bookingRepository.countByGymAndStatus(gym, "CONFIRMED"));
                detail.put("canceledBookings", bookingRepository.countByGymAndStatus(gym, "CANCELLED"));
            } catch (Exception ex) {
                detail.put("slotsCount", 0L);
                detail.put("availableSlotsCount", 0L);
                detail.put("bookingsCount", 0L);
                detail.put("pendingBookings", 0L);
                detail.put("confirmedBookings", 0L);
                detail.put("canceledBookings", 0L);
                detail.put("statsError", ex.getMessage());
            }

            try {
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
            } catch (Exception ex) {
                detail.put("owner", null);
                detail.put("ownerError", ex.getMessage());
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
    @GetMapping({"/users", "/users/list"})
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
        stats.put("summary", buildSystemUserSummary());
        stats.put("timestamp", LocalDateTime.now());

        return ResponseEntity.ok(stats);
    }

    /**
     * Dashboard card widgets-д шууд ашиглах нэг түвшний summary.
     * Frontend дээр key нэр өөр байгаа тохиолдолд compatibility alias-уудыг давхар буцаана.
     */
    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> getDashboardSummary() {
        Map<String, Object> summary = new HashMap<>(buildSystemUserSummary());

        // Frontend compatibility aliases
        summary.put("totalHalls", summary.get("totalGyms"));
        summary.put("activeOrders", summary.get("activeBookings"));
        summary.put("todayIncome", summary.get("todaysRevenue"));
        summary.put("totalIncome", summary.get("totalRevenue"));

        return ResponseEntity.ok(summary);
    }


    private Map<String, Object> buildSystemUserSummary() {
        LocalDate today = LocalDate.now();
        LocalDateTime startOfToday = today.atStartOfDay();
        LocalDateTime startOfTomorrow = today.plusDays(1).atStartOfDay();

        long totalUsers = userRepository.count();
        long totalGyms = gymRepository.count();
        long activeBookings = bookingRepository.countByStatusIn(List.of("PENDING", "CONFIRMED"));
        long pendingRequests = gymRepository.countByApprovedFalse();

        BigDecimal todaysRevenue = paymentRepository.sumPaidAmountBetween(startOfToday, startOfTomorrow);
        BigDecimal totalRevenue = paymentRepository.sumPaidAmount();
        if (todaysRevenue == null) {
            todaysRevenue = BigDecimal.ZERO;
        }
        if (totalRevenue == null) {
            totalRevenue = BigDecimal.ZERO;
        }

        Map<String, Object> summary = new HashMap<>();
        summary.put("totalUsers", totalUsers);
        summary.put("totalGyms", totalGyms);
        summary.put("activeBookings", activeBookings);
        summary.put("todaysRevenue", todaysRevenue);
        summary.put("pendingRequests", pendingRequests);
        summary.put("totalRevenue", totalRevenue);
        summary.put("generatedAt", LocalDateTime.now());

        return summary;
    }

    // ================== SYSTEM ADMIN PANELS ==================

    /**
     * Системийн админ - хэрэглэгчдийн жагсаалт (зөвхөн USER role).
     */
    @GetMapping("/system/users")
    public ResponseEntity<List<Map<String, Object>>> getSystemUsers() {
        List<Map<String, Object>> users = userRepository.findByRole("USER")
                .stream()
                .map(this::buildUserPanelRow)
                .collect(Collectors.toList());

        return ResponseEntity.ok(users);
    }

    /**
     * Системийн админ - хэрэглэгчийн хэсэгт харуулах нэгтгэсэн мэдээлэл.
     */
    @GetMapping("/system/users/overview")
    public ResponseEntity<Map<String, Object>> getSystemUsersOverview() {
        List<Map<String, Object>> users = userRepository.findByRole("USER")
                .stream()
                .map(this::buildUserPanelRow)
                .collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("users", users);
        response.put("summary", buildSystemUserSummary());

        return ResponseEntity.ok(response);
    }

    /**
     * Системийн админ - заалны админуудын жагсаалт.
     */
    @GetMapping("/system/gym-admins")
    public ResponseEntity<List<Map<String, Object>>> getSystemGymAdmins() {
        List<Map<String, Object>> admins = userRepository.findByRole("GYM_ADMIN")
                .stream()
                .map(user -> {
                    Map<String, Object> row = buildUserPanelRow(user);
                    if (user.getGym() != null) {
                        Map<String, Object> gymInfo = new HashMap<>();
                        gymInfo.put("id", user.getGym().getId());
                        gymInfo.put("name", user.getGym().getName());
                        gymInfo.put("approved", user.getGym().isApproved());
                        gymInfo.put("active", user.getGym().isActive());
                        row.put("gym", gymInfo);
                    } else {
                        row.put("gym", null);
                    }
                    return row;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(admins);
    }


    /**
     * Системийн админ - Gym admin-ийг заалд оноох.
     */
    @PutMapping("/gyms/{gymId}/assign-admin/{userId}")
    public ResponseEntity<Map<String, Object>> assignGymAdminToGym(@PathVariable Long gymId,
                                                                    @PathVariable Long userId) {
        Gym gym = gymRepository.findById(gymId).orElse(null);
        if (gym == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "message", "Gym олдсонгүй."));
        }

        User gymAdmin = userRepository.findById(userId).orElse(null);
        if (gymAdmin == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "message", "Хэрэглэгч олдсонгүй."));
        }

        if (!"GYM_ADMIN".equalsIgnoreCase(gymAdmin.getRole()) && !"ROLE_GYM_ADMIN".equalsIgnoreCase(gymAdmin.getRole())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "Сонгосон хэрэглэгч Gym admin биш байна."));
        }

        gymAdmin.setGym(gym);
        User savedAdmin = userRepository.save(gymAdmin);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Gym admin заалд амжилттай оноогдлоо.",
                "gymId", gym.getId(),
                "gymName", gym.getName(),
                "gymAdminId", savedAdmin.getId(),
                "gymAdminUsername", savedAdmin.getUsername()
        ));
    }

    /**
     * Системийн админ - gym admin хүсэлтийг батлах.
     */
    @PutMapping("/system/gym-admins/{id}/approve")
    public ResponseEntity<Map<String, Object>> approveGymAdmin(@PathVariable Long id) {
        User gymAdmin = userRepository.findById(id).orElse(null);
        if (gymAdmin == null || !"GYM_ADMIN".equalsIgnoreCase(gymAdmin.getRole())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "message", "Gym admin олдсонгүй."));
        }

        gymAdmin.setVerified(true);
        User savedUser = userRepository.save(gymAdmin);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Gym admin амжилттай батлагдлаа.");
        response.put("gymAdmin", buildUserPanelRow(savedUser));
        return ResponseEntity.ok(response);
    }

    /**
     * Системийн админ - gym admin хүсэлтийг устгах.
     */
    @DeleteMapping("/system/gym-admins/{id}")
    public ResponseEntity<Map<String, Object>> deleteGymAdminRequest(@PathVariable Long id) {
        User gymAdmin = userRepository.findById(id).orElse(null);
        if (gymAdmin == null || !"GYM_ADMIN".equalsIgnoreCase(gymAdmin.getRole())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "message", "Gym admin олдсонгүй."));
        }

        String username = gymAdmin.getUsername();
        userRepository.delete(gymAdmin);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Gym admin хүсэлт устгагдлаа.",
                "username", username
        ));
    }

    /**
     * Системийн админ - захиалгын мэдээлэл (optional filter дэмжинэ).
     */
    @GetMapping({"/system/bookings", "/bookings/detailed"})
    public ResponseEntity<List<Map<String, Object>>> getSystemBookings(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) LocalDate fromDate,
            @RequestParam(required = false) LocalDate toDate) {

        List<Map<String, Object>> bookings = bookingRepository.findAll().stream()
                .filter(booking -> status == null || status.equalsIgnoreCase(booking.getStatus()))
                .filter(booking -> fromDate == null ||
                        (booking.getDate() != null && !booking.getDate().isBefore(fromDate)))
                .filter(booking -> toDate == null ||
                        (booking.getDate() != null && !booking.getDate().isAfter(toDate)))
                .map(booking -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", booking.getId());
                    row.put("status", booking.getStatus());
                    row.put("approved", booking.isApproved());
                    row.put("date", booking.getDate());
                    row.put("time", booking.getTime());
                    row.put("totalPrice", booking.getTotalPrice());
                    row.put("createdAt", booking.getCreatedAt());
                    row.put("confirmedAt", booking.getConfirmedAt());
                    if (booking.getUser() != null) {
                        Map<String, Object> userInfo = new HashMap<>();
                        userInfo.put("id", booking.getUser().getId());
                        userInfo.put("username", booking.getUser().getUsername());
                        userInfo.put("fullName", getFullName(booking.getUser()));
                        userInfo.put("email", booking.getUser().getEmail());
                        userInfo.put("phone", booking.getUser().getPhone());
                        row.put("user", userInfo);
                    } else {
                        row.put("user", null);
                    }

                    if (booking.getGym() != null) {
                        Map<String, Object> gymInfo = new HashMap<>();
                        gymInfo.put("id", booking.getGym().getId());
                        gymInfo.put("name", booking.getGym().getName());
                        row.put("gym", gymInfo);
                    } else {
                        row.put("gym", null);
                    }

                    if (booking.getPayment() != null) {
                        Map<String, Object> paymentInfo = new HashMap<>();
                        paymentInfo.put("id", booking.getPayment().getId());
                        paymentInfo.put("status", booking.getPayment().getStatus());
                        paymentInfo.put("method", booking.getPayment().getPaymentMethod());
                        paymentInfo.put("amount", booking.getPayment().getAmount());
                        paymentInfo.put("transactionId", booking.getPayment().getTransactionId());
                        paymentInfo.put("paidAt", booking.getPayment().getPaidAt());
                        row.put("payment", paymentInfo);
                    } else {
                        row.put("payment", null);
                    }
                    return row;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(bookings);
    }

    /**
     * Системийн админ - төлбөрийн нэгдсэн мэдээлэл.
     */
    @GetMapping("/system/payments/summary")
    public ResponseEntity<Map<String, Object>> getPaymentSummary() {
        List<Payment> payments = paymentRepository.findAll();

        BigDecimal totalAmount = payments.stream()
                .map(Payment::getAmount)
                .filter(amount -> amount != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal paidAmount = payments.stream()
                .filter(payment -> "PAID".equalsIgnoreCase(payment.getStatus()))
                .map(Payment::getAmount)
                .filter(amount -> amount != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal pendingAmount = payments.stream()
                .filter(payment -> "PENDING".equalsIgnoreCase(payment.getStatus()))
                .map(Payment::getAmount)
                .filter(amount -> amount != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Long> statusBreakdown = payments.stream()
                .collect(Collectors.groupingBy(
                        payment -> payment.getStatus() == null ? "UNKNOWN" : payment.getStatus().toUpperCase(),
                        Collectors.counting()
                ));

        Map<String, BigDecimal> methodBreakdown = payments.stream()
                .filter(payment -> payment.getPaymentMethod() != null && payment.getAmount() != null)
                .collect(Collectors.groupingBy(
                        payment -> payment.getPaymentMethod().toUpperCase(),
                        Collectors.reducing(BigDecimal.ZERO, Payment::getAmount, BigDecimal::add)
                ));

        Map<String, Object> result = new HashMap<>();
        result.put("totalTransactions", payments.size());
        result.put("totalAmount", totalAmount);
        result.put("paidAmount", paidAmount);
        result.put("pendingAmount", pendingAmount);
        result.put("statusBreakdown", statusBreakdown);
        result.put("methodBreakdown", methodBreakdown);
        result.put("latestPayments", payments.stream()
                .sorted((a, b) -> {
                    LocalDateTime aCreated = a.getCreatedAt();
                    LocalDateTime bCreated = b.getCreatedAt();
                    if (aCreated == null && bCreated == null) {
                        return 0;
                    }
                    if (aCreated == null) {
                        return 1;
                    }
                    if (bCreated == null) {
                        return -1;
                    }
                    return bCreated.compareTo(aCreated);
                })
                .limit(20)
                .map(payment -> {
                    Map<String, Object> paymentInfo = new HashMap<>();
                    paymentInfo.put("id", payment.getId());
                    paymentInfo.put("amount", payment.getAmount());
                    paymentInfo.put("status", payment.getStatus());
                    paymentInfo.put("paymentMethod", payment.getPaymentMethod());
                    paymentInfo.put("transactionId", payment.getTransactionId());
                    paymentInfo.put("paidAt", payment.getPaidAt());
                    paymentInfo.put("createdAt", payment.getCreatedAt());
                    paymentInfo.put("bookingId", payment.getBooking() != null ? payment.getBooking().getId() : null);
                    paymentInfo.put("userId", payment.getUserId());
                    return paymentInfo;
                })
                .collect(Collectors.toList()));
        result.put("generatedAt", LocalDateTime.now());

        return ResponseEntity.ok(result);
    }

    /**
     * Системийн админ - шинэ заалны хүсэлтүүд.
     */
    @GetMapping("/system/gym-requests")
    public ResponseEntity<List<Map<String, Object>>> getGymRequests() {
        List<Map<String, Object>> requests = gymRepository.findByApprovedFalse()
                .stream()
                .map(gym -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", gym.getId());
                    row.put("name", gym.getName());
                    row.put("location", gym.getLocation());
                    row.put("description", gym.getDescription());
                    row.put("phone", gym.getPhone());
                    row.put("requestedAt", gym.getRequestedAt());
                    row.put("createdAt", gym.getCreatedAt());
                    row.put("active", gym.isActive());
                    if (gym.getOwnerUser() != null) {
                        Map<String, Object> ownerInfo = new HashMap<>();
                        ownerInfo.put("id", gym.getOwnerUser().getId());
                        ownerInfo.put("username", gym.getOwnerUser().getUsername());
                        ownerInfo.put("fullName", getFullName(gym.getOwnerUser()));
                        ownerInfo.put("email", gym.getOwnerUser().getEmail());
                        ownerInfo.put("phone", gym.getOwnerUser().getPhone());
                        row.put("owner", ownerInfo);
                    } else {
                        row.put("owner", null);
                    }
                    row.put("slotsCount", slotRepository.countByGym(gym));
                    row.put("bookingsCount", bookingRepository.countByGym(gym));
                    return row;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(requests);
    }

    private Map<String, Object> buildUserPanelRow(User user) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", user.getId());
        row.put("username", user.getUsername());
        row.put("email", user.getEmail());
        row.put("phone", user.getPhone());
        row.put("firstName", user.getFirstName());
        row.put("lastName", user.getLastName());
        row.put("fullName", getFullName(user));
        row.put("role", user.getRole());
        row.put("verified", user.isVerified());
        return row;
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
        List<Map<String, Object>> items = notifications.stream()
                .map(this::buildAdminNotificationItem)
                .toList();

        Map<String, Object> result = new HashMap<>();
        result.put("notifications", items);
        result.put("unreadCount", notificationService.getUnreadNotificationCount(admin.getId()));
        result.put("totalCount", notificationService.getTotalNotificationCount(admin.getId()));
        return ResponseEntity.ok(result);
    }

    /**
     * Системийн админ самбар дээр харуулах action-той мэдэгдлүүд.
     */
    @GetMapping("/system/notifications")
    public ResponseEntity<Map<String, Object>> getSystemActionNotifications() {
        List<Map<String, Object>> notifications = new ArrayList<>();

        List<User> gymAdminRequests = userRepository.findByRole("GYM_ADMIN")
                .stream()
                .filter(user -> !user.isVerified())
                .collect(Collectors.toList());

        for (User gymAdmin : gymAdminRequests) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", "gym-admin-" + gymAdmin.getId());
            item.put("type", "GYM_ADMIN_REQUEST");
            item.put("title", "👤 Шинэ заалны админ хүсэлт");
            item.put("message", String.format("\"%s\" заалны админ хүсэлт гаргасан байна.", gymAdmin.getUsername()));
            item.put("createdAt", null);
            item.put("read", false);
            item.put("data", buildUserPanelRow(gymAdmin));
            item.put("actions", List.of(
                    Map.of(
                            "label", "Батлах",
                            "method", "PUT",
                            "endpoint", "/admin/system/gym-admins/" + gymAdmin.getId() + "/approve"
                    ),
                    Map.of(
                            "label", "Устгах",
                            "method", "DELETE",
                            "endpoint", "/admin/system/gym-admins/" + gymAdmin.getId()
                    )
            ));
            notifications.add(item);
        }

        List<Gym> gymRequests = gymRepository.findByApprovedFalse();
        for (Gym gym : gymRequests) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", "gym-request-" + gym.getId());
            item.put("type", "GYM_REQUEST");
            item.put("title", "📋 Шинэ заалны хүсэлт");
            item.put("message", String.format("\"%s\" заал баталгаажуулалт хүлээж байна.", gym.getName()));
            item.put("createdAt", gym.getRequestedAt() != null ? gym.getRequestedAt() : gym.getCreatedAt());
            item.put("read", false);

            Map<String, Object> gymData = new HashMap<>();
            gymData.put("id", gym.getId());
            gymData.put("name", gym.getName());
            gymData.put("location", gym.getLocation());
            gymData.put("phone", gym.getPhone());
            gymData.put("requestedAt", gym.getRequestedAt());
            gymData.put("createdAt", gym.getCreatedAt());
            item.put("data", gymData);

            item.put("actions", List.of(
                    Map.of(
                            "label", "Батлах",
                            "method", "PUT",
                            "endpoint", "/admin/gyms/" + gym.getId() + "/approve"
                    ),
                    Map.of(
                            "label", "Устгах",
                            "method", "DELETE",
                            "endpoint", "/admin/gyms/" + gym.getId()
                    )
            ));
            notifications.add(item);
        }

        notifications.sort(Comparator.comparing(
                n -> (LocalDateTime) n.get("createdAt"),
                Comparator.nullsLast(Comparator.reverseOrder())
        ));

        Map<String, Object> response = new HashMap<>();
        response.put("notifications", notifications);
        response.put("unreadCount", notifications.size());
        response.put("totalCount", notifications.size());
        return ResponseEntity.ok(response);
    }

    private Map<String, Object> buildAdminNotificationItem(Notification notification) {
        Map<String, Object> item = new HashMap<>();
        item.put("id", notification.getId());
        item.put("userId", notification.getUserId());
        item.put("title", notification.getTitle());
        item.put("message", notification.getMessage());
        item.put("read", notification.isRead());
        item.put("createdAt", notification.getCreatedAt());
        item.put("readAt", notification.getReadAt());
        item.put("type", resolveAdminNotificationType(notification));
        item.put("gymName", extractGymName(notification.getMessage()));
        return item;
    }

    private String resolveAdminNotificationType(Notification notification) {
        String title = notification.getTitle() != null ? notification.getTitle().toLowerCase() : "";
        String message = notification.getMessage() != null ? notification.getMessage().toLowerCase() : "";

        if (title.contains("захиалга") || message.contains("захиалга")) {
            return "BOOKING";
        }

        if (title.contains("сэтгэгдэл") || message.contains("сэтгэгдэл") || message.contains("коммент")) {
            return "COMMENT";
        }

        return "GENERAL";
    }

    private String extractGymName(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }

        Pattern quotedGymName = Pattern.compile("\"([^\"]+)\"");
        Matcher matcher = quotedGymName.matcher(message);
        if (matcher.find()) {
            return matcher.group(1);
        }

        return null;
    }

    private boolean isSystemAdmin(User user) {
        if (user == null) {
            return false;
        }
        String role = user.getRole();
        return "SUPER_ADMIN".equals(role) || "ADMIN".equals(role);
    }

    @PostMapping("/system/sync-gyms")
    public ResponseEntity<?> syncGyms(@AuthenticationPrincipal User admin) {
        if (!isSystemAdmin(admin)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("success", false, "message", "Unauthorized"));
        }

        try {
            List<Gym> synced = gymDataService.syncGymsFromJson();
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Gym list refreshed",
                    "imported", synced.size()
            ));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", "Failed to sync gym data",
                    "error", ex.getMessage()
            ));
        }
    }

    @PostMapping("/system/reset-gym-comments")
    public ResponseEntity<?> resetGymComments(@AuthenticationPrincipal User admin) {
        // Temporarily disabled for testing
        // if (!isSystemAdmin(admin)) {
        //     return ResponseEntity.status(HttpStatus.FORBIDDEN)
        //             .body(Map.of("success", false, "message", "Unauthorized"));
        // }

        try {
            // Drop and recreate gym_comments table
            databaseMaintenanceService.getJdbcTemplate().execute("DROP TABLE IF EXISTS gym_comments");
            
            // Create new table
            String createTableSql = """
                CREATE TABLE gym_comments (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    gym_id BIGINT NOT NULL,
                    user_id BIGINT NOT NULL,
                    comment VARCHAR(1000) NOT NULL,
                    created_at DATETIME NOT NULL,
                    FOREIGN KEY (gym_id) REFERENCES gyms(id),
                    FOREIGN KEY (user_id) REFERENCES users(id),
                    INDEX idx_gym_comments_gym_id (gym_id),
                    INDEX idx_gym_comments_user_id (user_id),
                    INDEX idx_gym_comments_created_at (created_at)
                )
                """;
            databaseMaintenanceService.getJdbcTemplate().execute(createTableSql);
            
            // Insert test comments
            String insertSql = """
                INSERT INTO gym_comments (gym_id, user_id, comment, created_at) VALUES
                (40, 2, 'Excellent gym with great equipment!', NOW()),
                (40, 2, 'Very clean and well-maintained facility.', NOW() - INTERVAL 1 DAY),
                (40, 2, 'Staff is very helpful and professional.', NOW() - INTERVAL 2 DAY)
                """;
            databaseMaintenanceService.getJdbcTemplate().execute(insertSql);
            
            // Fix auto increment
            databaseMaintenanceService.fixAutoIncrementSequences();
            
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Gym comments table reset successfully",
                    "testComments", 3
            ));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", "Failed to reset gym comments",
                    "error", ex.getMessage()
            ));
        }
    }

    @PostMapping("/system/delete-test-gyms")
    public ResponseEntity<?> deleteTestGyms(@AuthenticationPrincipal User admin) {
        // Temporarily disabled for testing
        // if (!isSystemAdmin(admin)) {
        //     return ResponseEntity.status(HttpStatus.FORBIDDEN)
        //             .body(Map.of("success", false, "message", "Unauthorized"));
        // }

        try {
            // Delete specific gyms by name
            String[] gymNames = {
                "Power Gym",
                "FitLife Center", 
                "Iron House Gym",
                "Yoga Studio",
                "CrossFit Box",
                "Spin Studio"
            };
            
            // Build gym names for IN clause
            String gymNamesParam = String.join(",", java.util.Arrays.stream(gymNames).map(n -> "'" + n + "'").collect(java.util.stream.Collectors.toList()));
            
            // Delete all related data in correct order (with error handling)
            try {
                databaseMaintenanceService.getJdbcTemplate().update(
                    "DELETE FROM gym_comments WHERE gym_id IN (SELECT id FROM gyms WHERE name IN (" + gymNamesParam + "))"
                );
            } catch (Exception ex) { System.err.println("Failed to delete gym_comments: " + ex.getMessage()); }
            
            try {
                databaseMaintenanceService.getJdbcTemplate().update(
                    "DELETE FROM bookings WHERE gym_id IN (SELECT id FROM gyms WHERE name IN (" + gymNamesParam + "))"
                );
            } catch (Exception ex) { System.err.println("Failed to delete bookings: " + ex.getMessage()); }
            
            try {
                databaseMaintenanceService.getJdbcTemplate().update(
                    "DELETE FROM slots WHERE gym_id IN (SELECT id FROM gyms WHERE name IN (" + gymNamesParam + "))"
                );
            } catch (Exception ex) { System.err.println("Failed to delete slots: " + ex.getMessage()); }
            
            try {
                databaseMaintenanceService.getJdbcTemplate().update(
                    "DELETE FROM notifications WHERE gym_id IN (SELECT id FROM gyms WHERE name IN (" + gymNamesParam + "))"
                );
            } catch (Exception ex) { System.err.println("Failed to delete notifications: " + ex.getMessage()); }
            
            try {
                databaseMaintenanceService.getJdbcTemplate().update(
                    "UPDATE users SET gym_id = NULL WHERE gym_id IN (SELECT id FROM gyms WHERE name IN (" + gymNamesParam + "))"
                );
            } catch (Exception ex) { System.err.println("Failed to update users: " + ex.getMessage()); }
            
            // Delete the gyms
            int deletedCount = databaseMaintenanceService.getJdbcTemplate().update(
                "DELETE FROM gyms WHERE name IN (" + gymNamesParam + ")"
            );
            
            // Fix auto increment sequences
            databaseMaintenanceService.fixAutoIncrementSequences();
            
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Test gyms deleted successfully",
                    "deletedCount", deletedCount,
                    "deletedGyms", gymNames
            ));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", "Failed to delete test gyms",
                    "error", ex.getMessage()
            ));
        }
    }

    @PostMapping("/system/delete-gym-admins")
    public ResponseEntity<?> deleteGymAdmins(@AuthenticationPrincipal User admin) {
        // Temporarily disabled for testing
        // if (!isSystemAdmin(admin)) {
        //     return ResponseEntity.status(HttpStatus.FORBIDDEN)
        //             .body(Map.of("success", false, "message", "Unauthorized"));
        // }

        try {
            // First update gym owners to null for gym admins (except main admin)
            int updatedGyms = databaseMaintenanceService.getJdbcTemplate().update(
                "UPDATE gyms SET owner_user_id = NULL WHERE owner_user_id IN (SELECT id FROM users WHERE role = 'GYM_ADMIN' AND id != 2)"
            );
            
            // Then delete all GYM_ADMIN users except main admin (id=2)
            int deletedCount = databaseMaintenanceService.getJdbcTemplate().update(
                "DELETE FROM users WHERE role = 'GYM_ADMIN' AND id != 2"
            );
            
            // Fix auto increment sequences
            databaseMaintenanceService.fixAutoIncrementSequences();
            
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Gym admin users deleted successfully",
                    "deletedCount", deletedCount,
                    "updatedGyms", updatedGyms
            ));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", "Failed to delete gym admins",
                    "error", ex.getMessage()
            ));
        }
    }

    @PostMapping("/system/clean-ulaanbaatar-gyms")
    public ResponseEntity<?> deleteUlaanbaatarGyms(@AuthenticationPrincipal User admin) {
        // Temporarily disabled for testing
        // if (!isSystemAdmin(admin)) {
        //     return ResponseEntity.status(HttpStatus.FORBIDDEN)
        //             .body(Map.of("success", false, "message", "Unauthorized"));
        // }

        try {
            // Delete specific Ulaanbaatar gyms by name
            String[] gymNames = {
                "Power Gym",
                "FitLife Center", 
                "Iron House Gym",
                "Yoga Studio",
                "CrossFit Box",
                "Spin Studio"
            };
            
            // Build gym names for IN clause
            String gymNamesParam = String.join(",", java.util.Arrays.stream(gymNames).map(n -> "'" + n + "'").collect(java.util.stream.Collectors.toList()));
            
            // Delete all related data in correct order (with error handling)
            try {
                databaseMaintenanceService.getJdbcTemplate().update(
                    "DELETE FROM gym_comments WHERE gym_id IN (SELECT id FROM gyms WHERE name IN (" + gymNamesParam + "))"
                );
            } catch (Exception ex) { System.err.println("Failed to delete gym_comments: " + ex.getMessage()); }
            
            try {
                databaseMaintenanceService.getJdbcTemplate().update(
                    "DELETE FROM bookings WHERE gym_id IN (SELECT id FROM gyms WHERE name IN (" + gymNamesParam + "))"
                );
            } catch (Exception ex) { System.err.println("Failed to delete bookings: " + ex.getMessage()); }
            
            try {
                databaseMaintenanceService.getJdbcTemplate().update(
                    "DELETE FROM slots WHERE gym_id IN (SELECT id FROM gyms WHERE name IN (" + gymNamesParam + "))"
                );
            } catch (Exception ex) { System.err.println("Failed to delete slots: " + ex.getMessage()); }
            
            try {
                databaseMaintenanceService.getJdbcTemplate().update(
                    "DELETE FROM notifications WHERE gym_id IN (SELECT id FROM gyms WHERE name IN (" + gymNamesParam + "))"
                );
            } catch (Exception ex) { System.err.println("Failed to delete notifications: " + ex.getMessage()); }
            
            try {
                databaseMaintenanceService.getJdbcTemplate().update(
                    "UPDATE users SET gym_id = NULL WHERE gym_id IN (SELECT id FROM gyms WHERE name IN (" + gymNamesParam + "))"
                );
            } catch (Exception ex) { System.err.println("Failed to update users: " + ex.getMessage()); }
            
            // Delete the gyms
            int deletedCount = databaseMaintenanceService.getJdbcTemplate().update(
                "DELETE FROM gyms WHERE name IN (" + gymNamesParam + ")"
            );
            
            // Fix auto increment sequences
            databaseMaintenanceService.fixAutoIncrementSequences();
            
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Ulaanbaatar gyms deleted successfully",
                    "deletedCount", deletedCount,
                    "deletedGyms", gymNames
            ));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", "Failed to delete Ulaanbaatar gyms",
                    "error", ex.getMessage()
            ));
        }
    }

}
