package com.example.gymbooking.controller;

import com.example.gymbooking.model.AuditLog;
import com.example.gymbooking.service.AuditLogService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping({"/api/admin/security", "/admin/security"})
@CrossOrigin(origins = "http://localhost:3000")
public class SecurityAuditController {

    private final AuditLogService auditLogService;

    public SecurityAuditController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping("/audit-logs")
    public List<AuditLog> getAuditLogs(@RequestParam(defaultValue = "100") int limit) {
        List<AuditLog> logs = auditLogService.getAllLogs();
        if (limit <= 0) {
            return logs;
        }
        return logs.stream()
                .limit(Math.max(0, limit))
                .collect(Collectors.toList());
    }
}
