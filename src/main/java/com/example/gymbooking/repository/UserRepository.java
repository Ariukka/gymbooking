package com.example.gymbooking.repository;

import com.example.gymbooking.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    // Find by email
    Optional<User> findByEmail(String email);

    // Find by phone
    Optional<User> findByPhone(String phone);

    @Query("SELECT u FROM User u WHERE TRIM(u.phone) = :phone")
    Optional<User> findByPhoneTrimmed(@Param("phone") String phone);

    // Find by username (if you have username field)
    Optional<User> findByUsername(String username);

    @Query("SELECT u FROM User u WHERE LOWER(TRIM(u.email)) = LOWER(:email)")
    Optional<User> findByEmailNormalized(@Param("email") String email);

    @Query("SELECT u FROM User u WHERE LOWER(TRIM(u.username)) = LOWER(:username)")
    Optional<User> findByUsernameNormalized(@Param("username") String username);

    // Check existence
    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);

    boolean existsByUsername(String username);

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

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE User u SET u.gym = null WHERE u.gym IS NOT NULL")
    int clearGymReferences();
}
