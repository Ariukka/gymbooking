// AuthService.java
package com.example.gymbooking.service;

import com.example.gymbooking.model.User;
import com.example.gymbooking.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public Optional<User> authenticate(String username, String password) {
        // Try to find by email first
        Optional<User> userOpt = userRepository.findByEmail(username);

        // If not found by email, try by phone
        if (userOpt.isEmpty()) {
            userOpt = userRepository.findByPhone(username);
        }

        // If still not found, try by username if exists
        if (userOpt.isEmpty()) {
            userOpt = userRepository.findByUsername(username);
        }

        if (userOpt.isPresent()) {
            User user = userOpt.get();
            if (passwordEncoder.matches(password, user.getPassword())) {
                return Optional.of(user);
            }
        }

        return Optional.empty();
    }

    public Optional<User> findByUsername(String username) {
        // Try to find by email first
        Optional<User> userOpt = userRepository.findByEmail(username);

        // If not found by email, try by phone
        if (userOpt.isEmpty()) {
            userOpt = userRepository.findByPhone(username);
        }

        // If still not found, try by username if exists
        if (userOpt.isEmpty()) {
            userOpt = userRepository.findByUsername(username);
        }

        return userOpt;
    }
}