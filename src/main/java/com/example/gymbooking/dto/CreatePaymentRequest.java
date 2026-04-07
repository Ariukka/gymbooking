package com.example.gymbooking.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public class CreatePaymentRequest {

    @JsonAlias({"bookingId", "booking_id"})
    private Long bookingId;

    @JsonAlias("booking")
    private BookingReference booking;

    @NotNull(message = "amount is required")
    @DecimalMin(value = "0.01", message = "amount must be greater than 0")
    private BigDecimal amount;

    @JsonAlias({"paymentMethod", "payment_method"})
    private String paymentMethod;

    @JsonAlias({"userId", "user_id"})
    private Long userId;

    @AssertTrue(message = "bookingId is required")
    public boolean hasBookingReference() {
        return bookingId != null || (booking != null && booking.getId() != null);
    }

    public Long resolveBookingId() {
        if (bookingId != null) {
            return bookingId;
        }
        return booking != null ? booking.getId() : null;
    }

    public Long getBookingId() {
        return bookingId;
    }

    public void setBookingId(Long bookingId) {
        this.bookingId = bookingId;
    }

    public BookingReference getBooking() {
        return booking;
    }

    public void setBooking(BookingReference booking) {
        this.booking = booking;
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

    public static class BookingReference {
        private Long id;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }
    }
}
