package com.example.gymbooking.service;

import com.example.gymbooking.config.GymSeedData;
import com.example.gymbooking.model.Gym;
import com.example.gymbooking.repository.BookingRepository;
import com.example.gymbooking.repository.GymCommentRepository;
import com.example.gymbooking.repository.GymRepository;
import com.example.gymbooking.repository.SlotRepository;
import com.example.gymbooking.repository.UserRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
public class GymDataService {

    private static final Logger LOGGER = LoggerFactory.getLogger(GymDataService.class);

    private final GymRepository gymRepository;
    private final SlotRepository slotRepository;
    private final BookingRepository bookingRepository;
    private final GymCommentRepository gymCommentRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public GymDataService(GymRepository gymRepository,
                          SlotRepository slotRepository,
                          BookingRepository bookingRepository,
                          GymCommentRepository gymCommentRepository,
                          UserRepository userRepository,
                          ObjectMapper objectMapper) {
        this.gymRepository = gymRepository;
        this.slotRepository = slotRepository;
        this.bookingRepository = bookingRepository;
        this.gymCommentRepository = gymCommentRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public List<Gym> syncGymsFromJson() {
        Resource resource = new ClassPathResource("data/gyms.json");
        if (!resource.exists()) {
            LOGGER.warn("Gym data resource is missing: {}", resource.getFilename());
            return Collections.emptyList();
        }

        List<GymSeedData> seeds = readSeedData(resource);
        if (seeds.isEmpty()) {
            LOGGER.warn("Gym data seed file {} yielded no rows", resource.getFilename());
            return Collections.emptyList();
        }

        gymCommentRepository.deleteAllInBatch();
        bookingRepository.deleteAllInBatch();
        slotRepository.deleteAllInBatch();
        int clearedUsers = userRepository.clearGymReferences();
        gymRepository.deleteAllInBatch();
        LOGGER.info("Cleared gym references for {} users before gym import", clearedUsers);

        LocalDateTime now = LocalDateTime.now();
        List<Gym> inserted = new ArrayList<>();

        for (GymSeedData seed : seeds) {
            Gym gym = new Gym();
            gym.setName(seed.getName());
            gym.setLocation(seed.getLocation());
            gym.setDescription(seed.getDescription());
            gym.setPhone(seed.getPhone());
            gym.setCapacity(determineCapacity(seed));
            gym.setApproved(true);
            gym.setActive(true);
            gym.setRequestedAt(now);
            gym.setApprovedAt(now);
            inserted.add(gymRepository.save(gym));
        }

        LOGGER.info("Gym data sync imported {} gyms from {}", inserted.size(), resource.getFilename());
        return inserted;
    }

    private List<GymSeedData> readSeedData(Resource resource) {
        try (InputStream stream = resource.getInputStream()) {
            return objectMapper.readValue(stream, new TypeReference<>() {});
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to read gym seed data", ex);
        }
    }

    private Integer determineCapacity(GymSeedData seed) {
        return Optional.ofNullable(seed.getCapacity())
                .or(() -> Optional.ofNullable(seed.getSeatCount()))
                .or(() -> Optional.ofNullable(seed.getSeats()))
                .orElse(null);
    }
}
