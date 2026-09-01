package com.freshchain.order.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableFeignClients(basePackages = "com.freshchain.order.client")
@EnableConfigurationProperties({OutboxProperties.class, SecurityProperties.class})
public class AppConfig {
}
