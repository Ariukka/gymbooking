package com.example.gymbooking.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DatabaseMaintenanceService {

    private static final Logger log = LoggerFactory.getLogger(DatabaseMaintenanceService.class);

    private static final List<String> TABLES_WITH_ID = List.of(
            "users",
            "gyms",
            "slots",
            "bookings",
            "payments",
            "members",
            "notifications",
            "gym_comments",
            "audit_logs",
            "otp_codes"
    );

    private final JdbcTemplate jdbcTemplate;

    public DatabaseMaintenanceService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Ensures AUTO_INCREMENT starts from max(id)+1 on known tables.
     * This prevents collisions when legacy/manual inserts changed id values.
     */
    public void fixAutoIncrementSequences() {
        for (String tableName : TABLES_WITH_ID) {
            try {
                Long maxId = jdbcTemplate.queryForObject(
                        "SELECT COALESCE(MAX(id), 0) FROM " + tableName,
                        Long.class
                );

                long nextId = (maxId == null ? 1L : maxId + 1L);
                jdbcTemplate.execute("ALTER TABLE " + tableName + " AUTO_INCREMENT = " + nextId);

                log.info("Auto increment synced for table {} -> {}", tableName, nextId);
            } catch (Exception ex) {
                log.warn("Skipping auto increment sync for table {}: {}", tableName, ex.getMessage());
            }
        }
    }

    public JdbcTemplate getJdbcTemplate() {
        return jdbcTemplate;
    }
}
