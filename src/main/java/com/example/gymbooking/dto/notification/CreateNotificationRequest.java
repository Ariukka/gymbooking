package com.example.gymbooking.dto.notification;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class CreateNotificationRequest {

    @NotNull(message = "userId is required")
    private Long userId;

    @NotBlank(message = "title is required")
    @Size(max = 255, message = "title must be at most 255 characters")
    private String title;

    @NotBlank(message = "message is required")
    @Size(max = 5000, message = "message must be at most 5000 characters")
    private String message;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
