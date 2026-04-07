package com.example.gymbooking.config;

import com.example.gymbooking.model.Gym;
import com.example.gymbooking.model.User;
import com.example.gymbooking.repository.UserRepository;
import com.example.gymbooking.service.DatabaseMaintenanceService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;

@Component
public class GymAdminSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final DatabaseMaintenanceService databaseMaintenanceService;

    @PersistenceContext
    private EntityManager entityManager;

    public GymAdminSeeder(UserRepository userRepository,
                          PasswordEncoder passwordEncoder,
                          DatabaseMaintenanceService databaseMaintenanceService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.databaseMaintenanceService = databaseMaintenanceService;
    }

    @Override
    public void run(String... args) {
        try {
            databaseMaintenanceService.fixAutoIncrementSequences();

            // NOTE: We intentionally fetch only IDs to avoid issues if some gyms contain invalid data
            // in other columns (e.g., malformed decimal strings in hourly_price).
            List<Long> gymIds = entityManager.createNativeQuery("select id from gyms", Long.class).getResultList();
            for (Long gymId : gymIds) {
                if (gymId == null) continue;

                boolean alreadyHasAdmin = !userRepository
                        .findByGym_IdAndRoleIn(gymId, List.of("GYM_ADMIN", "ROLE_GYM_ADMIN"))
                        .isEmpty();

                if (alreadyHasAdmin) {
                    continue;
                }

                String username = "gymadmin_" + gymId;
                if (userRepository.findByUsername(username).isPresent()) {
                    // If username exists but not attached as gym admin, skip to avoid duplicates
                    continue;
                }

                User admin = new User();
                admin.setUsername(username);
                admin.setPassword(passwordEncoder.encode("admin1234"));
                admin.setRole("GYM_ADMIN");
                admin.setVerified(true);
                admin.setGym(entityManager.getReference(Gym.class, gymId));
                admin.setFirstName("Gym");
                admin.setLastName("Admin");
                admin.setEmail("gymadmin_" + gymId + "@example.com");
                admin.setPhone("8000" + String.format("%04d", gymId % 10000));

                userRepository.save(admin);
            }
        } catch (Exception e) {
            System.err.println("GymAdminSeeder failed: " + e.getMessage());
            // Continue startup even if seeder fails
        }
    }
}
