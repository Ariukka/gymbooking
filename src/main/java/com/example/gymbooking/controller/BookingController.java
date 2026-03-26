package com.example.gymbooking.controller;

import com.example.gymbooking.model.Booking;
import com.example.gymbooking.repository.BookingRepository;
import com.example.gymbooking.service.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private EmailService emailService;

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
    public Booking createBooking(@RequestBody Booking booking){

        Booking savedBooking = bookingRepository.save(booking);

        emailService.sendBookingEmail(
                booking.getEmail(),
                booking.getDate().toString(),
                booking.getTime()
        );

        return savedBooking;
    }
}
