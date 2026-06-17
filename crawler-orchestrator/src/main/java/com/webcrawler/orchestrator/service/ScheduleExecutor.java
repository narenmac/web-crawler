package com.webcrawler.orchestrator.service;

import com.azure.core.http.rest.PagedIterable;
import com.azure.data.tables.TableClient;
import com.azure.data.tables.TableClientBuilder;
import com.azure.data.tables.TableServiceClient;
import com.azure.data.tables.TableServiceClientBuilder;
import com.azure.data.tables.models.ListEntitiesOptions;
import com.azure.data.tables.models.TableEntity;
import com.azure.data.tables.models.TableServiceException;
import com.webcrawler.orchestrator.job.CrawlScheduleJob;
import com.webcrawler.orchestrator.model.CrawlSchedule;
import com.webcrawler.orchestrator.model.JobRecord;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
public class ScheduleExecutor {

    static final String SCHEDULE_GROUP = "crawler-schedules";
    private static final int MAX_RETRIES = 3;

    private final Scheduler scheduler;
    private final JobManager jobManager;
    private final TableClient scheduleTableClient;
    private final Map<String, CrawlSchedule> schedules = new ConcurrentHashMap<>();

    public ScheduleExecutor(Scheduler scheduler,
                            JobManager jobManager,
                            @Value("${app.azure.storage.connection-string}") String storageConnectionString,
                            @Value("${app.azure.tables.schedules:schedules}") String schedulesTableName) {
        this.scheduler = scheduler;
        this.jobManager = jobManager;

        TableServiceClient tableServiceClient = new TableServiceClientBuilder()
                .connectionString(storageConnectionString)
                .buildClient();
        tableServiceClient.createTableIfNotExists(schedulesTableName);
        this.scheduleTableClient = new TableClientBuilder()
                .connectionString(storageConnectionString)
                .tableName(schedulesTableName)
                .buildClient();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void loadSchedulesOnStartup() {
        try {
            scheduler.getContext().put(ScheduleExecutor.class.getName(), this);
        } catch (SchedulerException ex) {
            throw new IllegalStateException("Unable to initialize Quartz scheduler context", ex);
        }

        List<CrawlSchedule> enabledSchedules = findEnabledSchedules();
        for (CrawlSchedule schedule : enabledSchedules) {
            try {
                scheduleQuartzJob(schedule, false);
            } catch (RuntimeException ex) {
                log.error("Failed to register schedule {} for user {} during startup",
                        schedule.getScheduleId(),
                        schedule.getUserId(),
                        ex);
            }
        }
        log.info("Loaded {} enabled schedules into Quartz", enabledSchedules.size());
    }

    public void registerSchedule(String scheduleId, String userId, String cronExpression) {
        CrawlSchedule schedule = findSchedule(userId, scheduleId);
        if (StringUtils.hasText(cronExpression)) {
            schedule.setCronExpression(cronExpression);
        }

        if (!schedule.isEnabled()) {
            schedules.put(scheduleId, schedule);
            unregisterSchedule(scheduleId);
            return;
        }

        scheduleQuartzJob(schedule, true);
    }

    public void unregisterSchedule(String scheduleId) {
        schedules.remove(scheduleId);
        try {
            scheduler.deleteJob(jobKey(scheduleId));
            log.info("Unregistered schedule {}", scheduleId);
        } catch (SchedulerException ex) {
            throw new IllegalStateException("Unable to unregister schedule " + scheduleId, ex);
        }
    }

    public void triggerNow(String scheduleId) {
        CrawlSchedule schedule = schedules.get(scheduleId);
        if (schedule == null) {
            schedule = findScheduleById(scheduleId)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown schedule id: " + scheduleId));
            schedules.put(scheduleId, schedule);
        }
        if (!schedule.isEnabled()) {
            throw new IllegalStateException("Schedule is disabled: " + scheduleId);
        }

        try {
            if (!scheduler.checkExists(jobKey(scheduleId))) {
                scheduleQuartzJob(schedule, false);
            }
            scheduler.triggerJob(jobKey(scheduleId));
            log.info("Triggered schedule {} immediately", scheduleId);
        } catch (SchedulerException ex) {
            throw new IllegalStateException("Unable to trigger schedule " + scheduleId, ex);
        }
    }

    public void executeScheduledCrawl(String scheduleId, String userId, Date nextFireTime) {
        CrawlSchedule schedule = findSchedule(userId, scheduleId);
        schedules.put(scheduleId, schedule);

        if (!schedule.isEnabled()) {
            log.info("Skipping disabled schedule {}", scheduleId);
            unregisterSchedule(scheduleId);
            return;
        }

        Instant now = Instant.now();
        schedule.setUpdatedAt(now);
        schedule.setNextRunAt(toInstant(nextFireTime));

        if (jobManager.isJobRunning()) {
            schedule.setLastRunStatus("SKIPPED");
            upsertSchedule(schedule);
            log.warn("Skipping scheduled crawl for {} because another crawl job is already running", scheduleId);
            return;
        }

        try {
            JobRecord job = jobManager.createJob(userId, schedule.getSeedFileUrl());
            schedule.setLastRunJobId(job.getId());
            schedule.setLastRunStatus(job.getStatus());
            upsertSchedule(schedule);
            log.info("Schedule {} started crawl job {}", scheduleId, job.getId());
        } catch (RuntimeException ex) {
            schedule.setLastRunStatus("FAILED");
            upsertSchedule(schedule);
            log.error("Failed to execute scheduled crawl for {}", scheduleId, ex);
            throw ex;
        }
    }

    @PreDestroy
    public void shutdownScheduler() {
        try {
            if (!scheduler.isShutdown()) {
                log.info("Shutting down Quartz scheduler");
                scheduler.shutdown(true);
            }
        } catch (SchedulerException ex) {
            log.warn("Failed to shut down Quartz scheduler cleanly", ex);
        }
    }

    private void scheduleQuartzJob(CrawlSchedule schedule, boolean persistSchedule) {
        if (!StringUtils.hasText(schedule.getCronExpression())) {
            throw new IllegalArgumentException("Schedule %s has no cron expression".formatted(schedule.getScheduleId()));
        }

        JobDetail jobDetail = JobBuilder.newJob(CrawlScheduleJob.class)
                .withIdentity(jobKey(schedule.getScheduleId()))
                .usingJobData("scheduleId", schedule.getScheduleId())
                .usingJobData("userId", schedule.getUserId())
                .requestRecovery(true)
                .build();

        var trigger = TriggerBuilder.newTrigger()
                .withIdentity(triggerKey(schedule.getScheduleId()))
                .forJob(jobDetail)
                .withSchedule(CronScheduleBuilder.cronSchedule(schedule.getCronExpression())
                        .withMisfireHandlingInstructionDoNothing())
                .build();

        try {
            if (scheduler.checkExists(jobKey(schedule.getScheduleId()))) {
                scheduler.deleteJob(jobKey(schedule.getScheduleId()));
            }

            Date nextFireTime = scheduler.scheduleJob(jobDetail, trigger);
            schedule.setNextRunAt(toInstant(nextFireTime));
            schedule.setUpdatedAt(Instant.now());
            schedules.put(schedule.getScheduleId(), schedule);
            if (persistSchedule) {
                upsertSchedule(schedule);
            }
            log.info("Registered schedule {} for user {} with next run at {}",
                    schedule.getScheduleId(),
                    schedule.getUserId(),
                    schedule.getNextRunAt());
        } catch (SchedulerException ex) {
            throw new IllegalStateException("Unable to register schedule " + schedule.getScheduleId(), ex);
        }
    }

    private List<CrawlSchedule> findEnabledSchedules() {
        PagedIterable<TableEntity> entities = withRetry(() -> scheduleTableClient.listEntities(
                new ListEntitiesOptions().setFilter("enabled eq true"),
                null,
                null), "load enabled schedules");
        return entities.stream()
                .map(CrawlSchedule::fromEntity)
                .toList();
    }

    private CrawlSchedule findSchedule(String userId, String scheduleId) {
        RuntimeException lastException = null;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                CrawlSchedule schedule = CrawlSchedule.fromEntity(scheduleTableClient.getEntity(userId, scheduleId));
                schedules.put(scheduleId, schedule);
                return schedule;
            } catch (TableServiceException ex) {
                if (ex.getResponse() != null && ex.getResponse().getStatusCode() == 404) {
                    throw new IllegalArgumentException("Unknown schedule id: " + scheduleId, ex);
                }
                lastException = ex;
                log.warn("Operation failed while trying to read schedule {} (attempt {}/{}).",
                        scheduleId,
                        attempt,
                        MAX_RETRIES,
                        ex);
                sleep(attempt);
            } catch (RuntimeException ex) {
                lastException = ex;
                log.warn("Operation failed while trying to read schedule {} (attempt {}/{}).",
                        scheduleId,
                        attempt,
                        MAX_RETRIES,
                        ex);
                sleep(attempt);
            }
        }
        throw new IllegalStateException("Unable to read schedule " + scheduleId, lastException);
    }

    private Optional<CrawlSchedule> findScheduleById(String scheduleId) {
        PagedIterable<TableEntity> entities = withRetry(() -> scheduleTableClient.listEntities(
                new ListEntitiesOptions().setFilter("RowKey eq '%s'".formatted(escapeFilterValue(scheduleId))),
                null,
                null), "find schedule by id");
        return entities.stream()
                .findFirst()
                .map(CrawlSchedule::fromEntity);
    }

    private void upsertSchedule(CrawlSchedule schedule) {
        withRetry(() -> {
            scheduleTableClient.upsertEntity(schedule.toEntity());
            return null;
        }, "upsert schedule " + schedule.getScheduleId());
        schedules.put(schedule.getScheduleId(), schedule);
    }

    private Instant toInstant(Date value) {
        return value == null ? null : value.toInstant();
    }

    private String escapeFilterValue(String value) {
        return value.replace("'", "''");
    }

    private <T> T withRetry(AzureOperation<T> operation, String action) {
        RuntimeException lastException = null;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                return operation.run();
            } catch (RuntimeException ex) {
                lastException = ex;
                log.warn("Operation failed while trying to {} (attempt {}/{}).", action, attempt, MAX_RETRIES, ex);
                sleep(attempt);
            }
        }
        throw new IllegalStateException("Unable to " + action, lastException);
    }

    private void sleep(int attempt) {
        try {
            Thread.sleep(250L * attempt);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while retrying schedule operation", ex);
        }
    }

    private JobKey jobKey(String scheduleId) {
        return JobKey.jobKey(scheduleId, SCHEDULE_GROUP);
    }

    private TriggerKey triggerKey(String scheduleId) {
        return TriggerKey.triggerKey(scheduleId + "-trigger", SCHEDULE_GROUP);
    }

    @FunctionalInterface
    private interface AzureOperation<T> {
        T run();
    }
}
