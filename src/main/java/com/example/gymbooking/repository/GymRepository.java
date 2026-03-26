package com.example.gymbooking.repository;

import com.example.gymbooking.model.Gym;
import com.example.gymbooking.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface GymRepository extends JpaRepository<Gym, Long> {
    List<Gym> findByOwnerUser(User ownerUser);
    Optional<Gym> findFirstByOwnerUser(User ownerUser);
    List<Gym> findByApprovedTrue();
    List<Gym> findByApprovedTrueAndActiveTrue();
    List<Gym> findByApprovedFalse();
    List<Gym> findByApproved(boolean approved);
    long countByApprovedTrue();
    long countByApprovedFalse();
    boolean existsByOwnerUser(User ownerUser);
}