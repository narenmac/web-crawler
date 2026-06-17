package com.webcrawler.orchestrator.listener;

import com.webcrawler.orchestrator.model.JobControlMessage;
import com.webcrawler.orchestrator.service.JobManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class JobControlListener {

    private final JobManager jobManager;

    public JobControlListener(JobManager jobManager) {
        this.jobManager = jobManager;
    }

    @Scheduled(fixedDelayString = "${app.polling.job-control-ms:5000}")
    public void pollJobControlQueue() {
        // TODO: Receive messages from the Azure job-control-queue and deserialize to JobControlMessage.
        log.debug("Polling job-control queue for stop signals");
    }

    public void handleControlMessage(JobControlMessage message) {
        if ("STOP".equalsIgnoreCase(message.getAction())) {
            jobManager.stopJob(message.getJobId());
        }
    }
}
