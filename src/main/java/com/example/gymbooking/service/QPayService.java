package com.example.gymbooking.service;

import com.example.gymbooking.model.Payment;
import com.example.gymbooking.repository.PaymentRepository;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class QPayService {

    private final PaymentRepository paymentRepository;

    public QPayService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    // ======================
    // 1️⃣ React useEffect-д зориулсан төлбөр шалгах
    // ======================
    public Map<String, Object> checkPayment(String invoiceId) {
        Map<String, Object> result = new HashMap<>();
        Optional<Payment> paymentOpt = paymentRepository.findByTransactionId(invoiceId);

        if (paymentOpt.isPresent() && "PAID".equalsIgnoreCase(paymentOpt.get().getStatus())) {
            result.put("paid", true);
        } else {
            result.put("paid", false);
        }

        return result;
    }

    // ======================
    // 2️⃣ Инвойс үүсгэх (тест QR код)
    // ======================
    public Map<String, Object> createInvoice(Payment payment) {
        Map<String, Object> invoice = new HashMap<>();
        String invoiceId = UUID.randomUUID().toString();

        invoice.put("invoice_id", invoiceId);
        invoice.put("amount", payment.getAmount());
        invoice.put("qr_image_url", "https://api.qrserver.com/v1/create-qr-code/?size=300x300&data=" + invoiceId);

        // Локал transactionId-тэй Payment-г update хийх
        payment.setTransactionId(invoiceId);
        paymentRepository.save(payment);

        return invoice;
    }
}