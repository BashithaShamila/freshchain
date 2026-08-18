package com.freshchain.inventory.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        ReservationProperties.class,
        OutboxProperties.class,
        SubstitutionProperties.class,
        SecurityProperties.class,
})
public class PropertiesConfig {
}
