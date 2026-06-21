package com.webcrawler.gateway.controller;

import com.azure.storage.queue.QueueClient;
import com.azure.storage.queue.QueueClientBuilder;
import com.azure.storage.queue.models.PeekedMessageItem;
import com.azure.storage.queue.models.QueueProperties;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Debug endpoint to inspect queue state. Only available in 'local' profile.
 */
@RestController
@RequestMapping("/api/debug")
@Profile("local")
public class DebugController {

    private final String storageConnectionString;

    public DebugController(@Value("${app.azure.storage.connection-string}") String storageConnectionString) {
        this.storageConnectionString = storageConnectionString;
    }

    @GetMapping("/queues")
    public Map<String, Object> getQueueStats() {
        String[] queueNames = {"url-queue", "parse-queue", "result-queue", "job-control-queue"};
        Map<String, Object> result = new LinkedHashMap<>();

        for (String queueName : queueNames) {
            try {
                QueueClient client = new QueueClientBuilder()
                        .connectionString(storageConnectionString)
                        .queueName(queueName)
                        .buildClient();
                QueueProperties props = client.getProperties();
                Map<String, Object> queueInfo = new LinkedHashMap<>();
                queueInfo.put("approximateMessageCount", props.getApproximateMessagesCount());
                result.put(queueName, queueInfo);
            } catch (Exception e) {
                result.put(queueName, Map.of("error", e.getMessage()));
            }
        }
        return result;
    }

    @GetMapping("/queues/peek")
    public Map<String, Object> peekQueue(@RequestParam(defaultValue = "url-queue") String queue,
                                         @RequestParam(defaultValue = "5") int count) {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            QueueClient client = new QueueClientBuilder()
                    .connectionString(storageConnectionString)
                    .queueName(queue)
                    .buildClient();
            QueueProperties props = client.getProperties();
            result.put("queueName", queue);
            result.put("approximateMessageCount", props.getApproximateMessagesCount());

            List<String> messages = new ArrayList<>();
            for (PeekedMessageItem item : client.peekMessages(Math.min(count, 32), null, null)) {
                messages.add(item.getMessageText());
            }
            result.put("messages", messages);
        } catch (Exception e) {
            result.put("error", e.getMessage());
        }
        return result;
    }
}
