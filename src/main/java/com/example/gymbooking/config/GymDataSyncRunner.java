package com.example.gymbooking.config;

import com.example.gymbooking.model.Gym;
import com.example.gymbooking.service.GymDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class GymDataSyncRunner implements CommandLineRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(GymDataSyncRunner.class);

    private final GymDataService gymDataService;

    @Value("${gym.data.sync:false}")
    private boolean syncEnabled;

    public GymDataSyncRunner(GymDataService gymDataService) {
        this.gymDataService = gymDataService;
    }

    @Override
    public void run(String... args) {
        if (!syncEnabled) {
            return;
        }

        List<Gym> imported = gymDataService.syncGymsFromJson();
        LOGGER.info("Gym data sync runner imported {} gyms", imported.size());
    }
}
