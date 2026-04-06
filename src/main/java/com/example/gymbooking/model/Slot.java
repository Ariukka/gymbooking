package com.example.gymbooking.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

@Entity
@Table(name = "slots")
public class Slot {

    private static final List<DateTimeFormatter> SUPPORTED_TIME_FORMATS = List.of(
            DateTimeFormatter.ofPattern("H:mm"),
            DateTimeFormatter.ofPattern("H:mm:ss"),
            DateTimeFormatter.ISO_LOCAL_TIME
    );

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "gym_id", nullable = false)
    private Gym gym;

    @Column(name = "slot_date", nullable = false, columnDefinition = "DATE")
    private LocalDate date;

    @Column(name = "slot_time", nullable = false)
    private String time;

    @Column(precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "is_available")
    private boolean available = true;

    @Column(name = "max_capacity")
    private Integer maxCapacity = 1;

    @Column(name = "current_bookings")
    private Integer currentBookings = 0;

    // Constructors
    public Slot() {}

    public Slot(Gym gym, LocalDate date, String time, BigDecimal price) {
        this.gym = gym;
        this.date = date;
        this.time = time;
        this.price = price;
        this.available = true;
        this.maxCapacity = 1;
        this.currentBookings = 0;
    }

    // Check if slot has capacity
    public boolean hasCapacity() {
        return currentBookings != null && maxCapacity != null && currentBookings < maxCapacity;
    }

    // Increment bookings
    public void incrementBookings() {
        if (currentBookings == null) {
            currentBookings = 0;
        }
        currentBookings++;
        if (currentBookings >= maxCapacity) {
            available = false;
        }
    }

    // Decrement bookings
    public void decrementBookings() {
        if (currentBookings != null && currentBookings > 0) {
            currentBookings--;
            if (currentBookings < maxCapacity) {
                available = true;
            }
        }
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Gym getGym() {
        return gym;
    }

    public void setGym(Gym gym) {
        this.gym = gym;
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
        this.time = normalizeTime(time);
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public Integer getMaxCapacity() {
        return maxCapacity;
    }

    public void setMaxCapacity(Integer maxCapacity) {
        this.maxCapacity = maxCapacity;
    }

    public Integer getCurrentBookings() {
        return currentBookings;
    }

    public void setCurrentBookings(Integer currentBookings) {
        this.currentBookings = currentBookings;
    }

    @PrePersist
    @PreUpdate
    private void normalizeFields() {
        this.time = normalizeTime(this.time);
    }

    private static String normalizeTime(String time) {
        if (time == null) {
            return null;
        }

        String candidate = time.trim();
        if (candidate.isEmpty()) {
            return candidate;
        }

        for (DateTimeFormatter formatter : SUPPORTED_TIME_FORMATS) {
            try {
                return LocalTime.parse(candidate, formatter).format(DateTimeFormatter.ofPattern("HH:mm"));
            } catch (DateTimeParseException ignored) {
                // try next format
            }
        }

        return candidate;
    }
}
