package com.demomart.catalog.config;

import com.demomart.catalog.phone.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.util.List;

@Configuration
public class PhoneSeedConfiguration {
    @Bean
    CommandLineRunner seedPhones(PhoneRepository phones) {
        return args -> {
            if (phones.count() > 0) return;
            phones.saveAll(List.of(
                    new Phone("Apple", "iPhone 16", "Fast A-series performance with an excellent camera system.", new BigDecimal("799.00"), 12, "Ultramarine", "128 GB"),
                    new Phone("Samsung", "Galaxy S25", "Premium Android phone with a vivid AMOLED display.", new BigDecimal("749.00"), 9, "Navy", "256 GB"),
                    new Phone("Google", "Pixel 10", "Clean Android experience with powerful computational photography.", new BigDecimal("699.00"), 7, "Obsidian", "128 GB"),
                    new Phone("OnePlus", "OnePlus 14", "Flagship speed, smooth display and very fast charging.", new BigDecimal("649.00"), 15, "Emerald", "256 GB"),
                    new Phone("Nothing", "Phone (4)", "Distinctive transparent design with a clean software experience.", new BigDecimal("599.00"), 6, "White", "256 GB"),
                    new Phone("Motorola", "Edge 70", "Slim everyday phone with a large display and long battery life.", new BigDecimal("449.00"), 18, "Graphite", "128 GB")
            ));
        };
    }
}

