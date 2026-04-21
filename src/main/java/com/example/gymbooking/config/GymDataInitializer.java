package com.example.gymbooking.config;

import com.example.gymbooking.model.Gym;
import com.example.gymbooking.model.User;
import com.example.gymbooking.repository.GymRepository;
import com.example.gymbooking.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Configuration
public class GymDataInitializer {

    @Bean
    public CommandLineRunner initGymData(GymRepository gymRepository, 
                                       UserRepository userRepository,
                                       PasswordEncoder passwordEncoder) {
        return args -> {
            System.out.println("=== Initializing Gym Data from Image ===");
            
            // Create admin user
            if (!userRepository.existsByUsername("admin")) {
                User admin = new User();
                admin.setUsername("admin");
                admin.setEmail("admin@gym.com");
                admin.setPassword(passwordEncoder.encode("admin123"));
                admin.setFirstName("Admin");
                admin.setLastName("User");
                admin.setRole("ADMIN");
                admin.setVerified(true);
                userRepository.save(admin);
                System.out.println("Created admin user: admin/admin123");
            }

            // Create gyms from image data
            User adminUser = userRepository.findByUsername("admin").orElse(null);
            if (adminUser != null) {
                // Gym 1: Power Gym
                createGym(gymRepository, adminUser, 
                    "Power Gym", 
                    "https://images.unsplash.com/photo-1571902943202-507ec2618e8f?auto=format&fit=crop&w=800&q=80",
                    "Ulaanbaatar", 
                    new BigDecimal("4.8"),
                    "Modern fitness center with state-of-the-art equipment and professional trainers");

                // Gym 2: FitLife Center  
                createGym(gymRepository, adminUser,
                    "FitLife Center",
                    "https://images.unsplash.com/photo-1534438327276-14e5300c3a48?auto=format&fit=crop&w=800&q=80",
                    "Ulaanbaatar",
                    new BigDecimal("4.6"), 
                    "Premium fitness facility with swimming pool, sauna, and group classes");

                // Gym 3: Iron House Gym
                createGym(gymRepository, adminUser,
                    "Iron House Gym",
                    "https://images.unsplash.com/photo-1550345332-09e3ac987658?auto=format&fit=crop&w=800&q=80", 
                    "Ulaanbaatar",
                    new BigDecimal("4.7"),
                    "Hardcore gym focused on strength training and bodybuilding");

                // Gym 4: Yoga Studio
                createGym(gymRepository, adminUser,
                    "Yoga Studio",
                    "https://images.unsplash.com/photo-1506126613408-eca07ce68773?auto=format&fit=crop&w=800&q=80",
                    "Ulaanbaatar", 
                    new BigDecimal("4.9"),
                    "Peaceful yoga studio offering various yoga styles and meditation classes");

                // Gym 5: CrossFit Box
                createGym(gymRepository, adminUser,
                    "CrossFit Box", 
                    "https://images.unsplash.com/photo-1571019613454-1cb2f99b2d8b?auto=format&fit=crop&w=800&q=80",
                    "Ulaanbaatar",
                    new BigDecimal("4.5"),
                    "High-intensity functional fitness training with certified coaches");

                // Gym 6: Spin Studio
                createGym(gymRepository, adminUser,
                    "Spin Studio",
                    "https://images.unsplash.com/photo-1540555700478-4be289fbecef?auto=format&fit=crop&w=800&q=80",
                    "Ulaanbaatar",
                    new BigDecimal("4.4"), 
                    "Indoor cycling studio with energetic music and motivating instructors");
            }
            
            System.out.println("=== Gym Data Initialization Complete ===");
        };
    }

    private void createGym(GymRepository gymRepository, User adminUser, 
                         String name, String img, String location, 
                         BigDecimal rating, String description) {
        if (!gymRepository.existsByName(name)) {
            Gym gym = new Gym();
            gym.setName(name);
            gym.setImg(img);
            gym.setLocation(location);
            gym.setRating(rating);
            gym.setDescription(description);
            gym.setPhone("+976-9999-8888");
            gym.setCapacity(50);
            gym.setOwnerUser(adminUser);
            gym.setApproved(true);
            gym.setActive(true);
            gym.setRequestedAt(LocalDateTime.now());
            gymRepository.save(gym);
            System.out.println("Created gym: " + name + " (ID: " + gym.getId() + ")");
        }
    }
}
