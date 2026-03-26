package com.example.gymbooking.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    public void sendBookingEmail(String toEmail, String date, String time){

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setSubject("Заалны цагийн захиалга баталгаажлаа");

        message.setText(
                "Сайн байна уу?\n\n" +
                        "Таны захиалсан цаг амжилттай баталгаажлаа.\n\n" +
                        "Өдөр: " + date + "\n" +
                        "Цаг: " + time + "\n\n" +
                        "Баярлалаа."
        );

        mailSender.send(message);
    }
}