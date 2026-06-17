package com.webcrawler.orchestrator.config;

import com.azure.storage.queue.QueueClient;
import com.azure.storage.queue.QueueClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HealthCheckConfig {

    @Bean
    public HealthIndicator azureQueueHealthIndicator(@Value("${app.azure.storage.connection-string}") String storageConnectionString,
                                                     @Value("${app.azure.queues.job-control-queue}") String queueName) {
        QueueClient queueClient = new QueueClientBuilder()
                .connectionString(storageConnectionString)
                .queueName(queueName)
                .buildClient();

        return () -> {
            try {
                return Health.up()
                        .withDetail("storage", "queue")
                        .withDetail("queueName", queueName)
                        .withDetail("exists", queueClient.exists())
                        .build();
            } catch (RuntimeException ex) {
                return Health.down(ex)
                        .withDetail("storage", "queue")
                        .withDetail("queueName", queueName)
                        .build();
            }
        };
    }
}
