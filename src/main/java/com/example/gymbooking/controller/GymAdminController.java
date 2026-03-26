package com.example.gymbooking.controller;

import com.example.gymbooking.model.Booking;
import com.example.gymbooking.model.Gym;
import com.example.gymbooking.model.Slot;
import com.example.gymbooking.model.User;
import com.example.gymbooking.repository.BookingRepository;
import com.example.gymbooking.repository.GymRepository;
import com.example.gymbooking.repository.SlotRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/gym-admin")
@CrossOrigin(origins = "http://localhost:3000")
public class GymAdminController {

    private final GymRepository gymRepository;
    private final BookingRepository bookingRepository;
    private final SlotRepository slotRepository;

    public GymAdminController(GymRepository gymRepository,
                              BookingRepository bookingRepository,
                              SlotRepository slotRepository) {
        this.gymRepository = gymRepository;
        this.bookingRepository = bookingRepository;
        this.slotRepository = slotRepository;
    }

    // Helper method to get gym by admin
    private Gym getGymByAdmin(User admin) {
        List<Gym> gyms = gymRepository.findByOwnerUser(admin);
        return gyms.isEmpty() ? null : gyms.get(0);
    }

    // ================== GYM INFO ==================

    @GetMapping("/my-gym")
    public ResponseEntity<?> getMyGym(@AuthenticationPrincipal User admin) {
        Gym gym = getGymByAdmin(admin);
        if (gym == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(gym);
    }

    @PutMapping("/my-gym")
    public ResponseEntity<?> updateMyGym(@AuthenticationPrincipal User admin,
                                         @RequestBody Gym updatedGym) {
        Gym gym = getGymByAdmin(admin);
        if (gym == null) {
            return ResponseEntity.notFound().build();
        }

        if (updatedGym.getName() != null) gym.setName(updatedGym.getName());
        if (updatedGym.getLocation() != null) gym.setLocation(updatedGym.getLocation());
        if (updatedGym.getDescription() != null) gym.setDescription(updatedGym.getDescription());
        if (updatedGym.getPhone() != null) gym.setPhone(updatedGym.getPhone());

        return ResponseEntity.ok(gymRepository.save(gym));
    }

    // ================== BOOKINGS ==================

    @GetMapping("/bookings")
    public ResponseEntity<List<Booking>> getAllBookings(@AuthenticationPrincipal User admin) {
        Gym gym = getGymByAdmin(admin);
        if (gym == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(bookingRepository.findByGym(gym));
    }

    @GetMapping("/bookings/today")
    public ResponseEntity<?> getTodaysBookings(@AuthenticationPrincipal User admin) {
        Gym gym = getGymByAdmin(admin);
        if (gym == null) {
            return ResponseEntity.notFound().build();
        }

        List<Booking> bookings = bookingRepository.findTodaysBookingsByGym(gym);
        Map<String, Object> response = new HashMap<>();
        response.put("date", LocalDate.now());
        response.put("bookings", bookings);
        response.put("total", bookings.size());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/bookings/date")
    public ResponseEntity<?> getBookingsByDate(@AuthenticationPrincipal User admin,
                                               @RequestParam String date) {
        Gym gym = getGymByAdmin(admin);
        if (gym == null) {
            return ResponseEntity.notFound().build();
        }

        try {
            LocalDate parsedDate = LocalDate.parse(date);
            List<Booking> bookings = bookingRepository.findByGym_IdAndSlot_Date(gym.getId(), parsedDate);

            Map<String, Object> response = new HashMap<>();
            response.put("date", parsedDate);
            response.put("bookings", bookings);
            response.put("total", bookings.size());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid date format"));
        }
    }

    @GetMapping("/bookings/confirmed")
    public ResponseEntity<List<Booking>> getConfirmedBookings(@AuthenticationPrincipal User admin) {
        Gym gym = getGymByAdmin(admin);
        if (gym == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(bookingRepository.findByGymAndStatus(gym, "CONFIRMED"));
    }

    @GetMapping("/bookings/pending")
    public ResponseEntity<List<Booking>> getPendingBookings(@AuthenticationPrincipal User admin) {
        Gym gym = getGymByAdmin(admin);
        if (gym == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(bookingRepository.findByGymAndStatus(gym, "PENDING"));
    }

    @GetMapping("/bookings/cancelled")
    public ResponseEntity<List<Booking>> getCancelledBookings(@AuthenticationPrincipal User admin) {
        Gym gym = getGymByAdmin(admin);
        if (gym == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(bookingRepository.findByGymAndStatus(gym, "CANCELLED"));
    }

    @GetMapping("/bookings/stats")
    public ResponseEntity<?> getBookingStats(@AuthenticationPrincipal User admin) {
        Gym gym = getGymByAdmin(admin);
        if (gym == null) {
            return ResponseEntity.notFound().build();
        }

        Map<String, Object> stats = new HashMap<>();
        stats.put("total", bookingRepository.countByGym(gym));
        stats.put("confirmed", bookingRepository.countByGymAndStatus(gym, "CONFIRMED"));
        stats.put("pending", bookingRepository.countByGymAndStatus(gym, "PENDING"));
        stats.put("cancelled", bookingRepository.countByGymAndStatus(gym, "CANCELLED"));
        return ResponseEntity.ok(stats);
    }

    // ================== SLOTS ==================

    @GetMapping("/slots")
    public ResponseEntity<List<Slot>> getAllSlots(@AuthenticationPrincipal User admin) {
        Gym gym = getGymByAdmin(admin);
        if (gym == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(slotRepository.findByGym(gym));
    }

    @GetMapping("/slots/date")
    public ResponseEntity<?> getSlotsByDate(@AuthenticationPrincipal User admin,
                                            @RequestParam String date) {
        Gym gym = getGymByAdmin(admin);
        if (gym == null) {
            return ResponseEntity.notFound().build();
        }

        try {
            LocalDate parsedDate = LocalDate.parse(date);
            List<Slot> slots = slotRepository.findByGymAndDate(gym, parsedDate);

            Map<String, Object> response = new HashMap<>();
            response.put("date", parsedDate);
            response.put("slots", slots);
            response.put("total", slots.size());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid date format"));
        }
    }

    @GetMapping("/slots/available")
    public ResponseEntity<List<Slot>> getAvailableSlots(@AuthenticationPrincipal User admin) {
        Gym gym = getGymByAdmin(admin);
        if (gym == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(slotRepository.findByGymAndAvailableTrue(gym));
    }

    @PostMapping("/slots")
    public ResponseEntity<?> createSlot(@AuthenticationPrincipal User admin,
                                        @RequestBody Slot slot) {
        Gym gym = getGymByAdmin(admin);
        if (gym == null) {
            return ResponseEntity.notFound().build();
        }

        slot.setGym(gym);
        slot.setAvailable(true);
        if (slot.getMaxCapacity() == null) slot.setMaxCapacity(1);
        if (slot.getCurrentBookings() == null) slot.setCurrentBookings(0);

        return ResponseEntity.ok(slotRepository.save(slot));
    }

    @PutMapping("/slots/{slotId}")
    public ResponseEntity<?> updateSlot(@AuthenticationPrincipal User admin,
                                        @PathVariable Long slotId,
                                        @RequestBody Slot updatedSlot) {
        Gym gym = getGymByAdmin(admin);
        if (gym == null) {
            return ResponseEntity.notFound().build();
        }

        Slot slot = slotRepository.findById(slotId).orElse(null);
        if (slot == null || !slot.getGym().getId().equals(gym.getId())) {
            return ResponseEntity.notFound().build();
        }

        if (updatedSlot.getDate() != null) slot.setDate(updatedSlot.getDate());
        if (updatedSlot.getTime() != null) slot.setTime(updatedSlot.getTime());
        if (updatedSlot.getPrice() != null) slot.setPrice(updatedSlot.getPrice());
        if (updatedSlot.getMaxCapacity() != null) slot.setMaxCapacity(updatedSlot.getMaxCapacity());

        return ResponseEntity.ok(slotRepository.save(slot));
    }

    @DeleteMapping("/slots/{slotId}")
    public ResponseEntity<?> deleteSlot(@AuthenticationPrincipal User admin,
                                        @PathVariable Long slotId) {
        Gym gym = getGymByAdmin(admin);
        if (gym == null) {
            return ResponseEntity.notFound().build();
        }

        Slot slot = slotRepository.findById(slotId).orElse(null);
        if (slot == null || !slot.getGym().getId().equals(gym.getId())) {
            return ResponseEntity.notFound().build();
        }

        slotRepository.delete(slot);
        return ResponseEntity.ok(Map.of("success", true, "message", "Slot deleted successfully"));
    }

    // ================== DASHBOARD ==================

    @GetMapping("/dashboard")
    public ResponseEntity<?> getDashboard(@AuthenticationPrincipal User admin) {
        Gym gym = getGymByAdmin(admin);
        if (gym == null) {
            return ResponseEntity.notFound().build();
        }

        List<Booking> todaysBookings = bookingRepository.findTodaysBookingsByGym(gym);
        List<Slot> todaysSlots = slotRepository.findByGymAndDate(gym, LocalDate.now());

        Map<String, Object> dashboard = new HashMap<>();
        dashboard.put("gym", gym);
        dashboard.put("todaysBookings", todaysBookings);
        dashboard.put("todaysBookingsCount", todaysBookings.size());
        dashboard.put("todaysSlots", todaysSlots);
        dashboard.put("todaysSlotsCount", todaysSlots.size());
        dashboard.put("stats", Map.of(
                "totalBookings", bookingRepository.countByGym(gym),
                "confirmedBookings", bookingRepository.countByGymAndStatus(gym, "CONFIRMED"),
                "pendingBookings", bookingRepository.countByGymAndStatus(gym, "PENDING"),
                "totalSlots", slotRepository.countByGym(gym),
                "availableSlots", slotRepository.countByGymAndAvailableTrue(gym)
        ));

        return ResponseEntity.ok(dashboard);
    }
}