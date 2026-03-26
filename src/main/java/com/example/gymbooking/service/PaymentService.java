package com.example.gymbooking.service;

import com.example.gymbooking.model.Payment;
import com.example.gymbooking.repository.PaymentRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;

    public PaymentService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    // Create payment URL
    public String createPaymentUrl(Long paymentId, Double amount, String email) {
        // Generate transaction ID
        String transactionId = "TXN" + System.currentTimeMillis();

        // Save transaction ID to payment
        Payment payment = paymentRepository.findById(paymentId).orElse(null);
        if (payment != null) {
            payment.setTransactionId(transactionId);
            paymentRepository.save(payment);
        }

        // Return payment URL with transaction ID
        return "https://qpay.mn/payment?transactionId=" + transactionId + "&amount=" + amount;
    }

    // Verify payment
    public boolean verifyPayment(String transactionId) {
        // Find payment by transaction ID
        Optional<Payment> paymentOpt = paymentRepository.findByTransactionId(transactionId);

        if (paymentOpt.isPresent()) {
            Payment payment = paymentOpt.get();
            // Call external payment provider API to verify
            // For now, return true for demo
            return true;
        }

        return false;
    }

    // Check payment status
    public boolean checkPaymentStatus(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId).orElse(null);
        if (payment != null && payment.getTransactionId() != null) {
            // Call external payment provider API to check status
            // For now, return true if status is PAID
            return "PAID".equals(payment.getStatus());
        }
        return false;
    }

    // Process refund
    public boolean processRefund(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId).orElse(null);
        if (payment != null && payment.getTransactionId() != null) {
            // Call external payment provider API to refund
            payment.setStatus("REFUNDED");
            paymentRepository.save(payment);
            return true;
        }
        return false;
    }
}