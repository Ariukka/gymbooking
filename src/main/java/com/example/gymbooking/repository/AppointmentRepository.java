package com.example.gymbooking.repository;

import com.example.gymbooking.entity.Appointment;
import com.example.gymbooking.entity.AppointmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {
    List<Appointment> findByUserId(String userId);

    boolean existsByDateAndTimeAndStatusNot(LocalDate date, String time, AppointmentStatus status);
}
