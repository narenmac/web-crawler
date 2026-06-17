package com.webcrawler.orchestrator.listener;

import com.azure.core.util.Context;
import com.azure.storage.queue.QueueClient;
import com.azure.storage.queue.QueueClientBuilder;
import com.azure.storage.queue.models.QueueMessageItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.webcrawler.orchestrator.model.ResultQueueMessage;
import com.webcrawler.orchestrator.service.BfsScheduler;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ResultQueueListener {

    private final ObjectMapper objectMapper;
    private final QueueClient resultQueueClient;
    private final BfsScheduler bfsScheduler;

    public ResultQueueListener(ObjectMapper objectMapper,
                               BfsScheduler bfsScheduler,
                               @Value("${app.azure.storage.connection-string}") String storageConnectionString,
                               @Value("${app.azure.queues.result-queue}") String resultQueueName) {
        this.objectMapper = objectMapper;
        this.bfsScheduler = bfsScheduler;
        this.resultQueueClient = new QueueClientBuilder()
                .connectionString(storageConnectionString)
                .queueName(resultQueueName)
                .buildClient();
    }

    @Scheduled(fixedDelayString = "${app.polling.result-queue-ms:1000}")
    public void pollResultQueue() {
        resultQueueClient.createIfNotExists();
        for (QueueMessageItem messageItem : resultQueueClient.receiveMessages(32, Duration.ofSeconds(30), null, Context.NONE)) {
            try {
                ResultQueueMessage message = objectMapper.readValue(messageItem.getMessageText(), ResultQueueMessage.class);
                MDC.put("correlationId", messageItem.getMessageId());
                if (message.getJobId() != null && !message.getJobId().isBlank()) {
                    MDC.put("jobId", message.getJobId());
                }
                bfsScheduler.processDiscoveredLinks(
                        message.getJobId(),
                        message.getDiscoveredLinks(),
                        message.getParentBfsLevel() == null ? 0 : message.getParentBfsLevel(),
                        message.getParentUrl());
                resultQueueClient.deleteMessage(messageItem.getMessageId(), messageItem.getPopReceipt());
            } catch (Exception ex) {
                log.error("Failed to process result-queue message {}", messageItem.getMessageId(), ex);
            } finally {
                MDC.remove("jobId");
                MDC.remove("correlationId");
            }
        }
    }
}
