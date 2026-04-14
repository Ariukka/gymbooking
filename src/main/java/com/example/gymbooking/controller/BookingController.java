package com.example.gymbooking.controller;

import com.example.gymbooking.dto.CreateBookingRequest;
import com.example.gymbooking.model.Gym;
import com.example.gymbooking.model.Booking;
import com.example.gymbooking.model.Slot;
import com.example.gymbooking.model.User;
import com.example.gymbooking.repository.BookingRepository;
import com.example.gymbooking.repository.GymRepository;
import com.example.gymbooking.repository.SlotRepository;
import com.example.gymbooking.service.EmailService;
import com.example.gymbooking.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingRepository bookingRepository;
    private final EmailService emailService;
    private final NotificationService notificationService;
    private final SlotRepository slotRepository;
    private final GymRepository gymRepository;

    public BookingController(BookingRepository bookingRepository,
                             EmailService emailService,
                             NotificationService notificationService,
                             SlotRepository slotRepository,
                             GymRepository gymRepository) {
        this.bookingRepository = bookingRepository;
        this.emailService = emailService;
        this.notificationService = notificationService;
        this.slotRepository = slotRepository;
        this.gymRepository = gymRepository;
    }

    @GetMapping("/how-to-book")
    public ResponseEntity<?> getHowToBookGuide() {
        return ResponseEntity.ok(Map.of(
                "title", "Заал захиалах заавар",
                "steps", List.of(
                        "1. Эхлээд захиалах өдрөө сонгоно.",
                        "2. Дараа нь боломжтой цагаа сонгоно.",
                        "3. Төлбөрөө төлнө.",
                        "4. Баталгаажуулалтын мэдээллээ шалгаад захиалгаа баталгаажуулна."
                )
        ));
    }

    @PostMapping("/check-availability")
    public ResponseEntity<?> checkAvailability(@AuthenticationPrincipal User currentUser,
                                           @RequestBody CreateBookingRequest request) {
        if (currentUser == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }

        String validationError = validateRequest(request);
        if (validationError != null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Invalid request",
                    "message", validationError
            ));
        }

        Slot slot = resolveSlot(request);
        if (slot == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Slot not found",
                    "message", "slotId эсвэл gymId + date + time утга илгээнэ үү."
            ));
        }

        if (isSlotInPast(slot)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Selected slot is closed",
                    "message", "Өнгөрсөн цагийн интервалд захиалга хийх боломжгүй."
            ));
        }

        // Check if slot is available and has capacity
        if (!slot.isAvailable() || !slot.hasCapacity()) {
            return ResponseEntity.ok(Map.of(
                    "available", false,
                    "message", "Энэ цагийн слот аль хэдийн захиалгатай байна. Өөр цаг сонгоно уу."
            ));
        }

        // Check if user already booked this slot
        boolean alreadyBookedByUser = bookingRepository.existsByUser_IdAndSlot_IdAndStatusIn(
                currentUser.getId(), slot.getId(), List.of("PENDING", "CONFIRMED"));
        if (alreadyBookedByUser) {
            return ResponseEntity.ok(Map.of(
                    "available", false,
                    "message", "Та энэ цаг дээр өмнө нь захиалга хийсэн байна."
            ));
        }

        return ResponseEntity.ok(Map.of("available", true));
    }

    @PostMapping
    @Transactional
    public ResponseEntity<?> createBooking(@AuthenticationPrincipal User currentUser,
                                           @RequestBody CreateBookingRequest request) {
        if (currentUser == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }

        String validationError = validateRequest(request);
        if (validationError != null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Invalid request",
                    "message", validationError
            ));
        }

        Slot slot = resolveSlot(request);
        if (slot == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Slot not found",
                    "message", "slotId эсвэл gymId + date + time утга илгээнэ үү."
            ));
        }

        if (!slot.isAvailable() || !slot.hasCapacity()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Selected slot is not available"));
        }

        if (isSlotInPast(slot)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Selected slot is closed",
                    "message", "Өнгөрсөн цагийн интервалд захиалга хийх боломжгүй."
            ));
        }

        boolean alreadyBookedByUser = bookingRepository.existsByUser_IdAndSlot_IdAndStatusIn(
                currentUser.getId(), slot.getId(), List.of("PENDING", "CONFIRMED"));
        if (alreadyBookedByUser) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Duplicate booking",
                    "message", "Та энэ цаг дээр өмнө нь захиалга хийсэн байна."
            ));
        }

        Booking booking = new Booking();
        booking.setUser(currentUser);
        booking.setGym(slot.getGym());
        booking.setSlot(slot);
        booking.setTotalPrice(resolveTotalPrice(request, slot));
        booking.setStatus("CONFIRMED");
        booking.setApproved(true);
        booking.setConfirmedAt(LocalDateTime.now());

        Booking savedBooking = bookingRepository.save(booking);

        slot.incrementBookings();
        slotRepository.save(slot);

        safelySendBookingEmail(savedBooking);
        safelyCreateBookingNotifications(currentUser, savedBooking);

        return ResponseEntity.ok(savedBooking);
    }

    @GetMapping("/my")
    public ResponseEntity<?> getMyBookings(@AuthenticationPrincipal User currentUser) {
        if (currentUser == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }
        return ResponseEntity.ok(bookingRepository.findByUser_IdOrderByCreatedAtDesc(currentUser.getId()));
    }

    @GetMapping("/{date}")
    public ResponseEntity<?> getBookingsByDate(@PathVariable LocalDate date) {
        return ResponseEntity.ok(bookingRepository.findBySlot_Date(date));
    }

    @DeleteMapping("/{id}/cancel")
    @Transactional
    public ResponseEntity<?> cancelBooking(@PathVariable Long id, @AuthenticationPrincipal User currentUser) {
        if (currentUser == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }

        Booking booking = bookingRepository.findById(id).orElse(null);
        if (booking == null) {
            return ResponseEntity.notFound().build();
        }

        // Check if booking belongs to current user
        if (!booking.getUser().getId().equals(currentUser.getId())) {
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        }

        // Check if booking is already cancelled
        if ("CANCELLED".equals(booking.getStatus())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Booking already cancelled"));
        }

        // Calculate time difference for refund
        LocalDateTime bookingDateTime = LocalDateTime.of(
            booking.getSlot().getDate(),
            extractSlotStartTime(booking.getSlot().getTime())
        );
        LocalDateTime now = LocalDateTime.now();
        long hoursDiff = ChronoUnit.HOURS.between(now, bookingDateTime);

        int refundPercentage = 0;
        String message = "";

        if (hoursDiff < 0) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Booking time has passed",
                "message", "Cannot cancel past bookings"
            ));
        } else if (hoursDiff < 1) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Too late to cancel",
                "message", "Cannot cancel within 1 hour of booking time"
            ));
        } else if (hoursDiff < 4) {
            refundPercentage = 10;
            message = "Booking cancelled with 10% refund";
        } else {
            refundPercentage = 100;
            message = "Booking cancelled with full refund";
        }

        // Update booking status
        booking.setStatus("CANCELLED");
        booking.setApproved(false);
        booking.setCancelledAt(LocalDateTime.now());
        bookingRepository.save(booking);

        // Update slot capacity
        Slot slot = booking.getSlot();
        if (slot.getCurrentBookings() > 0) {
            slot.decrementBookings();
            slotRepository.save(slot);
        }

        // Send cancellation notification
        try {
            notificationService.createNotification(
                    currentUser.getId(),
                    "🚫 Захиалга цуцлагдлаа",
                    String.format("Таны %s %s цагийн захиалга цуцлагдлаа. %s%% буцаалт авна.",
                            booking.getSlot().getDate(),
                            booking.getSlot().getTime(),
                            refundPercentage)
            );
        } catch (RuntimeException ignored) {
            // Non-critical failure
        }

        return ResponseEntity.ok(Map.of(
                "message", message,
                "refundPercentage", refundPercentage,
                "bookingId", booking.getId()
        ));
    }

    private String validateRequest(CreateBookingRequest request) {
        if (request == null) {
            return "Request body is required.";
        }
        if (request.getSlotId() != null) {
            return null;
        }
        if (request.getGymId() == null) {
            return "gymId is required.";
        }
        if (request.getDate() == null) {
            return "date is required.";
        }
        if (request.getTime() == null || request.getTime().isBlank()) {
            return "time is required.";
        }
        return null;
    }

    @DeleteMapping("/all")
@Transactional
public ResponseEntity<?> deleteAllBookings(@AuthenticationPrincipal User currentUser) {
    if (currentUser == null) {
        return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
    }
    
    try {
        // Get all bookings for current user
        List<Booking> userBookings = bookingRepository.findByUser_IdOrderByCreatedAtDesc(currentUser.getId());
        
        if (userBookings.isEmpty()) {
            return ResponseEntity.ok(Map.of("message", "No bookings found to delete"));
        }
        
        int deletedCount = 0;
        for (Booking booking : userBookings) {
            // Update slot capacity for non-cancelled bookings
            if (!"CANCELLED".equals(booking.getStatus())) {
                Slot slot = booking.getSlot();
                if (slot != null && slot.getCurrentBookings() > 0) {
                    slot.decrementBookings();
                    slotRepository.save(slot);
                }
            }
            
            // Delete the booking
            bookingRepository.delete(booking);
            deletedCount++;
        }
        
        // Send notification
        try {
            notificationService.createNotification(
                currentUser.getId(),
                "Bvkh zahiagluud ustgagdlaa",
                String.format("Tanii %d zahiagluud amjilttai ustgagdlaa.", deletedCount)
            );
        } catch (RuntimeException ignored) {
        }
        
        return ResponseEntity.ok(Map.of(
            "message", "All bookings deleted successfully",
            "deletedCount", deletedCount
        ));
        
    } catch (Exception e) {
        return ResponseEntity.status(500).body(Map.of(
            "error", "Failed to delete bookings",
            "message", e.getMessage()
        ));
    }
}

private Slot resolveSlot(CreateBookingRequest request) {
        if (request == null) {
            return null;
        }

        String normalizedTime = mergeAndNormalizeTimeRange(request.getTime(), request.getEndTime());

        if (request.getSlotId() != null) {
            return slotRepository.findById(request.getSlotId()).orElse(null);
        }

        if (request.getGymId() == null || request.getDate() == null || normalizedTime == null || normalizedTime.isBlank()) {
            return null;
        }

        return slotRepository.findByGymIdAndDate(request.getGymId(), request.getDate()).stream()
                .filter(slot -> normalizedTime.equals(normalizeTime(slot.getTime())))
                .findFirst()
                .orElseGet(() -> {
            Gym gym = gymRepository.findById(request.getGymId()).orElse(null);
            if (gym == null) {
                return null;
            }

            Slot newSlot = new Slot();
            newSlot.setGym(gym);
            newSlot.setDate(request.getDate());
            newSlot.setTime(normalizedTime);
            newSlot.setPrice(resolveTotalPrice(request, null));
            newSlot.setAvailable(true);
            newSlot.setMaxCapacity(1);
            newSlot.setCurrentBookings(0);

            return slotRepository.save(newSlot);
        });
    }

    private BigDecimal resolveTotalPrice(CreateBookingRequest request, Slot slot) {
        if (request != null && request.getTotalPrice() != null) {
            return request.getTotalPrice();
        }
        if (slot != null && slot.getPrice() != null) {
            return slot.getPrice();
        }
        return BigDecimal.ZERO;
    }

    private void safelySendBookingEmail(Booking booking) {
        try {
            if (booking.getEmail() != null && booking.getDate() != null && booking.getTime() != null) {
                emailService.sendBookingEmail(
                        booking.getEmail(),
                        booking.getDate().toString(),
                        booking.getTime()
                );
            }
        } catch (RuntimeException ignored) {
            // Non-critical failure should not block booking creation.
        }
    }

    private void safelyCreateBookingNotifications(User currentUser, Booking savedBooking) {
        try {
            notificationService.createNotification(
                    currentUser.getId(),
                    "✅ Захиалга баталгаажлаа",
                    String.format("Таны %s %s цагийн захиалга амжилттай баталгаажлаа.",
                            savedBooking.getDate(), savedBooking.getTime())
            );
            notificationService.createGymBookingNotificationForAdmins(savedBooking);
        } catch (RuntimeException ignored) {
            // Non-critical failure should not block booking creation.
        }
    }

    private String normalizeTime(String time) {
        if (time == null) {
            return null;
        }

        String candidate = time.trim();
        if (candidate.isEmpty()) {
            return candidate;
        }

        if (candidate.contains("-")) {
            String[] parts = candidate.split("-", 2);
            String start = normalizeSingleTime(parts[0]);
            String end = normalizeSingleTime(parts[1]);
            if (start != null && end != null) {
                return start + "-" + end;
            }
            return candidate.replaceAll("\\s+", "");
        }

        return normalizeSingleTime(candidate);
    }

    private String mergeAndNormalizeTimeRange(String startTime, String endTime) {
        String normalizedStart = normalizeTime(startTime);
        String normalizedEnd = normalizeTime(endTime);

        if (normalizedStart == null || normalizedStart.isBlank()) {
            return normalizedStart;
        }

        if (normalizedStart.contains("-")) {
            return normalizedStart;
        }

        if (normalizedEnd == null || normalizedEnd.isBlank()) {
            return normalizedStart;
        }

        if (normalizedEnd.contains("-")) {
            normalizedEnd = normalizedEnd.split("-", 2)[0];
        }

        return normalizedStart + "-" + normalizedEnd;
    }

    private String normalizeSingleTime(String candidate) {
        try {
            return LocalTime.parse(candidate, DateTimeFormatter.ofPattern("H:mm"))
                    .format(DateTimeFormatter.ofPattern("HH:mm"));
        } catch (DateTimeParseException ignored) {
        }

        try {
            return LocalTime.parse(candidate, DateTimeFormatter.ofPattern("H:mm:ss"))
                    .format(DateTimeFormatter.ofPattern("HH:mm"));
        } catch (DateTimeParseException ignored) {
        }

        try {
            return LocalTime.parse(candidate, DateTimeFormatter.ISO_LOCAL_TIME)
                    .format(DateTimeFormatter.ofPattern("HH:mm"));
        } catch (DateTimeParseException ignored) {
            return candidate;
        }
    }

    private boolean isSlotInPast(Slot slot) {
        if (slot == null || slot.getDate() == null) {
            return false;
        }

        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
        if (slot.getDate().isBefore(now.toLocalDate())) {
            return true;
        }

        if (!slot.getDate().isEqual(now.toLocalDate())) {
            return false;
        }

        LocalTime startTime = extractSlotStartTime(slot.getTime());
        if (startTime == null) {
            return false;
        }

        return !startTime.isAfter(now.toLocalTime());
    }

    private LocalTime extractSlotStartTime(String slotTime) {
        if (slotTime == null || slotTime.isBlank()) {
            return null;
        }

        String normalized = normalizeTime(slotTime);
        String start = normalized.contains("-") ? normalized.split("-", 2)[0] : normalized;
        try {
            return LocalTime.parse(start, DateTimeFormatter.ofPattern("HH:mm"));
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }
}
