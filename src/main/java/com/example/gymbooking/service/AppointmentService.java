package com.example.gymbooking.service;

import com.example.gymbooking.dto.appointment.AppointmentRequestDto;
import com.example.gymbooking.dto.appointment.AppointmentResponseDto;

import java.util.List;

public interface AppointmentService {
    AppointmentResponseDto createAppointment(AppointmentRequestDto requestDto);

    List<AppointmentResponseDto> getAppointmentsByUserId(String userId);

    AppointmentResponseDto getAppointmentById(Long id);

    AppointmentResponseDto cancelAppointment(Long id);
}
