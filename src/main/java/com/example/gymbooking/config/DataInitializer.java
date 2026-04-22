package com.example.gymbooking.config;

import com.example.gymbooking.model.Gym;
import com.example.gymbooking.model.User;
import com.example.gymbooking.repository.GymRepository;
import com.example.gymbooking.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Component
public class DataInitializer implements CommandLineRunner {
    private static final String SYSTEM_ADMIN_PASSWORD = "Admin@123";
    private static final String DEFAULT_GYM_ADMIN_PASSWORD = "Admin@12";


    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GymRepository gymRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        // System admin user-aa uusgeh esehiig shalgach
        Optional<User> existingAdmin = userRepository.findByPhone("94671508");
        User systemAdmin;
        
        if (existingAdmin.isEmpty()) {
            // System admin user uusgeh
            systemAdmin = new User();
            systemAdmin.setFirstName("Ariunbold");
            systemAdmin.setLastName("");
            systemAdmin.setPhone("94671508");
            systemAdmin.setEmail("aaariuka02@gmail.com");
            systemAdmin.setPassword(passwordEncoder.encode(SYSTEM_ADMIN_PASSWORD));
            systemAdmin.setRole("ADMIN");
            systemAdmin.setUsername("94671508");
            systemAdmin.setVerified(true);
            
            userRepository.save(systemAdmin);
            
            System.out.println("=== SYSTEM ADMIN CREATED ===");
            System.out.println("Name: Ariunbold");
            System.out.println("Phone: 94671508");
            System.out.println("Email: aaariuka02@gmail.com");
            System.out.println("Password: " + SYSTEM_ADMIN_PASSWORD);
            System.out.println("Role: ADMIN");
            System.out.println("============================");
        } else {
            systemAdmin = existingAdmin.get();
            // Role-ig shalgaj ADMIN bolgooh
            if (!"ADMIN".equals(systemAdmin.getRole())) {
                systemAdmin.setRole("ADMIN");
                userRepository.save(systemAdmin);
                System.out.println("Updated user role to ADMIN: " + systemAdmin.getPhone());
            }
            System.out.println("System admin already exists: " + systemAdmin.getPhone());
        }

        // ODGOI GYMS-D ADMIN NEMEH
        createAdminsForExistingGyms();
        syncDefaultPasswordsForGeneratedGymAdmins();
    }

    @Transactional
    private void cleanupOldGyms() {
        // Tugs buyu zaaluudig ustgah
        List<Gym> allGyms = gymRepository.findAll();
        int deletedCount = 0;
        
        // Delete these specific gyms:
        String[] gymsToDelete = {
            "Power Fitness",
            "Elite Gym",
            "Fit Zone"
        };
        
        for (Gym gym : allGyms) {
            if (gym.getName() != null) {
                boolean shouldDelete = false;
                
                // Check if gym name matches any in the delete list
                for (String gymToDelete : gymsToDelete) {
                    if (gym.getName().equals(gymToDelete)) {
                        shouldDelete = true;
                        break;
                    }
                }
                
                if (shouldDelete) {
                    // EHLELJED GYM-TEI HOLBOOTOI USERS-IIG USTGAH
                    List<User> gymUsers = userRepository.findAll().stream()
                        .filter(user -> user.getGym() != null && user.getGym().getId().equals(gym.getId()))
                        .toList();
                    
                    for (User user : gymUsers) {
                        userRepository.delete(user);
                        System.out.println("Deleted gym user: " + user.getPhone());
                    }
                    
                    // Gym admin-ig ustgah
                    if (gym.getOwnerUser() != null) {
                        User gymAdmin = gym.getOwnerUser();
                        if ("GYM_ADMIN".equals(gymAdmin.getRole())) {
                            userRepository.delete(gymAdmin);
                            System.out.println("Deleted gym admin: " + gymAdmin.getPhone());
                        }
                    }
                    
                    // DARA NI GYM-IIG USTGAH
                    gymRepository.delete(gym);
                    System.out.println("Deleted gym: " + gym.getName());
                    deletedCount++;
                }
            }
        }
        
        if (deletedCount > 0) {
            System.out.println("=== CLEANUP COMPLETED ===");
            System.out.println("Deleted " + deletedCount + " gyms");
            System.out.println("========================");
        } else {
            System.out.println("No gyms to cleanup");
        }
    }

    private void createSampleGymWithAdmin(String gymName, String adminPhone, String adminName, String adminEmail) {
        // Gym admin user-aa shalgah
        Optional<User> existingGymAdmin = userRepository.findByPhone(adminPhone);
        User gymAdmin;
        
        if (existingGymAdmin.isEmpty()) {
            // Gym admin uusgeh
            gymAdmin = new User();
            gymAdmin.setFirstName(adminName);
            gymAdmin.setLastName("");
            gymAdmin.setPhone(adminPhone);
            gymAdmin.setEmail(adminEmail);
            gymAdmin.setPassword(passwordEncoder.encode(DEFAULT_GYM_ADMIN_PASSWORD));
            gymAdmin.setRole("GYM_ADMIN");
            gymAdmin.setUsername(adminPhone);
            gymAdmin.setVerified(true);
            
            userRepository.save(gymAdmin);
            
            System.out.println("=== GYM ADMIN CREATED ===");
            System.out.println("Name: " + adminName);
            System.out.println("Phone: " + adminPhone);
            System.out.println("Email: " + adminEmail);
            System.out.println("Password: " + DEFAULT_GYM_ADMIN_PASSWORD);
            System.out.println("Role: GYM_ADMIN");
            System.out.println("========================");
        } else {
            gymAdmin = existingGymAdmin.get();
            System.out.println("Gym admin already exists: " + gymAdmin.getPhone());
        }

        // Gym-ig shalgah
        Optional<Gym> existingGym = gymRepository.findByName(gymName);
        
        if (existingGym.isEmpty()) {
            // Gym uusgeh
            Gym gym = new Gym();
            gym.setName(gymName);
            gym.setLocation("Ulaanbaatar");
            gym.setDescription("High quality fitness center with modern equipment");
            gym.setPhone(adminPhone);
            gym.setCapacity(100);
            gym.setOwnerUser(gymAdmin);
            gym.setApproved(true);
            gym.setActive(true);
            
            gymRepository.save(gym);
            
            System.out.println("=== GYM CREATED ===");
            System.out.println("Name: " + gymName);
            System.out.println("Admin: " + adminName + " (" + adminPhone + ")");
            System.out.println("===================");
        } else {
            System.out.println("Gym already exists: " + gymName);
        }
    }

    @Transactional
    private void createAdminsForExistingGyms() {
        // Odoo bga gyms-uudiig avah
        List<Gym> existingGyms = gymRepository.findAll();
        
        for (Gym gym : existingGyms) {
            // Herew gym-d admin baigaa esehiig shalgah
            if (gym.getOwnerUser() == null) {
                // Gym admin uusgeh
                User gymAdmin = new User();
                gymAdmin.setFirstName(gym.getName() + " Admin");
                gymAdmin.setLastName("");
                gymAdmin.setPhone("9" + String.format("%07d", gym.getId())); // 9 + gym_id
                gymAdmin.setEmail("admin" + gym.getId() + "@gym.com");
                gymAdmin.setPassword(passwordEncoder.encode(DEFAULT_GYM_ADMIN_PASSWORD));
                gymAdmin.setRole("GYM_ADMIN");
                gymAdmin.setUsername("admin" + gym.getId());
                gymAdmin.setVerified(true);
                
                // Admin-iig hadgalah
                userRepository.save(gymAdmin);
                
                // Gym-d admin-iig tohiroh
                gym.setOwnerUser(gymAdmin);
                gymRepository.save(gym);
                
                System.out.println("=== GYM ADMIN CREATED ===");
                System.out.println("Gym: " + gym.getName());
                System.out.println("Admin: " + gymAdmin.getFirstName());
                System.out.println("Phone: " + gymAdmin.getPhone());
                System.out.println("Email: " + gymAdmin.getEmail());
                System.out.println("Password: " + DEFAULT_GYM_ADMIN_PASSWORD);
                System.out.println("========================");
            } else {
                System.out.println("Gym already has admin: " + gym.getName());
            }
        }
    }

    @Transactional
    private void syncDefaultPasswordsForGeneratedGymAdmins() {
        List<User> gymAdmins = userRepository.findByRole("GYM_ADMIN");

        for (User gymAdmin : gymAdmins) {
            String username = gymAdmin.getUsername() != null ? gymAdmin.getUsername() : "";
            String email = gymAdmin.getEmail() != null ? gymAdmin.getEmail() : "";
            boolean generatedAdmin =
                    username.matches("^admin\\d+$") || email.matches("^admin\\d+@gym\\.com$");

            if (!generatedAdmin) {
                continue;
            }

            if (!passwordEncoder.matches(DEFAULT_GYM_ADMIN_PASSWORD, gymAdmin.getPassword())) {
                gymAdmin.setPassword(passwordEncoder.encode(DEFAULT_GYM_ADMIN_PASSWORD));
                userRepository.save(gymAdmin);
                System.out.println("Reset default password for gym admin: " + gymAdmin.getEmail());
            }
        }
    }
}
