package com.example.gymbooking.repository;

import com.example.gymbooking.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    // Find by email
    Optional<User> findByEmail(String email);

    // Find by phone
    Optional<User> findByPhone(String phone);

    // Find by username (if you have username field)
    Optional<User> findByUsername(String username);

    // Check existence
    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);

    // Find by role
    List<User> findByRole(String role);

    // Count by role
    long countByRole(String role);

    // Count verified users
    @Query("SELECT COUNT(u) FROM User u WHERE u.verified = true")
    long countByVerifiedTrue();

    // Find by gym ID and roles
    List<User> findByGym_IdAndRoleIn(Long gymId, List<String> roles);

    // Find by gym ID
    List<User> findByGym_Id(Long gymId);

    // Search users by name
    List<User> findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCase(String firstName, String lastName);

    // Find users with their gyms (JPQL)
    @Query("SELECT u FROM User u LEFT JOIN FETCH u.gym WHERE u.id = :userId")
    Optional<User> findUserWithGym(@Param("userId") Long userId);
}