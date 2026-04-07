package com.example.gymbooking.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.AssertTrue;

import java.math.BigDecimal;

public class CreatePaymentRequest {

    @JsonAlias({"bookingId", "booking_id"})
    private Long bookingId;

    @JsonAlias({"amount", "totalPrice", "total_price", "price"})
    private BigDecimal amount;

    @JsonAlias({"paymentMethod", "payment_method"})
    private String paymentMethod;

    @JsonAlias({"userId", "user_id"})
    private Long userId;

    @AssertTrue(message = "bookingId is required")
    public boolean hasBookingReference() {
        return bookingId != null;
    }

    public Long resolveBookingId() {
        return bookingId;
    }

    public Long getBookingId() {
        return bookingId;
    }

    public void setBookingId(Long bookingId) {
        this.bookingId = bookingId;
    }

    @JsonSetter("booking")
    public void setBookingFromJson(JsonNode bookingNode) {
        if (bookingNode == null || bookingNode.isNull()) {
            return;
        }

        if (bookingNode.isNumber() || bookingNode.isTextual()) {
            try {
                this.bookingId = Long.parseLong(bookingNode.asText());
            } catch (NumberFormatException ignored) {
                // Leave bookingId unchanged so validation can return a clean error.
            }
            return;
        }

        if (bookingNode.isObject()) {
            JsonNode idNode = bookingNode.get("id");
            if (idNode != null && !idNode.isNull()) {
                try {
                    this.bookingId = Long.parseLong(idNode.asText());
                } catch (NumberFormatException ignored) {
                    // Leave bookingId unchanged so validation can return a clean error.
                }
            }
        }
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

}
