package com.example.gymbooking.service;

import org.springframework.stereotype.Service;

@Service
public class SmsService {

    public void sendSms(String phone, String code){
        // SMS gateway API ашиглана
        System.out.println("Send SMS to " + phone + " code: " + code);
    }

}