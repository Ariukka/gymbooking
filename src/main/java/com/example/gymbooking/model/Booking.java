package com.example.gymbooking.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "bookings")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id")
    @JsonIgnoreProperties({"password", "authorities", "gym"})
    private User user;

    @ManyToOne
    @JoinColumn(name = "gym_id")
    @JsonIgnoreProperties({"ownerUser", "slots", "bookings", "comments"})
    private Gym gym;

    @ManyToOne
    @JoinColumn(name = "slot_id")
    @JsonIgnoreProperties({"gym"})
    private Slot slot;

    @Column(name = "total_price", precision = 12, scale = 2)
    private BigDecimal totalPrice;

    private String status; // PENDING, CONFIRMED, CANCELLED, REJECTED

    @Column(name = "is_approved")
    private boolean approved = false;

    @OneToOne(mappedBy = "booking", cascade = CascadeType.ALL)
    @JsonIgnoreProperties({"booking"})
    private Payment payment;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "confirmed_at")  // Add this field
    private LocalDateTime confirmedAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = "PENDING";
        }
    }

    // Constructors
    public Booking() {}

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getEmail() {
        return user != null ? user.getEmail() : null;
    }

    public LocalDate getDate() {
        return slot != null ? slot.getDate() : null;
    }

    public String getTime() {
        return slot != null ? slot.getTime() : null;
    }

    public Gym getGym() {
        return gym;
    }

    public void setGym(Gym gym) {
        this.gym = gym;
    }

    public Slot getSlot() {
        return slot;
    }

    public void setSlot(Slot slot) {
        this.slot = slot;
    }

    public BigDecimal getTotalPrice() {
        return totalPrice;
    }

    public void setTotalPrice(BigDecimal totalPrice) {
        this.totalPrice = totalPrice;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isApproved() {
        return approved;
    }

    public void setApproved(boolean approved) {
        this.approved = approved;
    }

    public Payment getPayment() {
        return payment;
    }

    public void setPayment(Payment payment) {
        this.payment = payment;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getConfirmedAt() {
        return confirmedAt;
    }

    public void setConfirmedAt(LocalDateTime confirmedAt) {
        this.confirmedAt = confirmedAt;
    }
}
