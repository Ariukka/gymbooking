package com.example.gymbooking.repository;

import com.example.gymbooking.model.GymComment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GymCommentRepository extends JpaRepository<GymComment, Long> {
    List<GymComment> findByGymIdOrderByCreatedAtDesc(Long gymId);
    Optional<GymComment> findByIdAndGymId(Long id, Long gymId);
}
