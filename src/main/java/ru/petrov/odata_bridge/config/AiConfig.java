package ru.petrov.odata_bridge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ai")
public record AiConfig(
        String systemPrompt
) {
}
