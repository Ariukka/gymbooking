package com.example.gymbooking.dto.appointment;

import com.example.gymbooking.entity.AppointmentStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Builder
public class AppointmentResponseDto {
    private Long id;
    private String userId;
    private LocalDate date;
    private String time;
    private AppointmentStatus status;
}
