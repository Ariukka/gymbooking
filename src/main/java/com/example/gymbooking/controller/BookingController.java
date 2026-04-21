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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingRepository bookingRepository;
    private final EmailService emailService;
    private final NotificationService notificationService;
    private final SlotRepository slotRepository;
    private final GymRepository gymRepository;
    private final boolean paymentEnabled;

    public BookingController(BookingRepository bookingRepository,
                             EmailService emailService,
                             NotificationService notificationService,
                             SlotRepository slotRepository,
                             GymRepository gymRepository,
                             @Value("${app.payment.enabled:false}") boolean paymentEnabled) {
        this.bookingRepository = bookingRepository;
        this.emailService = emailService;
        this.notificationService = notificationService;
        this.slotRepository = slotRepository;
        this.gymRepository = gymRepository;
        this.paymentEnabled = paymentEnabled;
        System.out.println("=== PAYMENT CONFIG ===");
        System.out.println("paymentEnabled: " + paymentEnabled);
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

    @GetMapping("/stream")
    public SseEmitter streamBookingUpdates(@RequestParam Long gymId) {
        SseEmitter emitter = new SseEmitter(60_000L); // 60 seconds timeout
        try {
            // Send initial data
            emitter.send(SseEmitter.event()
                    .name("booking-update")
                    .data(Map.of(
                            "gymId", gymId,
                            "bookedHoursByDate", getBookedHoursByDate(gymId),
                            "timestamp", LocalDateTime.now().toString()
                    )));
            
            // Don't complete immediately - keep connection alive for real-time updates
            // The emitter will be completed when the client disconnects or timeout occurs
            
        } catch (Exception ex) {
            emitter.completeWithError(ex);
        }
        return emitter;
    }

    @PostMapping("/check-availability")
    public ResponseEntity<?> checkAvailability(@AuthenticationPrincipal User currentUser,
                                           @RequestBody CreateBookingRequest request) {
        System.out.println("=== CHECK AVAILABILITY REQUEST ===");
        System.out.println("Request body: " + request);
        System.out.println("User: " + (currentUser != null ? currentUser.getUsername() : "null"));
        
        // Detailed request field logging
        System.out.println("=== REQUEST FIELDS ===");
        System.out.println("gymId: " + request.getGymId() + " (type: " + (request.getGymId() != null ? request.getGymId().getClass().getSimpleName() : "null") + ")");
        System.out.println("date: " + request.getDate() + " (type: " + (request.getDate() != null ? request.getDate().getClass().getSimpleName() : "null") + ")");
        System.out.println("time: " + request.getTime() + " (type: " + (request.getTime() != null ? request.getTime().getClass().getSimpleName() : "null") + ")");
        System.out.println("slotId: " + request.getSlotId());
        
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

        // Handle multiple time slots
        String timeString = request.getTime();
        if (timeString != null && timeString.contains(",")) {
            String[] timeSlots = timeString.split(",");
            for (String timeSlot : timeSlots) {
                CreateBookingRequest singleSlotRequest = new CreateBookingRequest();
                singleSlotRequest.setGymId(request.getGymId());
                singleSlotRequest.setDate(request.getDate());
                singleSlotRequest.setTime(timeSlot.trim());
                
                Slot slot = resolveSlot(singleSlotRequest);
                if (slot == null) {
                    return ResponseEntity.badRequest().body(Map.of(
                            "error", "Slot not found",
                            "message", "slotId esvel gymId + date + time talbaruud shaardlagatai"
                    ));
                }

                if (isSlotInPast(slot)) {
                    return ResponseEntity.badRequest().body(Map.of(
                            "error", "Selected slot is closed",
                            "message", "Ungursun tsagiin interwald zahiulga hiikh bolomjgui"
                    ));
                }

                // Check if slot is available and has capacity
                if (!slot.isAvailable() || !slot.hasCapacity()) {
                    return ResponseEntity.ok(Map.of(
                            "available", false,
                            "message", "Time slot " + timeSlot.trim() + " zahiagdlaa. Busad tsag songono uu."
                    ));
                }

                // Check if user already booked this slot
                boolean alreadyBookedByUser = bookingRepository.existsByUser_IdAndSlot_IdAndStatusIn(
                        currentUser.getId(), slot.getId(), List.of("PENDING", "CONFIRMED"));
                if (alreadyBookedByUser) {
                    return ResponseEntity.ok(Map.of(
                            "available", false,
                            "message", "Ta ene tsagiin slotiig omno n zahiulsan baina."
                    ));
                }
            }
            return ResponseEntity.ok(Map.of("available", true));
        }

        // Handle single time slot (original logic)
        Slot slot = resolveSlot(request);
        if (slot == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Slot not found",
                    "message", "slotId esvel gymId + date + time talbaruud shaardlagatai"
            ));
        }

        if (isSlotInPast(slot)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Selected slot is closed",
                    "message", "Ungursun tsagiin interwald zahiulga hiikh bolomjgui"
            ));
        }

        // Check if slot is available and has capacity
        if (!slot.isAvailable() || !slot.hasCapacity()) {
            return ResponseEntity.ok(Map.of(
                    "available", false,
                    "message", "Ene tsagiin slot zahiagdlaa. Busad tsag songono uu."
            ));
        }

        // Check if user already booked this slot
        boolean alreadyBookedByUser = bookingRepository.existsByUser_IdAndSlot_IdAndStatusIn(
                currentUser.getId(), slot.getId(), List.of("PENDING", "CONFIRMED"));
        if (alreadyBookedByUser) {
            return ResponseEntity.ok(Map.of(
                    "available", false,
                    "message", "Ta ene tsagiin slotiig omno n zahiulsan baina."
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

        // Handle multiple time slots
        String timeString = request.getTime();
        if (timeString != null && timeString.contains(",")) {
            String[] timeSlots = timeString.split(",");
            List<Booking> createdBookings = new ArrayList<>();
            
            for (String timeSlot : timeSlots) {
                CreateBookingRequest singleSlotRequest = new CreateBookingRequest();
                singleSlotRequest.setGymId(request.getGymId());
                singleSlotRequest.setDate(request.getDate());
                singleSlotRequest.setTime(timeSlot.trim());
                
                // Validate each slot
                String singleValidationError = validateRequest(singleSlotRequest);
                if (singleValidationError != null) {
                    return ResponseEntity.badRequest().body(Map.of(
                            "error", "Invalid request",
                            "message", "Time slot " + timeSlot.trim() + ": " + singleValidationError
                    ));
                }
                
                Slot slot = resolveSlot(singleSlotRequest);
                if (slot == null) {
                    return ResponseEntity.badRequest().body(Map.of(
                            "error", "Slot not found",
                            "message", "Time slot " + timeSlot.trim() + ": slotId or gymId + date + time required"
                    ));
                }

                if (!slot.isAvailable() || !slot.hasCapacity()) {
                    return ResponseEntity.badRequest().body(Map.of("error", "Time slot " + timeSlot.trim() + " is not available"));
                }

                if (isSlotInPast(slot)) {
                    return ResponseEntity.badRequest().body(Map.of(
                            "error", "Selected slot is closed",
                            "message", "Time slot " + timeSlot.trim() + ": Ungursun tsagiin interwald zahiulga hiikh bolomjgui"
                    ));
                }

                boolean alreadyBookedByUser = bookingRepository.existsByUser_IdAndSlot_IdAndStatusIn(
                        currentUser.getId(), slot.getId(), List.of("PENDING", "CONFIRMED"));
                if (alreadyBookedByUser) {
                    return ResponseEntity.badRequest().body(Map.of(
                            "error", "Duplicate booking",
                            "message", "Time slot " + timeSlot.trim() + ": Ta ene tsagiin slotiig omno n zahiulsan baina."
                    ));
                }

                // Create booking for this slot
                Booking booking = new Booking();
                booking.setUser(currentUser);
                booking.setGym(slot.getGym());
                booking.setSlot(slot);
                // Ensure date is set from slot if request date is null
                LocalDate bookingDate = singleSlotRequest.getDate();
                if (bookingDate == null) {
                    bookingDate = slot.getDate();
                }
                if (bookingDate == null) {
                    bookingDate = LocalDate.now();
                }
                booking.setDate(bookingDate);
                booking.setTotalPrice(resolveTotalPrice(singleSlotRequest, slot));
                
                // Check if payment is enabled
                if (!paymentEnabled) {
                    booking.setStatus("CONFIRMED");
                    booking.setApproved(true);
                    booking.setConfirmedAt(LocalDateTime.now());
                    System.out.println("Payment disabled - Auto-confirming booking: " + booking.getId());
                } else {
                    booking.setStatus("PENDING_PAYMENT");
                    booking.setApproved(false);
                    System.out.println("Payment enabled - Setting booking to PENDING_PAYMENT: " + booking.getId());
                }

                Booking savedBooking = bookingRepository.save(booking);
                slot.incrementBookings();
                slotRepository.save(slot);

                safelySendBookingEmail(savedBooking);
                safelyCreateBookingNotifications(currentUser, savedBooking);
                
                createdBookings.add(savedBooking);
            }
            
            return ResponseEntity.ok(Map.of(
                "message", "Multiple bookings created successfully",
                "bookings", createdBookings,
                "count", createdBookings.size()
            ));
        }

        // Handle single time slot (original logic)
        Slot slot = resolveSlot(request);
        if (slot == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Slot not found",
                    "message", "slotId or gymId + date + time required"
            ));
        }

        if (!slot.isAvailable() || !slot.hasCapacity()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Selected slot is not available"));
        }

        if (isSlotInPast(slot)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Selected slot is closed",
                    "message", "Ungursun tsagiin interwald zahiulga hiikh bolomjgui"
            ));
        }

        boolean alreadyBookedByUser = bookingRepository.existsByUser_IdAndSlot_IdAndStatusIn(
                currentUser.getId(), slot.getId(), List.of("PENDING", "CONFIRMED"));
        if (alreadyBookedByUser) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Duplicate booking",
                    "message", "Ta ene tsagiin slotiig omno n zahiulsan baina."
            ));
        }

        Booking booking = new Booking();
        booking.setUser(currentUser);
        booking.setGym(slot.getGym());
        booking.setSlot(slot);
        // Ensure date is set from slot if request date is null
        LocalDate bookingDate = request.getDate();
        if (bookingDate == null) {
            bookingDate = slot.getDate();
        }
        if (bookingDate == null) {
            bookingDate = LocalDate.now();
        }
        booking.setDate(bookingDate);
        booking.setTotalPrice(resolveTotalPrice(request, slot));
        
        // Check if payment is enabled
        if (!paymentEnabled) {
            booking.setStatus("CONFIRMED");
            booking.setApproved(true);
            booking.setConfirmedAt(LocalDateTime.now());
            System.out.println("Payment disabled - Auto-confirming booking: " + booking.getId());
        } else {
            booking.setStatus("PENDING_PAYMENT");
            booking.setApproved(false);
            System.out.println("Payment enabled - Setting booking to PENDING_PAYMENT: " + booking.getId());
        }

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
            System.err.println("Validation Error: Request body is null");
            return "Request body is required.";
        }
        
        System.out.println("=== DETAILED VALIDATION ===");
        System.out.println("  slotId: " + request.getSlotId());
        System.out.println("  gymId: " + request.getGymId() + " (type: " + (request.getGymId() != null ? request.getGymId().getClass().getSimpleName() : "null") + ")");
        System.out.println("  date: " + request.getDate() + " (type: " + (request.getDate() != null ? request.getDate().getClass().getSimpleName() : "null") + ")");
        System.out.println("  time: '" + request.getTime() + "' (length: " + (request.getTime() != null ? request.getTime().length() : "null") + ")");
        
        // Option 1: slotId-er zahiulga
        if (request.getSlotId() != null) {
            System.out.println("Validation: Using slotId = " + request.getSlotId());
            return null; // VALIDATION SUCCESS
        }
        
        // Option 2: gymId + date + time-er zahiulga
        if (request.getGymId() == null) {
            System.err.println("Validation Error: gymId is null");
            return "Gym ID is required.";
        }
        if (request.getDate() == null) {
            System.err.println("Validation Error: date is null");
            return "Date is required (format: yyyy-MM-dd).";
        }
        if (request.getTime() == null || request.getTime().trim().isEmpty()) {
            System.err.println("Validation Error: time is null or blank");
            return "Time is required.";
        }
        
        // Validate time format
        String time = request.getTime().trim();
        if (!time.equals("FULL_DAY") && !time.matches("^\\d{2}:\\d{2}(-\\d{2}:\\d{2})?(,\\d{2}:\\d{2}(-\\d{2}:\\d{2})?)*$")) {
            System.err.println("Validation Error: Invalid time format: " + time);
            return "Invalid time format. Use HH:MM or HH:MM-HH:MM format.";
        }
        
        System.out.println("=== VALIDATION PASSED ===");
        return null; // VALIDATION SUCCESS
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
            System.err.println("DEBUG: resolveSlot - request is null");
            return null;
        }

        String normalizedTime = mergeAndNormalizeTimeRange(request.getTime(), request.getEndTime());
        System.out.println("=== DEBUG RESOLVE SLOT ===");
        System.out.println("Request gymId: " + request.getGymId());
        System.out.println("Request date: " + request.getDate());
        System.out.println("Request time: " + request.getTime());
        System.out.println("Normalized time: " + normalizedTime);

        if (request.getSlotId() != null) {
            System.out.println("Using slotId: " + request.getSlotId());
            return slotRepository.findById(request.getSlotId()).orElse(null);
        }

        if (request.getGymId() == null || request.getDate() == null || normalizedTime == null || normalizedTime.isBlank()) {
            System.err.println("DEBUG: Missing required fields");
            return null;
        }

        System.out.println("Searching for existing slots...");
        List<Slot> existingSlots = slotRepository.findByGymIdAndDate(request.getGymId(), request.getDate());
        System.out.println("Found " + existingSlots.size() + " existing slots for gym " + request.getGymId() + " on " + request.getDate());
        
        for (Slot slot : existingSlots) {
            String slotNormalizedTime = normalizeTime(slot.getTime());
            System.out.println("  Slot ID: " + slot.getId() + ", Time: '" + slot.getTime() + "' -> Normalized: '" + slotNormalizedTime + "'");
            System.out.println("  Match check: '" + normalizedTime + "' equals '" + slotNormalizedTime + "' = " + normalizedTime.equals(slotNormalizedTime));
        }

        return existingSlots.stream()
                .filter(slot -> normalizedTime.equals(normalizeTime(slot.getTime())))
                .findFirst()
                .orElseGet(() -> {
            System.out.println("No existing slot found, creating new slot...");
            Gym gym = gymRepository.findById(request.getGymId()).orElse(null);
            if (gym == null) {
                System.err.println("DEBUG: Gym not found with ID: " + request.getGymId());
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

            Slot savedSlot = slotRepository.save(newSlot);
            System.out.println("Created new slot ID: " + savedSlot.getId() + " with time: " + savedSlot.getTime());
            return savedSlot;
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
            // If normalization fails, return the original format for frontend compatibility
            return candidate;
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

    private Map<String, List<String>> getBookedHoursByDate(Long gymId) {
        return slotRepository.findByGymId(gymId).stream()
                .filter(slot -> !slot.isAvailable() || slot.getCurrentBookings() > 0)
                .collect(Collectors.groupingBy(
                        slot -> slot.getDate().toString(),
                        Collectors.mapping(Slot::getTime, Collectors.toList())
                ));
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
