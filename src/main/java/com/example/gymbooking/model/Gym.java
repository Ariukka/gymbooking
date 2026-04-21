// Gym.java entity-д нэмэх
package com.example.gymbooking.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "gyms")
public class Gym {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String location;

    @Column(length = 1000)
    private String description;

    @Column(length = 500)
    private String img;

    @Column(precision = 3, scale = 2)
    private BigDecimal rating;

    @Column(name = "phone")
    private String phone;

    @Column(name = "capacity", nullable = false)
    private Integer capacity;

    @ManyToOne
    @JoinColumn(name = "owner_user_id")
    @JsonIgnoreProperties({"gym", "password", "authorities"})
    private User ownerUser;

    @Column(name = "is_approved")
    private Boolean approved = false;

    // Legacy schema compatibility: some databases still keep `approved` column
    // as NOT NULL without a default, so we write both columns.
    @Column(name = "approved")
    private Boolean legacyApproved = false;

    @Column(name = "is_active")
    private Boolean active = true;

    @Column(name = "requested_at")  // Add this field
    private LocalDateTime requestedAt;

    @Column(name = "approved_at")  // Optional: add approved at
    private LocalDateTime approvedAt;

    @OneToMany(mappedBy = "gym", cascade = CascadeType.ALL)
    @JsonIgnoreProperties({"gym"})
    private List<Slot> slots;

    @OneToMany(mappedBy = "gym", cascade = CascadeType.ALL)
    @JsonIgnoreProperties({"gym"})
    private List<Booking> bookings;

    @OneToMany(mappedBy = "gym", cascade = CascadeType.ALL)
    @JsonIgnoreProperties({"gym"})
    private List<GymComment> comments;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (approved == null) {
            approved = false;
        }
        if (legacyApproved == null) {
            legacyApproved = approved;
        }
    }

    // Constructors
    public Gym() {}

    public Gym(String name, String location, User ownerUser) {
        this.name = name;
        this.location = location;
        this.ownerUser = ownerUser;
        this.requestedAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getImg() {
        return img;
    }

    public void setImg(String img) {
        this.img = img;
    }

    public BigDecimal getRating() {
        return rating;
    }

    public void setRating(BigDecimal rating) {
        this.rating = rating;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public Integer getCapacity() {
        return capacity;
    }

    public void setCapacity(Integer capacity) {
        this.capacity = capacity;
    }

    public User getOwnerUser() {
        return ownerUser;
    }

    public void setOwnerUser(User ownerUser) {
        this.ownerUser = ownerUser;
    }

    public boolean isApproved() {
        return Boolean.TRUE.equals(approved) || Boolean.TRUE.equals(legacyApproved);
    }

    public void setApproved(boolean approved) {
        this.approved = approved;
        this.legacyApproved = approved;
    }

    public boolean isActive() {
        return Boolean.TRUE.equals(active);
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public LocalDateTime getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(LocalDateTime requestedAt) {
        this.requestedAt = requestedAt;
    }

    public LocalDateTime getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(LocalDateTime approvedAt) {
        this.approvedAt = approvedAt;
    }

    public List<Slot> getSlots() {
        return slots;
    }

    public void setSlots(List<Slot> slots) {
        this.slots = slots;
    }

    public List<Booking> getBookings() {
        return bookings;
    }

    public void setBookings(List<Booking> bookings) {
        this.bookings = bookings;
    }

    public List<GymComment> getComments() {
        return comments;
    }

    public void setComments(List<GymComment> comments) {
        this.comments = comments;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
