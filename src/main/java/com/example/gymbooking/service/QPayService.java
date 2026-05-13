package com.example.gymbooking.service;

import com.example.gymbooking.model.Booking;
import com.example.gymbooking.model.Payment;
import com.example.gymbooking.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
public class QPayService {

    private static final Logger log = LoggerFactory.getLogger(QPayService.class);

    private final PaymentRepository paymentRepository;
    private final RestClient restClient;
    private final String username;
    private final String password;
    private final String invoiceCode;
    private final String invoiceReceiverCode;
    private final String callbackUrl;

    public QPayService(PaymentRepository paymentRepository,
                       @Value("${qpay.base-url:https://merchant-sandbox.qpay.mn/v2}") String baseUrl,
                       @Value("${qpay.username:}") String username,
                       @Value("${qpay.password:}") String password,
                       @Value("${qpay.invoice-code:}") String invoiceCode,
                       @Value("${qpay.invoice-receiver-code:terminal}") String invoiceReceiverCode,
                       @Value("${qpay.callback-url:}") String callbackUrl) {
        this.paymentRepository = paymentRepository;
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
        this.username = username;
        this.password = password;
        this.invoiceCode = invoiceCode;
        this.invoiceReceiverCode = invoiceReceiverCode;
        this.callbackUrl = callbackUrl;
    }

    public Map<String, Object> checkPayment(String invoiceId) {
        Optional<Payment> paymentOpt = paymentRepository.findByTransactionId(invoiceId);
        if (paymentOpt.isPresent() && "PAID".equalsIgnoreCase(paymentOpt.get().getStatus())) {
            return new HashMap<>(Map.of(
                    "paid", true,
                    "invoice_id", invoiceId,
                    "status", "PAID"
            ));
        }

        ensureConfigured();

        Map<String, Object> payload = Map.of(
                "object_type", "INVOICE",
                "object_id", invoiceId,
                "offset", Map.of(
                        "page_number", 1,
                        "page_limit", 100
                )
        );

        Map<String, Object> response = postWithBearer("/payment/check", payload);
        List<Map<String, Object>> rows = asMapList(response.get("rows"));
        Map<String, Object> firstPaidRow = rows.stream()
                .filter(this::isPaidRow)
                .findFirst()
                .orElse(null);

        Map<String, Object> result = new HashMap<>();
        result.put("invoice_id", invoiceId);
        result.put("count", response.getOrDefault("count", 0));
        result.put("paid_amount", response.getOrDefault("paid_amount", BigDecimal.ZERO));
        result.put("rows", rows);
        result.put("paid", firstPaidRow != null);

        if (firstPaidRow != null) {
            result.put("payment_id", firstPaidRow.get("payment_id"));
            result.put("payment_status", firstPaidRow.get("payment_status"));
            result.put("payment_date", firstPaidRow.get("payment_date"));
        }

        return result;
    }

    public Map<String, Object> createInvoice(Payment payment) {
        ensureConfigured();

        String senderInvoiceNo = buildSenderInvoiceNo(payment);
        Map<String, Object> payload = new HashMap<>();
        payload.put("invoice_code", invoiceCode);
        payload.put("sender_invoice_no", senderInvoiceNo);
        payload.put("invoice_receiver_code", defaultIfBlank(invoiceReceiverCode, "terminal"));
        payload.put("invoice_description", buildDescription(payment));
        payload.put("amount", payment.getAmount());
        payload.put("callback_url", buildCallbackUrl(payment));

        Booking booking = payment.getBooking();
        if (booking != null && booking.getUser() != null) {
            Map<String, Object> receiverData = new HashMap<>();
            receiverData.put("name", booking.getUser().getUsername());
            receiverData.put("email", booking.getUser().getEmail());
            receiverData.put("phone", booking.getUser().getPhone());
            payload.put("invoice_receiver_data", receiverData);
        }

        Map<String, Object> response = postWithBearer("/invoice", payload);
        response.put("sender_invoice_no", senderInvoiceNo);
        response.put("local_payment_id", payment.getId());
        return response;
    }

    private String buildDescription(Payment payment) {
        Booking booking = payment.getBooking();
        if (booking == null) {
            return "Gym booking payment";
        }

        String gymName = booking.getGym() != null ? booking.getGym().getName() : "Gym";
        String date = booking.getDate() != null ? booking.getDate().toString() : "unknown-date";
        String time = booking.getTime() != null ? booking.getTime() : "unknown-time";
        return String.format("%s booking %s %s", gymName, date, time);
    }

    private String buildSenderInvoiceNo(Payment payment) {
        Long bookingId = payment.getBooking() != null ? payment.getBooking().getId() : null;
        String base = "BOOKING-" + bookingId + "-PAY-" + payment.getId();
        return base.length() <= 45 ? base : base.substring(0, 45);
    }

    private String buildCallbackUrl(Payment payment) {
        if (callbackUrl == null || callbackUrl.isBlank()) {
            return callbackUrl;
        }

        String separator = callbackUrl.contains("?") ? "&" : "?";
        return callbackUrl + separator + "local_payment_id=" + payment.getId();
    }

    private Map<String, Object> postWithBearer(String path, Object body) {
        String accessToken = getAccessToken();
        try {
            Map<String, Object> response = restClient.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            return response == null ? new HashMap<>() : response;
        } catch (RestClientResponseException ex) {
            log.error("QPay API error on {}: status={}, body={}", path, ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new IllegalStateException("QPay request failed: " + ex.getResponseBodyAsString(), ex);
        } catch (RestClientException ex) {
            log.error("Unable to reach QPay API on {}", path, ex);
            throw new IllegalStateException("Unable to connect to QPay API.", ex);
        }
    }

    private String getAccessToken() {
        try {
            Map<String, Object> response = restClient.post()
                    .uri("/auth/token")
                    .headers(headers -> headers.setBasicAuth(username, password))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of())
                    .retrieve()
                    .body(Map.class);

            String token = response == null ? null : Objects.toString(response.get("access_token"), null);
            if (token == null || token.isBlank()) {
                throw new IllegalStateException("QPay access token missing from response.");
            }
            return token;
        } catch (RestClientResponseException ex) {
            log.error("QPay auth failed: status={}, body={}", ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new IllegalStateException("QPay authentication failed: " + ex.getResponseBodyAsString(), ex);
        } catch (RestClientException ex) {
            log.error("Unable to authenticate with QPay", ex);
            throw new IllegalStateException("Unable to authenticate with QPay.", ex);
        }
    }

    private boolean isPaidRow(Map<String, Object> row) {
        String status = Objects.toString(row.get("payment_status"), "");
        return "PAID".equalsIgnoreCase(status);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> asMapList(Object rows) {
        if (rows instanceof List<?> list) {
            return list.stream()
                    .filter(Map.class::isInstance)
                    .map(item -> (Map<String, Object>) item)
                    .toList();
        }
        return List.of();
    }

    private void ensureConfigured() {
        if (isBlank(username) || isBlank(password) || isBlank(invoiceCode) || isBlank(callbackUrl)) {
            throw new IllegalStateException("QPay configuration is incomplete. Please set qpay.username, qpay.password, qpay.invoice-code and qpay.callback-url.");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String defaultIfBlank(String value, String fallback) {
        return isBlank(value) ? fallback : value;
    }
}
