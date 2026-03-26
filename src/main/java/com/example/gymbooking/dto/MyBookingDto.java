package com.example.gymbooking.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MyBookingDto(
        Long bookingId,
        String status,
        boolean approved,
        BigDecimal totalPrice,
        LocalDateTime createdAt,
        Long gymId,
        String gymName,
        Long slotId,
        String slotDate,
        String slotTime
) {}

