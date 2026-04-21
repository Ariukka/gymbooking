package com.example.gymbooking.repository;

import com.example.gymbooking.model.OtpCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OtpRepository extends JpaRepository<OtpCode, Long> {
    Optional<OtpCode> findByPhoneAndCodeAndUsedFalse(String phone, String code);
    Optional<OtpCode> findByEmailAndCodeAndUsedFalse(String email, String code);
    Optional<OtpCode> findByEmailAndTypeAndUsedFalse(String email, String type);
    void deleteByEmailAndType(String email, String type);
}
