package com.example.gymbooking.controller;

import com.example.gymbooking.model.Slot;
import com.example.gymbooking.repository.SlotRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/slots")
@CrossOrigin(origins = "http://localhost:3000")
public class SlotController {

    private final SlotRepository slotRepository;

    public SlotController(SlotRepository slotRepository) {
        this.slotRepository = slotRepository;
    }

    // Get all slots
    @GetMapping
    public List<Slot> getAllSlots() {
        return slotRepository.findAll().stream()
                .map(this::toResponseSlot)
                .collect(Collectors.toList());
    }

    // Get slot by ID
    @GetMapping("/{id}")
    public ResponseEntity<Slot> getSlotById(@PathVariable Long id) {
        return slotRepository.findById(id)
                .map(this::toResponseSlot)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // Get slots by gym ID
    @GetMapping("/gym/{gymId}")
    public ResponseEntity<List<Slot>> getSlotsByGym(@PathVariable Long gymId) {
        List<Slot> slots = slotRepository.findByGymId(gymId).stream()
                .map(this::toResponseSlot)
                .collect(Collectors.toList());
        return ResponseEntity.ok(slots);
    }

    // Get slots by gym ID and date
    @GetMapping("/gym/{gymId}/date")
    public ResponseEntity<?> getSlotsByGymAndDate(@PathVariable Long gymId,
                                                  @RequestParam String date) {
        try {
            LocalDate parsedDate = LocalDate.parse(date);
            List<Slot> slots = slotRepository.findByGymIdAndDate(gymId, parsedDate).stream()
                    .map(this::toResponseSlot)
                    .collect(Collectors.toList());

            Map<String, Object> response = Map.of(
                    "gymId", gymId,
                    "date", parsedDate,
                    "slots", slots,
                    "total", slots.size()
            );

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid date format"));
        }
    }

    // Get available slots by gym ID
    @GetMapping("/gym/{gymId}/available")
    public ResponseEntity<List<Slot>> getAvailableSlotsByGym(@PathVariable Long gymId) {
        List<Slot> slots = slotRepository.findByGymIdAndAvailableTrue(gymId).stream()
                .map(this::toResponseSlot)
                .filter(Slot::isAvailable)
                .collect(Collectors.toList());
        return ResponseEntity.ok(slots);
    }

    // Get available slots by gym ID and date
    @GetMapping("/gym/{gymId}/available/date")
    public ResponseEntity<?> getAvailableSlotsByGymAndDate(@PathVariable Long gymId,
                                                           @RequestParam String date) {
        try {
            LocalDate parsedDate = LocalDate.parse(date);
            List<Slot> slots = slotRepository.findByGymIdAndDateAndAvailable(gymId, parsedDate, true).stream()
                    .map(this::toResponseSlot)
                    .filter(Slot::isAvailable)
                    .collect(Collectors.toList());

            Map<String, Object> response = Map.of(
                    "gymId", gymId,
                    "date", parsedDate,
                    "slots", slots,
                    "total", slots.size()
            );

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid date format"));
        }
    }

    // Create new slot
    @PostMapping
    public ResponseEntity<Slot> createSlot(@RequestBody Slot slot) {
        slot.setAvailable(true);
        Slot savedSlot = slotRepository.save(slot);
        return ResponseEntity.ok(savedSlot);
    }

    // Update slot
    @PutMapping("/{id}")
    public ResponseEntity<?> updateSlot(@PathVariable Long id,
                                        @RequestBody Slot updatedSlot) {
        Slot slot = slotRepository.findById(id).orElse(null);

        if (slot == null) {
            return ResponseEntity.notFound().build();
        }

        // Update fields
        if (updatedSlot.getDate() != null) {
            slot.setDate(updatedSlot.getDate());
        }
        if (updatedSlot.getTime() != null) {
            slot.setTime(updatedSlot.getTime());
        }
        if (updatedSlot.getPrice() != null) {
            slot.setPrice(updatedSlot.getPrice());
        }
        if (updatedSlot.getMaxCapacity() != null) {
            slot.setMaxCapacity(updatedSlot.getMaxCapacity());
        }
        if (updatedSlot.getGym() != null) {
            slot.setGym(updatedSlot.getGym());
        }

        Slot savedSlot = slotRepository.save(slot);
        return ResponseEntity.ok(savedSlot);
    }

    // Update slot availability
    @PatchMapping("/{id}/availability")
    public ResponseEntity<?> updateSlotAvailability(@PathVariable Long id,
                                                    @RequestBody Map<String, Boolean> request) {
        Slot slot = slotRepository.findById(id).orElse(null);

        if (slot == null) {
            return ResponseEntity.notFound().build();
        }

        Boolean available = request.get("available");
        if (available != null) {
            slot.setAvailable(available);
            slotRepository.save(slot);
        }

        return ResponseEntity.ok(Map.of(
                "id", slot.getId(),
                "available", slot.isAvailable()
        ));
    }

    // Delete slot
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteSlot(@PathVariable Long id) {
        Slot slot = slotRepository.findById(id).orElse(null);

        if (slot == null) {
            return ResponseEntity.notFound().build();
        }

        slotRepository.delete(slot);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Slot deleted successfully",
                "id", id
        ));
    }

    private Slot toResponseSlot(Slot originalSlot) {
        Slot responseSlot = new Slot();
        responseSlot.setId(originalSlot.getId());
        responseSlot.setGym(originalSlot.getGym());
        responseSlot.setDate(originalSlot.getDate());
        responseSlot.setTime(originalSlot.getTime());
        responseSlot.setPrice(originalSlot.getPrice());
        responseSlot.setMaxCapacity(originalSlot.getMaxCapacity());
        responseSlot.setCurrentBookings(originalSlot.getCurrentBookings());
        responseSlot.setAvailable(originalSlot.isAvailable() && !isPastSlot(originalSlot));
        return responseSlot;
    }

    private boolean isPastSlot(Slot slot) {
        if (slot == null || slot.getDate() == null) {
            return false;
        }

        LocalDateTime now = LocalDateTime.now();
        if (slot.getDate().isBefore(now.toLocalDate())) {
            return true;
        }

        if (!slot.getDate().isEqual(now.toLocalDate())) {
            return false;
        }

        LocalTime slotStartTime = extractStartTime(slot.getTime());
        if (slotStartTime == null) {
            return false;
        }

        return !slotStartTime.isAfter(now.toLocalTime());
    }

    private LocalTime extractStartTime(String slotTimeRange) {
        if (slotTimeRange == null || slotTimeRange.isBlank()) {
            return null;
        }

        String startTime = slotTimeRange.contains("-")
                ? slotTimeRange.split("-", 2)[0].trim()
                : slotTimeRange.trim();

        try {
            return LocalTime.parse(startTime, DateTimeFormatter.ofPattern("H:mm"));
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }
}
