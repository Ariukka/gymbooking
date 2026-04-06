package com.example.gymbooking.controller;

import com.example.gymbooking.dto.CreateBookingRequest;
import com.example.gymbooking.model.Gym;
import com.example.gymbooking.model.Booking;
import com.example.gymbooking.model.Slot;
import com.example.gymbooking.model.User;
import com.example.gymbooking.repository.BookingRepository;
import com.example.gymbooking.repository.GymRepository;
import com.example.gymbooking.repository.SlotRepository;
import com.example.gymbooking.repository.UserRepository;
import com.example.gymbooking.service.EmailService;
import com.example.gymbooking.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingRepository bookingRepository;
    private final EmailService emailService;
    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final SlotRepository slotRepository;
    private final GymRepository gymRepository;

    public BookingController(BookingRepository bookingRepository,
                             EmailService emailService,
                             NotificationService notificationService,
                             UserRepository userRepository,
                             SlotRepository slotRepository,
                             GymRepository gymRepository) {
        this.bookingRepository = bookingRepository;
        this.emailService = emailService;
        this.notificationService = notificationService;
        this.userRepository = userRepository;
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

        boolean alreadyBooked = bookingRepository.existsBySlot_IdAndStatusIn(
                slot.getId(), List.of("PENDING", "CONFIRMED"));
        if (alreadyBooked) {
            return ResponseEntity.badRequest().body(Map.of("error", "Slot already booked"));
        }

        Booking booking = new Booking();
        booking.setUser(currentUser);
        booking.setGym(slot.getGym());
        booking.setSlot(slot);
        booking.setTotalPrice(request.getTotalPrice() != null ? request.getTotalPrice() : slot.getPrice());
        booking.setStatus("CONFIRMED");
        booking.setApproved(true);
        booking.setConfirmedAt(LocalDateTime.now());

        Booking savedBooking = bookingRepository.save(booking);

        slot.incrementBookings();
        slotRepository.save(slot);

        if (savedBooking.getEmail() != null && savedBooking.getDate() != null && savedBooking.getTime() != null) {
            emailService.sendBookingEmail(
                    savedBooking.getEmail(),
                    savedBooking.getDate().toString(),
                    savedBooking.getTime()
            );
        }

        notificationService.createNotification(
                currentUser.getId(),
                "✅ Захиалга баталгаажлаа",
                String.format("Таны %s %s цагийн захиалга амжилттай баталгаажлаа.",
                        savedBooking.getDate(), savedBooking.getTime())
        );

        List<User> admins = userRepository.findByRole("ADMIN");
        for (User admin : admins) {
            notificationService.createNotification(
                    admin.getId(),
                    "🆕 Шинэ захиалга",
                    String.format("%s хэрэглэгч %s %s цагт \"%s\" заалд захиалга хийлээ.",
                            currentUser.getUsername(),
                            savedBooking.getDate(),
                            savedBooking.getTime(),
                            savedBooking.getGym() != null ? savedBooking.getGym().getName() : "")
            );
        }

        return ResponseEntity.ok(savedBooking);
    }

    @GetMapping("/my")
    public ResponseEntity<?> getMyBookings(@AuthenticationPrincipal User currentUser) {
        if (currentUser == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }
        return ResponseEntity.ok(bookingRepository.findByUser_IdOrderByCreatedAtDesc(currentUser.getId()));
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

    private Slot resolveSlot(CreateBookingRequest request) {
        if (request == null) {
            return null;
        }

        if (request.getSlotId() != null) {
            return slotRepository.findById(request.getSlotId()).orElse(null);
        }

        if (request.getGymId() == null || request.getDate() == null || request.getTime() == null) {
            return null;
        }

        return slotRepository.findByGymIdAndDateAndTime(
                request.getGymId(), request.getDate(), request.getTime()
        ).orElseGet(() -> {
            Gym gym = gymRepository.findById(request.getGymId()).orElse(null);
            if (gym == null) {
                return null;
            }

            Slot newSlot = new Slot();
            newSlot.setGym(gym);
            newSlot.setDate(request.getDate());
            newSlot.setTime(request.getTime());
            newSlot.setPrice(request.getTotalPrice());
            newSlot.setAvailable(true);
            newSlot.setMaxCapacity(1);
            newSlot.setCurrentBookings(0);

            return slotRepository.save(newSlot);
        });
    }
}
