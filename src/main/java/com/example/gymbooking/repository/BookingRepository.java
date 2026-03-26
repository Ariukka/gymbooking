package com.example.gymbooking.repository;

import com.example.gymbooking.model.Booking;
import com.example.gymbooking.model.Gym;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    List<Booking> findByUser_Id(Long userId);
    List<Booking> findByUser_IdOrderByCreatedAtDesc(Long userId);
    List<Booking> findByGymId(Long gymId);
    List<Booking> findByGym(Gym gym);
    List<Booking> findByGymAndStatus(Gym gym, String status);
    List<Booking> findByGymAndApprovedTrue(Gym gym);
    boolean existsBySlot_IdAndStatusIn(Long slotId, List<String> statuses);
    List<Booking> findByGym_IdAndSlot_Date(Long gymId, LocalDate date);
    long countByGym(Gym gym);
    long countByGymAndStatus(Gym gym, String status);

    @Query("SELECT b FROM Booking b WHERE b.gym = :gym AND b.slot.date = CURRENT_DATE")
    List<Booking> findTodaysBookingsByGym(@Param("gym") Gym gym);
}