package com.example.gymbooking.controller;

import com.example.gymbooking.dto.CreatePaymentRequest;
import com.example.gymbooking.model.Payment;
import com.example.gymbooking.model.Booking;
import com.example.gymbooking.repository.BookingRepository;
import com.example.gymbooking.repository.PaymentRepository;
import com.example.gymbooking.repository.SlotRepository;
import com.example.gymbooking.service.NotificationService;
import com.example.gymbooking.service.QPayService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.math.BigDecimal;

@RestController
@RequestMapping({"/api/payments", "/api/payment", "/payment"})
@CrossOrigin(origins = "http://localhost:3000")
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    private final PaymentRepository paymentRepository;
    private final BookingRepository bookingRepository;
    private final QPayService qPayService;
    private final NotificationService notificationService;
    private final SlotRepository slotRepository;
    private final boolean paymentRequired;

    public PaymentController(PaymentRepository paymentRepository,
                             BookingRepository bookingRepository,
                             QPayService qPayService,
                             NotificationService notificationService,
                             SlotRepository slotRepository,
                             @Value("${app.payment.required:true}") boolean paymentRequired) {
        this.paymentRepository = paymentRepository;
        this.bookingRepository = bookingRepository;
        this.qPayService = qPayService;
        this.notificationService = notificationService;
        this.slotRepository = slotRepository;
        this.paymentRequired = paymentRequired;
    }

    // ========================
    // 1️⃣ Инвойс үүсгэх (QPay)
    // ========================
    @PostMapping("/{paymentId}/qpay-invoice")
    public ResponseEntity<?> createQpayInvoice(@PathVariable Long paymentId) {
        return paymentRepository.findById(paymentId)
                .map(payment -> {
                    if (!"PENDING".equals(payment.getStatus())) {
                        return ResponseEntity.badRequest()
                                .body(Map.of("error", "Payment already processed"));
                    }
                    Map<String, Object> invoice = qPayService.createInvoice(payment);
                    if (invoice.containsKey("invoice_id")) {
                        payment.setTransactionId(Objects.toString(invoice.get("invoice_id"), null));
                        paymentRepository.save(payment);
                    }
                    return ResponseEntity.ok(invoice);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ========================
    // 2️⃣ Төлбөрийг шалгах (React useEffect)
    // ========================
    @GetMapping("/check/{invoiceId}")
    public ResponseEntity<?> checkPayment(@PathVariable String invoiceId) {

        Map<String, Object> result = qPayService.checkPayment(invoiceId);

        if (Boolean.TRUE.equals(result.get("paid"))) {

            Payment payment = paymentRepository.findByTransactionId(invoiceId).orElse(null);

            if (payment != null && !"PAID".equals(payment.getStatus())) {

                payment.setStatus("PAID");
                paymentRepository.save(payment);

                Booking booking = payment.getBooking();
                booking.setStatus("CONFIRMED");
                booking.setApproved(true);
                bookingRepository.save(booking);

                lockSlotForBooking(booking);

                notificationService.createPaymentSuccessNotification(payment);
            }
        }

        return ResponseEntity.ok(result);
    }

    // ========================
    // 3️⃣ QPay Callback (Webhook)
    // ========================
    @PostMapping("/qpay/callback")
    @Transactional
    public ResponseEntity<?> qpayCallback(@RequestBody Map<String, Object> callbackData) {
        String invoiceId = Objects.toString(
                callbackData.getOrDefault("invoice_id", callbackData.get("invoiceId")), null);

        if (invoiceId == null || invoiceId.isBlank()) {
            return ResponseEntity.badRequest().body("invoice_id is required");
        }

        return paymentRepository.findByTransactionId(invoiceId)
                .map(payment -> {
                    String previousStatus = payment.getStatus();
                    boolean paid = isCallbackPaid(callbackData);

                    Booking booking = payment.getBooking();

                    if (paid) {
                        payment.setStatus("PAID");
                        if (callbackData.containsKey("payment_id")) {
                            payment.setTransactionId(
                                    Objects.toString(callbackData.get("payment_id"), payment.getTransactionId()));
                        }
                        paymentRepository.save(payment);

                        booking.setStatus("CONFIRMED");
                        booking.setApproved(true);
                        bookingRepository.save(booking);
                        lockSlotForBooking(booking);

                        if (!"PAID".equalsIgnoreCase(previousStatus)) {
                            notificationService.createPaymentSuccessNotification(payment);
                        }

                        return ResponseEntity.ok(Map.of("result", "success"));
                    } else {
                        payment.setStatus("FAILED");
                        paymentRepository.save(payment);

                        booking.setStatus("CANCELLED");
                        booking.setApproved(false);
                        bookingRepository.save(booking);
                        releaseSlotForBooking(booking);

                        return ResponseEntity.ok(Map.of("result", "failed"));
                    }
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ========================
    // 4️⃣ CRUD & Utility
    // ========================
    @GetMapping
    public List<Payment> getAllPayments() {
        return paymentRepository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Payment> getPaymentById(@PathVariable Long id) {
        return paymentRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping({"", "/create"})
    public ResponseEntity<?> createPayment(@Valid @RequestBody CreatePaymentRequest request) {
        log.info("/payments/create payload: bookingId={}, amount={}, paymentMethod={}, userId={}",
                request.resolveBookingId(),
                request.getAmount(),
                request.getPaymentMethod(),
                request.getUserId());

        Long bookingId = request.resolveBookingId();
        if (bookingId == null) {
            log.warn("/payments/create rejected: bookingId is missing");
            return ResponseEntity.badRequest().body(Map.of("error", "bookingId is required"));
        }

        return bookingRepository.findById(bookingId)
                .map(booking -> {
                    BigDecimal resolvedAmount = request.getAmount() != null
                            ? request.getAmount()
                            : booking.getTotalPrice();

                    if (resolvedAmount == null || resolvedAmount.compareTo(BigDecimal.ZERO) <= 0) {
                        log.warn("/payments/create rejected: invalid amount. requestAmount={}, bookingTotalPrice={}, bookingId={}",
                                request.getAmount(), booking.getTotalPrice(), booking.getId());
                        return ResponseEntity.badRequest().body(Map.of(
                                "error", "amount is required",
                                "message", "Provide amount (or ensure booking has a valid totalPrice)."
                        ));
                    }

                    Payment payment = new Payment();
                    payment.setBooking(booking);
                    payment.setAmount(resolvedAmount);
                    payment.setPaymentMethod(
                            request.getPaymentMethod() == null || request.getPaymentMethod().isBlank()
                                    ? "QPAY"
                                    : request.getPaymentMethod());
                    if (booking.getUser() != null && booking.getUser().getId() != null) {
                        payment.setUserId(booking.getUser().getId());
                    } else {
                        payment.setUserId(request.getUserId());
                    }
                    if (payment.getUserId() == null) {
                        log.warn("/payments/create rejected: userId unresolved for bookingId={}", booking.getId());
                        return ResponseEntity.badRequest().body(Map.of("error", "userId is required"));
                    }

                    if (!paymentRequired) {
                        payment.setStatus("PAID");
                        payment.setTransactionId("TEST-BYPASS-" + booking.getId());
                        Payment saved = paymentRepository.save(payment);

                        booking.setStatus("CONFIRMED");
                        booking.setApproved(true);
                        bookingRepository.save(booking);
                        lockSlotForBooking(booking);
                        notificationService.createPaymentSuccessNotification(saved);

                        return ResponseEntity.ok(Map.of(
                                "payment", saved,
                                "message", "Test mode: payment bypassed and booking confirmed."
                        ));
                    }

                    payment.setStatus("PENDING");
                    log.info("/payments/create accepted: bookingId={}, amount={}, paymentMethod={}, userId={}",
                            booking.getId(), payment.getAmount(), payment.getPaymentMethod(), payment.getUserId());
                    return ResponseEntity.ok(paymentRepository.save(payment));
                })
                .orElseGet(() -> {
                    log.warn("/payments/create rejected: booking not found. bookingId={}", bookingId);
                    return ResponseEntity.badRequest().body(Map.of("error", "booking not found"));
                });
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<?> updatePaymentStatus(@PathVariable Long id, @RequestBody Map<String, String> request) {
        return paymentRepository.findById(id)
                .map(payment -> {
                    String previousStatus = payment.getStatus();
                    String nextStatus = request.get("status");

                    payment.setStatus(nextStatus);
                    if (request.containsKey("transactionId")) {
                        payment.setTransactionId(request.get("transactionId"));
                    }

                    Payment saved = paymentRepository.save(payment);
                    Booking booking = payment.getBooking();

                    if ("PAID".equalsIgnoreCase(nextStatus)) {
                        booking.setStatus("CONFIRMED");
                        booking.setApproved(true);
                        bookingRepository.save(booking);

                        lockSlotForBooking(booking);

                        if (!"PAID".equals(previousStatus)) {
                                notificationService.createPaymentSuccessNotification(saved);
                        }
                    }
                    if ("FAILED".equalsIgnoreCase(nextStatus)) {
                        booking.setStatus("CANCELLED");
                        booking.setApproved(false);
                        bookingRepository.save(booking);
                        releaseSlotForBooking(booking);
                    }
                    return ResponseEntity.ok(saved);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deletePayment(@PathVariable Long id) {
        if (!paymentRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        paymentRepository.deleteById(id);
        return ResponseEntity.ok("Payment deleted");
    }

    // ========================
    // Helper methods
    // ========================
    @Transactional
    private void lockSlotForBooking(Booking booking) {
        if (booking == null || booking.getSlot() == null) return;
        booking.getSlot().setAvailable(false);
        slotRepository.save(booking.getSlot());
    }

    @Transactional
    private void releaseSlotForBooking(Booking booking) {
        if (booking == null || booking.getSlot() == null) return;
        booking.getSlot().setAvailable(true);
        slotRepository.save(booking.getSlot());
    }

    private boolean isCallbackPaid(Map<String, Object> callbackData) {
        if (callbackData == null || callbackData.isEmpty()) return true;

        Object statusObj = callbackData.getOrDefault(
                "status",
                callbackData.getOrDefault("payment_status", null));

        if (statusObj == null) return true;

        String s = String.valueOf(statusObj).trim().toLowerCase();
        if (s.equals("1") || s.equals("true") || s.equals("paid") || s.equals("success") || s.contains("paid") || s.contains("success")) {
            return true;
        }
        if (s.contains("fail") || s.contains("error") || s.contains("cancel")) {
            return false;
        }
        return true;
    }
}
