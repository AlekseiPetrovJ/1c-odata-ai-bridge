package ru.petrov.odata_bridge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

@ConfigurationProperties(prefix = "app.indexing")
public record IndexingConfig(
        @DefaultValue("100") int batchSize,
        @DefaultValue("10") int topK,
        @DefaultValue("0.75") double similarityThreshold,
        List<String> excludeEntities,
        List<String> excludeFields,
        String baseUrl,
        String username,
        String password
) {}
