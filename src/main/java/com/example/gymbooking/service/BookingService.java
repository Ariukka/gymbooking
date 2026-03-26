package com.example.gymbooking.service;

import com.example.gymbooking.model.Booking;
import com.example.gymbooking.repository.BookingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class BookingService {

    @Autowired
    private BookingRepository bookingRepository;

    // Booking үүсгэх (save хийх)
    public Booking createBooking(Booking booking) {
        return bookingRepository.save(booking);
    }

    // User ID-д холбогдсон booking-уудыг авах
    public List<Booking> getBookingsByUserId(Long userId) {
        return bookingRepository.findByUser_Id(userId);
    }

    // Gym-д холбогдсон booking-уудыг авах
    public List<Booking> getBookingsByGym(com.example.gymbooking.model.Gym gym) {
        return bookingRepository.findByGym(gym);
    }
}