package com.example.gymbooking.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import java.math.BigDecimal;
import java.time.LocalDate;

public class CreateBookingRequest {

    @JsonAlias({"slot_id", "slotID"})
    private Long slotId;
    @JsonAlias({"gym_id", "gymID"})
    private Long gymId;
    @JsonAlias({"bookingDate", "day"})
    private LocalDate date;
    @JsonAlias({"slotTime", "startTime"})
    private String time;
    @JsonAlias({"price", "amount"})
    private BigDecimal totalPrice;

    public Long getSlotId() {
        return slotId;
    }

    public void setSlotId(Long slotId) {
        this.slotId = slotId;
    }

    public Long getGymId() {
        return gymId;
    }

    public void setGymId(Long gymId) {
        this.gymId = gymId;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public BigDecimal getTotalPrice() {
        return totalPrice;
    }

    public void setTotalPrice(BigDecimal totalPrice) {
        this.totalPrice = totalPrice;
    }
}
