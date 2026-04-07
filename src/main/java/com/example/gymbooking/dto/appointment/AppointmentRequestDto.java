package com.example.gymbooking.dto.appointment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class AppointmentRequestDto {

    @NotBlank(message = "userId is required")
    private String userId;

    @NotNull(message = "date is required")
    private LocalDate date;

    @NotBlank(message = "time is required")
    private String time;
}
