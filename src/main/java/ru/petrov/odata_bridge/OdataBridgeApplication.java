package ru.petrov.odata_bridge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import ru.petrov.odata_bridge.config.IndexingConfig;

@SpringBootApplication
@EnableConfigurationProperties(IndexingConfig.class)
public class OdataBridgeApplication {

	public static void main(String[] args) {
		SpringApplication.run(OdataBridgeApplication.class, args);
	}

}
