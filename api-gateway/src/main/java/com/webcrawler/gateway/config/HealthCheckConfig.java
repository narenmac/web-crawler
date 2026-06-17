package com.webcrawler.gateway.config;

import com.azure.data.tables.TableServiceClient;
import com.azure.data.tables.TableServiceClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HealthCheckConfig {

    @Bean
    public HealthIndicator azureTableHealthIndicator(@Value("${app.azure.storage.connection-string}") String storageConnectionString) {
        TableServiceClient tableServiceClient = new TableServiceClientBuilder()
                .connectionString(storageConnectionString)
                .buildClient();

        return () -> {
            try {
                tableServiceClient.listTables().iterator().hasNext();
                return Health.up()
                        .withDetail("storage", "table")
                        .build();
            } catch (RuntimeException ex) {
                return Health.down(ex)
                        .withDetail("storage", "table")
                        .build();
            }
        };
    }
}
