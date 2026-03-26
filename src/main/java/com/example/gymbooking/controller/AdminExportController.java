package com.example.gymbooking.controller;

import com.example.gymbooking.model.Booking;
import com.example.gymbooking.repository.BookingRepository;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.example.gymbooking.model.User;

import java.util.List;
import java.util.stream.Collectors;
import java.util.Map;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;

@RestController
@RequestMapping("/admin-export")
@CrossOrigin(origins = "http://localhost:3000")
public class AdminExportController {

    private final BookingRepository bookingRepository;

    public AdminExportController(BookingRepository bookingRepository) {
        this.bookingRepository = bookingRepository;
    }

    // Бүх booking-уудыг export хийх
    @GetMapping("/bookings")
    public ResponseEntity<List<Map<String, Object>>> exportBookings(@AuthenticationPrincipal User user) {
        List<Booking> bookings = bookingRepository.findAll();

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        List<Map<String, Object>> data = bookings.stream().map(b -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", b.getId());
            row.put("user", b.getUser() != null ? b.getUser().getUsername() : "N/A");
            row.put("gym", b.getGym() != null ? b.getGym().getName() : "N/A");
            row.put("slotDate", b.getSlot() != null ? b.getSlot().getDate().toString() : "N/A");
            row.put("slotTime", b.getSlot() != null ? b.getSlot().getTime() : "N/A");
            row.put("totalPrice", b.getTotalPrice());
            row.put("status", b.getStatus());
            row.put("approved", b.isApproved());
            row.put("createdAt", b.getCreatedAt() != null ? b.getCreatedAt().format(formatter) : "N/A");
            return row;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(data);
    }
}