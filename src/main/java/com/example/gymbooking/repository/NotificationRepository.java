package com.example.gymbooking.repository;

import com.example.gymbooking.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;


import java.time.LocalDateTime;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // findByUserId
    List<Notification> findByUserId(Long userId);

    // findByUserIdOrderByCreatedAtDesc
    List<Notification> findByUserIdOrderByCreatedAtDesc(Long userId);

    // findByUserIdAndRead
    List<Notification> findByUserIdAndRead(Long userId, boolean read);

    // findByUserIdAndReadOrderByCreatedAtDesc
    List<Notification> findByUserIdAndReadOrderByCreatedAtDesc(Long userId, boolean read);

    // findByUserIdAndCreatedAtBefore
    List<Notification> findByUserIdAndCreatedAtBefore(Long userId, LocalDateTime dateTime);

    // findByUserIdAndCreatedAtAfter
    List<Notification> findByUserIdAndCreatedAtAfter(Long userId, LocalDateTime dateTime);

    // countByUserIdAndRead
    long countByUserIdAndRead(Long userId, boolean read);

    // countByUserId
    long countByUserId(Long userId);

    // countByUserIdAndReadFalse
    long countByUserIdAndReadFalse(Long userId);

    // countByUser_IdAndReadFalse
    long countByUser_IdAndReadFalse(Long userId);

    // deleteByUserId
    void deleteByUserId(Long userId);

    // findByUser_IdOrderByCreatedAtDesc
    List<Notification> findByUser_IdOrderByCreatedAtDesc(Long userId);

    // findByUser_IdAndReadFalseOrderByCreatedAtDesc
    List<Notification> findByUser_IdAndReadFalseOrderByCreatedAtDesc(Long userId);

    // deleteByUser_Id
    void deleteByUser_Id(Long userId);
}