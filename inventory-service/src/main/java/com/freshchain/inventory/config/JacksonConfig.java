package com.freshchain.inventory.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonConfig {

    /**
     * Events are a wire contract with other services, so the shape is pinned
     * here rather than left to defaults: ISO-8601 instants, and unknown fields
     * ignored so a producer can add a field without breaking every consumer.
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer eventJsonCustomizer() {
        return builder -> builder
                .featuresToDisable(
                        SerializationFeature.WRITE_DATES_AS_TIMESTAMPS,
                        DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }
}
