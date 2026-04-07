package com.example.gymbooking.service.impl;

import com.example.gymbooking.dto.appointment.AppointmentRequestDto;
import com.example.gymbooking.dto.appointment.AppointmentResponseDto;
import com.example.gymbooking.entity.Appointment;
import com.example.gymbooking.entity.AppointmentStatus;
import com.example.gymbooking.exception.ConflictException;
import com.example.gymbooking.exception.ResourceNotFoundException;
import com.example.gymbooking.repository.AppointmentRepository;
import com.example.gymbooking.service.AppointmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AppointmentServiceImpl implements AppointmentService {

    private final AppointmentRepository appointmentRepository;

    @Override
    public AppointmentResponseDto createAppointment(AppointmentRequestDto requestDto) {
        boolean isBooked = appointmentRepository.existsByDateAndTimeAndStatusNot(
                requestDto.getDate(),
                requestDto.getTime(),
                AppointmentStatus.CANCELLED
        );

        if (isBooked) {
            throw new ConflictException("Selected date and time is already booked");
        }

        Appointment appointment = Appointment.builder()
                .userId(requestDto.getUserId())
                .date(requestDto.getDate())
                .time(requestDto.getTime())
                .status(AppointmentStatus.CONFIRMED)
                .build();

        return toResponse(appointmentRepository.save(appointment));
    }

    @Override
    public List<AppointmentResponseDto> getAppointmentsByUserId(String userId) {
        return appointmentRepository.findByUserId(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public AppointmentResponseDto getAppointmentById(Long id) {
        Appointment appointment = appointmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found with id: " + id));

        return toResponse(appointment);
    }

    @Override
    public AppointmentResponseDto cancelAppointment(Long id) {
        Appointment appointment = appointmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found with id: " + id));

        appointment.setStatus(AppointmentStatus.CANCELLED);
        return toResponse(appointmentRepository.save(appointment));
    }

    private AppointmentResponseDto toResponse(Appointment appointment) {
        return AppointmentResponseDto.builder()
                .id(appointment.getId())
                .userId(appointment.getUserId())
                .date(appointment.getDate())
                .time(appointment.getTime())
                .status(appointment.getStatus())
                .build();
    }
}
