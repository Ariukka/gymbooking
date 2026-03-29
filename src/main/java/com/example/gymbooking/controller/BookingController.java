package com.example.gymbooking.controller;

import com.example.gymbooking.model.Booking;
import com.example.gymbooking.model.Slot;
import com.example.gymbooking.model.User;
import com.example.gymbooking.repository.BookingRepository;
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

    public BookingController(BookingRepository bookingRepository,
                             EmailService emailService,
                             NotificationService notificationService,
                             UserRepository userRepository,
                             SlotRepository slotRepository) {
        this.bookingRepository = bookingRepository;
        this.emailService = emailService;
        this.notificationService = notificationService;
        this.userRepository = userRepository;
        this.slotRepository = slotRepository;
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
                                           @RequestBody Booking booking) {
        if (currentUser == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }

        if (booking.getSlot() == null || booking.getSlot().getId() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Slot is required"));
        }

        Slot slot = slotRepository.findById(booking.getSlot().getId()).orElse(null);
        if (slot == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Slot not found"));
        }

        if (!slot.isAvailable() || !slot.hasCapacity()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Selected slot is not available"));
        }

        boolean alreadyBooked = bookingRepository.existsBySlot_IdAndStatusIn(
                slot.getId(), List.of("PENDING", "CONFIRMED"));
        if (alreadyBooked) {
            return ResponseEntity.badRequest().body(Map.of("error", "Slot already booked"));
        }

        booking.setUser(currentUser);
        booking.setGym(slot.getGym());
        booking.setSlot(slot);
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
}
