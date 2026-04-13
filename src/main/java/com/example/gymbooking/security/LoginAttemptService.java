package com.example.gymbooking.security;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LoginAttemptService {

    private static final int MAX_ATTEMPTS = 5;
    private static final Duration BLOCK_DURATION = Duration.ofMinutes(15);
    private static final Duration RESET_WINDOW = Duration.ofMinutes(30);

    private final Map<String, LoginAttempt> attempts = new ConcurrentHashMap<>();

    public boolean isBlocked(String key) {
        if (key == null) {
            return false;
        }

        LoginAttempt attempt = attempts.get(key);
        if (attempt == null) {
            return false;
        }

        if (attempt.blockedUntil != null) {
            if (Instant.now().isBefore(attempt.blockedUntil)) {
                return true;
            } else {
                attempts.remove(key);
                return false;
            }
        }

        if (attempt.lastAttempt != null &&
                Duration.between(attempt.lastAttempt, Instant.now()).compareTo(RESET_WINDOW) > 0) {
            attempts.remove(key);
            return false;
        }

        return false;
    }

    public boolean loginFailed(String key) {
        if (key == null) {
            return false;
        }

        Instant now = Instant.now();
        LoginAttempt attempt = attempts.computeIfAbsent(key, k -> new LoginAttempt());

        synchronized (attempt) {
            if (attempt.lastAttempt != null &&
                    Duration.between(attempt.lastAttempt, now).compareTo(RESET_WINDOW) > 0) {
                attempt.count = 0;
            }

            attempt.count++;
            attempt.lastAttempt = now;

            if (attempt.blockedUntil != null && now.isBefore(attempt.blockedUntil)) {
                return true;
            }

            if (attempt.count >= MAX_ATTEMPTS) {
                attempt.blockedUntil = now.plus(BLOCK_DURATION);
                return true;
            }
        }

        return false;
    }

    public void loginSucceeded(String key) {
        if (key != null) {
            attempts.remove(key);
        }
    }

    public long getBlockDurationMinutes() {
        return BLOCK_DURATION.toMinutes();
    }

    private static class LoginAttempt {
        private int count = 0;
        private Instant lastAttempt;
        private Instant blockedUntil;
    }
}
