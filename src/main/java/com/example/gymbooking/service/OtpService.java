package com.example.gymbooking.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@Service
public class OtpService {

    @Autowired
    private JavaMailSender mailSender;

    private static final SecureRandom random = new SecureRandom();

    // Generate 6-digit OTP
    public String generateOtp() {
        return String.format("%06d", random.nextInt(1000000));
    }

    // Send OTP for registration verification
    public void sendRegistrationOtp(String email, String otp) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(email);
            message.setSubject("GymBooking - ÐÑÑÑÐ³Ð» Ð±Ð°ÑÐ°Ð»ÑÑÐ»Ð°Ñ ÐºÐ¾Ð´");
            
            String emailBody = "Сайн байна уу!\n\n" +
                              "Таны GymBooking бүртгэл баталгаажуулах код:\n\n" +
                              "================================\n" +
                              "         " + otp + "\n" +
                              "================================\n\n" +
                              "Энэ код 5 минутын дотор оруулна уу.\n" +
                              "Хэрэглэгчийн мэдээллийн төлөвлөгийг хийгээрэй байна.\n\n" +
                              "Баярлалаа,\n" +
                              "GymBooking баг";
            
            message.setText(emailBody);
            message.setFrom("gymbooking@gmail.com");

            mailSender.send(message);
            System.out.println("Бүртгэлийн OTP имэйл илгээгдсэн: " + email);
        } catch (Exception e) {
            System.err.println("Failed to send email: " + e.getMessage());
            throw new RuntimeException("Email send failed: " + e.getMessage());
        }
    }

    // Send OTP for password reset (existing method)
    public void sendResetPasswordOtp(String email, String otp) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(email);
            message.setSubject("GymBooking - Нууц үг сэргээх код");
            
            String emailBody = "Сайн байна уу!\n\n" +
                              "Таны нууц үг сэргээх код:\n\n" +
                              "================================\n" +
                              "         " + otp + "\n" +
                              "================================\n\n" +
                              "Энэ код 5 минутын дотор оруулна уу.\n" +
                              "Нууц үг сэргээх төлөвлөгийг хийгээрэй байна.\n\n" +
                              "Баярлалаа,\n" +
                              "GymBooking баг";
            
            message.setText(emailBody);
            message.setFrom("gymbooking@gmail.com");

            mailSender.send(message);
            System.out.println("Нууц үг сэргээх имэйл илгээгдсэн: " + email);
        } catch (Exception e) {
            System.err.println("Failed to send email: " + e.getMessage());
            throw new RuntimeException("Email send failed: " + e.getMessage());
        }
    }

    // Check if OTP is expired
    public boolean isOtpExpired(LocalDateTime expiresAt) {
        return LocalDateTime.now().isAfter(expiresAt);
    }
}