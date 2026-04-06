package com.example.gymbooking.repository;

import com.example.gymbooking.model.Gym;
import com.example.gymbooking.model.Slot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SlotRepository extends JpaRepository<Slot, Long> {
    List<Slot> findByGymId(Long gymId);
    List<Slot> findByGym(Gym gym);
    List<Slot> findByGymAndDate(Gym gym, LocalDate date);
    List<Slot> findByGymIdAndDate(Long gymId, LocalDate date);
    Optional<Slot> findByGymIdAndDateAndTime(Long gymId, LocalDate date, String time);
    List<Slot> findByGymAndAvailableTrue(Gym gym);
    List<Slot> findByGymIdAndAvailableTrue(Long gymId);
    long countByGym(Gym gym);
    long countByGymAndAvailableTrue(Gym gym);
    boolean existsByGymAndDateAndTime(Gym gym, LocalDate date, String time);
    List<Slot> findByGymIdAndDateAndAvailable(Long gymId, LocalDate date, boolean available);

    @Query("SELECT s FROM Slot s WHERE s.gym.id = :gymId AND s.date = :date AND s.available = true")
    List<Slot> findAvailableSlotsByGymIdAndDate(@Param("gymId") Long gymId, @Param("date") LocalDate date);
}
