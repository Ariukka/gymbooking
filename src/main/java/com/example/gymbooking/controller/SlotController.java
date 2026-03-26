package com.example.gymbooking.controller;

import com.example.gymbooking.model.Slot;
import com.example.gymbooking.repository.SlotRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

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
        return slotRepository.findAll();
    }

    // Get slot by ID
    @GetMapping("/{id}")
    public ResponseEntity<Slot> getSlotById(@PathVariable Long id) {
        return slotRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // Get slots by gym ID
    @GetMapping("/gym/{gymId}")
    public ResponseEntity<List<Slot>> getSlotsByGym(@PathVariable Long gymId) {
        List<Slot> slots = slotRepository.findByGymId(gymId);
        return ResponseEntity.ok(slots);
    }

    // Get slots by gym ID and date
    @GetMapping("/gym/{gymId}/date")
    public ResponseEntity<?> getSlotsByGymAndDate(@PathVariable Long gymId,
                                                  @RequestParam String date) {
        try {
            LocalDate parsedDate = LocalDate.parse(date);
            List<Slot> slots = slotRepository.findByGymIdAndDate(gymId, parsedDate);

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
        List<Slot> slots = slotRepository.findByGymIdAndAvailableTrue(gymId);
        return ResponseEntity.ok(slots);
    }

    // Get available slots by gym ID and date
    @GetMapping("/gym/{gymId}/available/date")
    public ResponseEntity<?> getAvailableSlotsByGymAndDate(@PathVariable Long gymId,
                                                           @RequestParam String date) {
        try {
            LocalDate parsedDate = LocalDate.parse(date);
            List<Slot> slots = slotRepository.findByGymIdAndDateAndAvailable(gymId, parsedDate, true);

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
}