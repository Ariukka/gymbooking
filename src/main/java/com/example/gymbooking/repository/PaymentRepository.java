package com.example.gymbooking.repository;

import com.example.gymbooking.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    // Find payment by transaction ID
    Optional<Payment> findByTransactionId(String transactionId);

    // Find payment by booking ID
    Optional<Payment> findByBookingId(Long bookingId);

    // Find payment by payment ID (if you have separate paymentId field)
    Optional<Payment> findByPaymentId(String paymentId);

    // Custom JPQL query
    @Query("SELECT p FROM Payment p WHERE p.transactionId = :transactionId")
    Optional<Payment> findPaymentByTransactionId(@Param("transactionId") String transactionId);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE UPPER(COALESCE(p.status, '')) = 'PAID'")
    BigDecimal sumPaidAmount();

    @Query("""
            SELECT COALESCE(SUM(p.amount), 0)
            FROM Payment p
            WHERE UPPER(COALESCE(p.status, '')) = 'PAID'
              AND p.paidAt >= :startDateTime
              AND p.paidAt < :endDateTime
            """)
    BigDecimal sumPaidAmountBetween(
            @Param("startDateTime") LocalDateTime startDateTime,
            @Param("endDateTime") LocalDateTime endDateTime
    );
}
