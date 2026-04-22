// AuthService.java
package com.example.gymbooking.service;

import com.example.gymbooking.model.User;
import com.example.gymbooking.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public Optional<User> authenticate(String username, String password) {
        Optional<User> userOpt = findByIdentifier(username);

        if (userOpt.isPresent()) {
            User user = userOpt.get();
            if (passwordEncoder.matches(password, user.getPassword())) {
                return Optional.of(user);
            }
        }

        return Optional.empty();
    }

    public Optional<User> findByUsername(String username) {
        return findByIdentifier(username);
    }

    private Optional<User> findByIdentifier(String identifier) {
        Optional<User> userOpt = userRepository.findByEmail(identifier);

        if (userOpt.isEmpty()) {
            for (String phoneCandidate : buildPhoneCandidates(identifier)) {
                userOpt = userRepository.findByPhone(phoneCandidate);
                if (userOpt.isPresent()) {
                    return userOpt;
                }
            }
        }

        if (userOpt.isEmpty()) {
            userOpt = userRepository.findByUsername(identifier);
        }

        return userOpt;
    }

    private List<String> buildPhoneCandidates(String rawIdentifier) {
        if (rawIdentifier == null || rawIdentifier.isBlank()) {
            return List.of();
        }

        Set<String> candidates = new LinkedHashSet<>();
        candidates.add(rawIdentifier);

        String compact = rawIdentifier.replaceAll("[^\\d+]", "");
        if (!compact.isBlank()) {
            candidates.add(compact);
        }

        String digitsOnly = rawIdentifier.replaceAll("\\D", "");
        if (!digitsOnly.isBlank()) {
            candidates.add(digitsOnly);
        }

        if (digitsOnly.startsWith("976") && digitsOnly.length() > 3) {
            candidates.add(digitsOnly.substring(3));
            candidates.add("+976" + digitsOnly.substring(3));
        } else if (digitsOnly.length() == 8) {
            candidates.add("976" + digitsOnly);
            candidates.add("+976" + digitsOnly);
        }

        return new ArrayList<>(candidates);
    }
}
