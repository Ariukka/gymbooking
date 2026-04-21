package com.example.gymbooking.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonSetter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

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
    
    @JsonSetter("date")
    public void setDateFromString(String dateString) {
        this.date = parseFlexibleDate(dateString);
    }

    @JsonSetter("bookingDate")
    public void setBookingDateFromString(String dateString) {
        this.date = parseFlexibleDate(dateString);
    }

    @JsonSetter("day")
    public void setDayFromString(String dateString) {
        this.date = parseFlexibleDate(dateString);
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

    private LocalDate parseFlexibleDate(String dateString) {
        if (dateString == null || dateString.trim().isEmpty()) {
            return null;
        }

        String value = dateString.trim();

        try {
            return LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException ignored) {
        }

        try {
            return OffsetDateTime.parse(value, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toLocalDate();
        } catch (DateTimeParseException ignored) {
        }

        if (value.contains("T")) {
            try {
                return LocalDate.parse(value.substring(0, value.indexOf('T')), DateTimeFormatter.ISO_LOCAL_DATE);
            } catch (DateTimeParseException ignored) {
            }
        }

        try {
            return LocalDate.parse(value, DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }
}
