package ru.petrov.odata_bridge.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.odata")
public record ODataConfig(
        @NotBlank(message = "URL 1С обязателен")
        String baseUrl,

        @NotBlank(message = "Логин обязателен")
        String username,

        @NotBlank(message = "Пароль обязателен")
        String password
) {}
