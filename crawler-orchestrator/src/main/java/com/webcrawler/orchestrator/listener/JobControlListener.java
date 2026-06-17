package com.webcrawler.orchestrator.listener;

import com.azure.core.util.Context;
import com.azure.storage.queue.QueueClient;
import com.azure.storage.queue.QueueClientBuilder;
import com.azure.storage.queue.models.QueueMessageItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.webcrawler.orchestrator.model.JobControlMessage;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class JobControlListener {

    private final ObjectMapper objectMapper;
    private final QueueClient jobControlQueueClient;
    private final Set<String> stopRequestedJobs = ConcurrentHashMap.newKeySet();

    public JobControlListener(ObjectMapper objectMapper,
                              @Value("${app.azure.storage.connection-string}") String storageConnectionString,
                              @Value("${app.azure.queues.job-control-queue}") String jobControlQueueName) {
        this.objectMapper = objectMapper;
        this.jobControlQueueClient = new QueueClientBuilder()
                .connectionString(storageConnectionString)
                .queueName(jobControlQueueName)
                .buildClient();
    }

    @Scheduled(fixedDelayString = "${app.polling.job-control-ms:1000}")
    public void pollJobControlQueue() {
        jobControlQueueClient.createIfNotExists();
        for (QueueMessageItem messageItem : jobControlQueueClient.receiveMessages(32, Duration.ofSeconds(30), null, Context.NONE)) {
            try {
                JobControlMessage message = objectMapper.readValue(messageItem.getMessageText(), JobControlMessage.class);
                MDC.put("correlationId", messageItem.getMessageId());
                if (message.getJobId() != null && !message.getJobId().isBlank()) {
                    MDC.put("jobId", message.getJobId());
                }
                handleControlMessage(message);
                jobControlQueueClient.deleteMessage(messageItem.getMessageId(), messageItem.getPopReceipt());
            } catch (Exception ex) {
                log.error("Failed to process job-control message {}", messageItem.getMessageId(), ex);
            } finally {
                MDC.remove("jobId");
                MDC.remove("correlationId");
            }
        }
    }

    public boolean isStopRequested(String jobId) {
        return stopRequestedJobs.contains(jobId);
    }

    public void handleControlMessage(JobControlMessage message) {
        if (message != null && "STOP".equalsIgnoreCase(message.getAction())) {
            stopRequestedJobs.add(message.getJobId());
            log.info("Registered stop request for job {}", message.getJobId());
        }
    }
}
