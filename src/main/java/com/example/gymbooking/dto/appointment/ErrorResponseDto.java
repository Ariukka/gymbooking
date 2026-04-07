package com.example.gymbooking.dto.appointment;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ErrorResponseDto {
    private LocalDateTime timestamp;
    private int status;
    private String error;
    private String message;
}
