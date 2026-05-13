package com.example.gymbooking.controller;

import com.example.gymbooking.dto.CreatePaymentRequest;
import com.example.gymbooking.model.Booking;
import com.example.gymbooking.model.Payment;
import com.example.gymbooking.model.User;
import com.example.gymbooking.repository.BookingRepository;
import com.example.gymbooking.repository.PaymentRepository;
import com.example.gymbooking.repository.SlotRepository;
import com.example.gymbooking.repository.UserRepository;
import com.example.gymbooking.service.NotificationService;
import com.example.gymbooking.service.QPayService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@RestController
@RequestMapping({"/api/payments", "/api/payment", "/payment", "/api/admin/payments"})
@CrossOrigin(origins = "http://localhost:3000")
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    private final PaymentRepository paymentRepository;
    private final BookingRepository bookingRepository;
    private final QPayService qPayService;
    private final NotificationService notificationService;
    private final SlotRepository slotRepository;
    private final UserRepository userRepository;
    private final boolean paymentRequired;

    public PaymentController(PaymentRepository paymentRepository,
                             BookingRepository bookingRepository,
                             QPayService qPayService,
                             NotificationService notificationService,
                             SlotRepository slotRepository,
                             UserRepository userRepository,
                             @Value("${app.payment.required:true}") boolean paymentRequired) {
        this.paymentRepository = paymentRepository;
        this.bookingRepository = bookingRepository;
        this.qPayService = qPayService;
        this.notificationService = notificationService;
        this.slotRepository = slotRepository;
        this.userRepository = userRepository;
        this.paymentRequired = paymentRequired;
    }

    @PostMapping("/{paymentId}/qpay-invoice")
    public ResponseEntity<?> createQpayInvoice(@PathVariable Long paymentId) {
        return paymentRepository.findById(paymentId)
                .map(payment -> {
                    if (!"PENDING".equals(payment.getStatus())) {
                        return ResponseEntity.badRequest()
                                .body(Map.of("error", "Payment already processed"));
                    }

                    try {
                        log.info("Creating QPay invoice. paymentId={}, bookingId={}, userId={}, amount={}, method={}",
                                payment.getId(),
                                payment.getBooking() != null ? payment.getBooking().getId() : null,
                                payment.getUserId(),
                                payment.getAmount(),
                                payment.getPaymentMethod());

                        Map<String, Object> invoice = qPayService.createInvoice(payment);
                        if (invoice.containsKey("invoice_id")) {
                            payment.setTransactionId(Objects.toString(invoice.get("invoice_id"), null));
                            paymentRepository.save(payment);
                        }
                        Map<String, Object> response = new HashMap<>();
                        response.put("invoice", invoice);
                        response.put("qpay", qPayService.buildQrPaymentPayload(payment, invoice));
                        return ResponseEntity.ok(response);
                    } catch (IllegalStateException ex) {
                        log.error("QPay invoice backend error. paymentId={}, bookingId={}, userId={}, amount={}, message={}",
                                payment.getId(),
                                payment.getBooking() != null ? payment.getBooking().getId() : null,
                                payment.getUserId(),
                                payment.getAmount(),
                                ex.getMessage(),
                                ex);

                        return ResponseEntity.status(500).body(Map.of(
                                "message", "Failed to create QPay invoice",
                                "details", ex.getMessage(),
                                "paymentId", payment.getId()
                        ));
                    } catch (Exception ex) {
                        log.error("Unexpected QPay invoice error. paymentId={}, bookingId={}, userId={}, amount={}",
                                payment.getId(),
                                payment.getBooking() != null ? payment.getBooking().getId() : null,
                                payment.getUserId(),
                                payment.getAmount(),
                                ex);

                        return ResponseEntity.status(500).body(Map.of(
                                "message", "Failed to create QPay invoice",
                                "details", "Unexpected server error while creating QPay invoice.",
                                "paymentId", payment.getId()
                        ));
                    }
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/check/{invoiceId}")
    public ResponseEntity<?> checkPayment(@PathVariable String invoiceId) {
        Map<String, Object> result = qPayService.checkPayment(invoiceId);

        if (Boolean.TRUE.equals(result.get("paid"))) {
            Payment payment = paymentRepository.findByTransactionId(invoiceId).orElse(null);
            if (payment != null && !"PAID".equals(payment.getStatus())) {
                markPaymentPaid(payment, Objects.toString(result.get("payment_id"), null));
            }
        }

        return ResponseEntity.ok(result);
    }

    @RequestMapping(value = "/qpay/callback", method = {RequestMethod.POST, RequestMethod.GET})
    @Transactional
    public ResponseEntity<?> qpayCallback(@RequestBody(required = false) Map<String, Object> callbackData,
                                          @RequestParam Map<String, String> queryParams) {
        Map<String, Object> payload = mergeCallbackData(callbackData, queryParams);
        Optional<Payment> paymentOpt = resolveCallbackPayment(payload);

        if (paymentOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Unable to resolve payment",
                    "message", "Provide local_payment_id, invoice_id, or payment_id in callback payload."
            ));
        }

        Payment payment = paymentOpt.get();
        boolean paid = isCallbackPaid(payload);
        String externalPaymentId = Objects.toString(payload.get("payment_id"), null);

        if (paid) {
            markPaymentPaid(payment, externalPaymentId);
            return ResponseEntity.ok(Map.of(
                    "result", "success",
                    "paymentId", payment.getId(),
                    "invoiceId", payment.getTransactionId(),
                    "externalPaymentId", payment.getPaymentId()
            ));
        }

        markPaymentFailed(payment);
        return ResponseEntity.ok(Map.of(
                "result", "failed",
                "paymentId", payment.getId(),
                "invoiceId", payment.getTransactionId()
        ));
    }

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
        log.info("=== PAYMENT DEBUG START ===");
        log.info("Creating payment with request: {}", request);
        log.info("REQUEST USER ID: {}", request.getUserId());
        log.info("REQUEST BOOKING ID: {}", request.resolveBookingId());
        log.info("REQUEST AMOUNT: {}", request.getAmount());
        log.info("REQUEST PAYMENT METHOD: {}", request.getPaymentMethod());

        return bookingRepository.findById(request.resolveBookingId())
                .map(booking -> {
                    log.info("Found booking: {}", booking);
                    log.info("BOOKING USER: {}", booking.getUser());
                    log.info("BOOKING USER ID: {}", booking.getUser() != null ? booking.getUser().getId() : "null");

                    BigDecimal resolvedAmount = request.getAmount() != null
                            ? request.getAmount()
                            : booking.getTotalPrice();

                    if (resolvedAmount == null || resolvedAmount.compareTo(BigDecimal.ZERO) <= 0) {
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
                    log.info("SETTING USER ID - BOOKING USER ID: {}, REQUEST USER ID: {}",
                        booking.getUser() != null ? booking.getUser().getId() : "null",
                        request.getUserId());

                    User paymentUser = booking.getUser();
                    if (paymentUser != null && paymentUser.getId() != null) {
                        payment.setUserId(paymentUser.getId());
                        payment.setUser(paymentUser);
                        log.info("USING BOOKING USER ID: {}", paymentUser.getId());
                    } else if (request.getUserId() != null) {
                        paymentUser = userRepository.findById(request.getUserId()).orElse(null);
                        if (paymentUser == null) {
                            log.error("REQUEST USER NOT FOUND: {}", request.getUserId());
                            return ResponseEntity.badRequest().body(Map.of("error", "User not found", "message", "User not found"));
                        }
                        payment.setUserId(paymentUser.getId());
                        payment.setUser(paymentUser);
                        log.info("USING REQUEST USER ID: {}", paymentUser.getId());
                    }
                    if (payment.getUserId() == null) {
                        log.error("USER ID IS NULL - BOOKING USER: {}, REQUEST USER: {}",
                            booking.getUser(), request.getUserId());
                        return ResponseEntity.badRequest().body(Map.of("error", "userId is required"));
                    }
                    log.info("FINAL PAYMENT USER ID: {}", payment.getUserId());

                    boolean isQPayPayment = "QPAY".equalsIgnoreCase(payment.getPaymentMethod());

                    if (!paymentRequired && !isQPayPayment) {
                        payment.setStatus("PENDING");
                        payment.setTransactionId("TEST-BYPASS-" + booking.getId());
                        Payment saved = paymentRepository.save(payment);
                        markPaymentPaid(saved, null);

                        return ResponseEntity.ok(Map.of(
                                "payment", saved,
                                "message", "Test mode: payment bypassed and booking confirmed."
                        ));
                    }

                    payment.setStatus("PENDING");
                    return ResponseEntity.ok(paymentRepository.save(payment));
                })
                .orElse(ResponseEntity.badRequest().build());
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<?> updatePaymentStatus(@PathVariable Long id, @RequestBody Map<String, String> request) {
        if (request == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Request body is required"));
        }
        return paymentRepository.findById(id)
                .map(payment -> {
                    String nextStatus = request.get("status");
                    if (request.containsKey("transactionId")) {
                        payment.setTransactionId(request.get("transactionId"));
                    }

                    Payment saved = paymentRepository.save(payment);

                    if ("PAID".equalsIgnoreCase(nextStatus)) {
                        markPaymentPaid(saved, request.get("paymentId"));
                    } else if ("FAILED".equalsIgnoreCase(nextStatus)) {
                        markPaymentFailed(saved);
                    } else {
                        saved.setStatus(nextStatus);
                        saved = paymentRepository.save(saved);
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

    @Transactional
    private void lockSlotForBooking(Booking booking) {
        if (booking == null || booking.getSlot() == null) {
            return;
        }
        booking.getSlot().setAvailable(false);
        slotRepository.save(booking.getSlot());
    }

    @Transactional
    private void releaseSlotForBooking(Booking booking) {
        if (booking == null || booking.getSlot() == null) {
            return;
        }
        booking.getSlot().setAvailable(true);
        slotRepository.save(booking.getSlot());
    }

    private boolean isCallbackPaid(Map<String, Object> callbackData) {
        if (callbackData == null || callbackData.isEmpty()) {
            return true;
        }

        Object statusObj = callbackData.getOrDefault(
                "status",
                callbackData.getOrDefault("payment_status", null));

        if (statusObj == null) {
            return true;
        }

        String status = String.valueOf(statusObj).trim().toLowerCase();
        if (status.equals("1") || status.equals("true") || status.equals("paid")
                || status.equals("success") || status.contains("paid") || status.contains("success")) {
            return true;
        }
        if (status.contains("fail") || status.contains("error") || status.contains("cancel")) {
            return false;
        }
        return true;
    }

    private Map<String, Object> mergeCallbackData(Map<String, Object> callbackData, Map<String, String> queryParams) {
        Map<String, Object> payload = new HashMap<>();
        if (queryParams != null) {
            payload.putAll(queryParams);
        }
        if (callbackData != null) {
            payload.putAll(callbackData);
        }
        return payload;
    }

    private Optional<Payment> resolveCallbackPayment(Map<String, Object> payload) {
        String localPaymentId = Objects.toString(
                payload.getOrDefault("local_payment_id", payload.get("localPaymentId")), null);
        if (localPaymentId != null && !localPaymentId.isBlank()) {
            try {
                return paymentRepository.findById(Long.parseLong(localPaymentId));
            } catch (NumberFormatException ignored) {
                // Fall through to invoice and payment lookup.
            }
        }

        String invoiceId = Objects.toString(
                payload.getOrDefault("invoice_id", payload.get("invoiceId")), null);
        if (invoiceId != null && !invoiceId.isBlank()) {
            Optional<Payment> payment = paymentRepository.findByTransactionId(invoiceId);
            if (payment.isPresent()) {
                return payment;
            }
        }

        String externalPaymentId = Objects.toString(payload.get("payment_id"), null);
        if (externalPaymentId != null && !externalPaymentId.isBlank()) {
            return paymentRepository.findByPaymentId(externalPaymentId);
        }

        return Optional.empty();
    }

    private void markPaymentPaid(Payment payment, String externalPaymentId) {
        String previousStatus = payment.getStatus();
        payment.setStatus("PAID");
        payment.setPaidAt(LocalDateTime.now());
        if (externalPaymentId != null && !externalPaymentId.isBlank()) {
            payment.setPaymentId(externalPaymentId);
        }
        Payment savedPayment = paymentRepository.save(payment);

        Booking booking = payment.getBooking();
        markBookingConfirmed(booking);
        lockSlotForBooking(booking);

        if (!"PAID".equalsIgnoreCase(previousStatus)) {
            notificationService.createPaymentSuccessNotification(savedPayment);
        }
    }

    private void markBookingConfirmed(Booking booking) {
        if (booking == null) {
            return;
        }
        booking.setStatus("CONFIRMED");
        booking.setApproved(true);
        if (booking.getConfirmedAt() == null) {
            booking.setConfirmedAt(LocalDateTime.now());
        }
        bookingRepository.save(booking);
    }

    @GetMapping("/detailed")
    public ResponseEntity<?> getDetailedPayments() {
        List<Payment> payments = paymentRepository.findAllByOrderByCreatedAtDesc();
        return ResponseEntity.ok(Map.of(
                "payments", payments,
                "count", payments.size(),
                "message", "Detailed payments retrieved successfully"
        ));
    }

    private void markPaymentFailed(Payment payment) {
        payment.setStatus("FAILED");
        paymentRepository.save(payment);

        Booking booking = payment.getBooking();
        markBookingCancelled(booking);
        releaseSlotForBooking(booking);
    }

    private void markBookingCancelled(Booking booking) {
        if (booking == null) {
            return;
        }
        booking.setStatus("CANCELLED");
        booking.setApproved(false);
        booking.setConfirmedAt(null);
        bookingRepository.save(booking);
    }
}




