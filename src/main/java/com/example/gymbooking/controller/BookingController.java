package com.example.gymbooking.controller;

import com.example.gymbooking.model.Booking;
import com.example.gymbooking.repository.BookingRepository;
import com.example.gymbooking.service.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private EmailService emailService;

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
