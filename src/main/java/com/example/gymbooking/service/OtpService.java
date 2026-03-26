package com.example.gymbooking.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class OtpService {

    @Autowired
    private JavaMailSender mailSender;

    public void sendResetPasswordOtp(String email, String otp) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(email);
            message.setSubject("GymBooking - Нууц үг сэргээх код");
            message.setText("Таны нууц үг сэргээх код: " + otp + "\n\nЭнэ код 5 минутын хугацаанд хүчинтэй.\n\nGymBooking баг");
            message.setFrom("gymbooking@gmail.com");

            mailSender.send(message);
            System.out.println("✅ Password reset email sent to: " + email);
        } catch (Exception e) {
            System.err.println("❌ Failed to send email: " + e.getMessage());
            throw new RuntimeException("Email send failed: " + e.getMessage());
        }
    }
}