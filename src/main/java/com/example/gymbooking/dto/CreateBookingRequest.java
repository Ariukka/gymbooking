package com.example.gymbooking.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.math.BigDecimal;
import java.time.LocalDate;

public class CreateBookingRequest {

    @JsonAlias({"slot_id", "slotID", "slotId"})
    private Long slotId;
    @JsonAlias({"gym_id", "gymID", "gymId"})
    private Long gymId;
    @JsonAlias({"bookingDate", "day", "date"})
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate date;
    @JsonAlias({"slotTime", "startTime", "time"})
    private String time;
    @JsonAlias({"endTime", "toTime"})
    private String endTime;
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
    
    // Custom setter to handle string date conversion
    @JsonIgnore
    public void setDateAsString(String dateString) {
        if (dateString != null && !dateString.trim().isEmpty()) {
            try {
                this.date = LocalDate.parse(dateString.trim());
            } catch (Exception e) {
                System.err.println("Failed to parse date: " + dateString + ", error: " + e.getMessage());
                // Keep date as null if parsing fails
            }
        }
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public String getEndTime() {
        return endTime;
    }

    public void setEndTime(String endTime) {
        this.endTime = endTime;
    }

    public BigDecimal getTotalPrice() {
        return totalPrice;
    }

    public void setTotalPrice(BigDecimal totalPrice) {
        this.totalPrice = totalPrice;
    }
}
