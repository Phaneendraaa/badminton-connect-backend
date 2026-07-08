package com.app.badminton_backend.auth.config;

import org.modelmapper.ModelMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.client.RestTemplate;

@Configuration
@EnableAsync
public class GeneralConfig {

    @Bean
    public ModelMapper modelMapper() {
        return new ModelMapper();
    }

    /**
     * Used by PushNotificationService to POST to the Expo push API.
     * A single shared instance is safe (RestTemplate is thread-safe).
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
