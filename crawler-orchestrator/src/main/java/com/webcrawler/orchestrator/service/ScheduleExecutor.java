package com.webcrawler.orchestrator.service;

import com.webcrawler.orchestrator.model.CrawlSchedule;
import com.webcrawler.orchestrator.model.JobRecord;
import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.quartz.CronScheduleBuilder;
import org.quartz.CronTrigger;
import org.quartz.JobBuilder;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.TriggerBuilder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ScheduleExecutor {

    private final Scheduler scheduler;
    private final JobManager jobManager;
    private final BfsScheduler bfsScheduler;
    private final Map<String, CrawlSchedule> schedules = new ConcurrentHashMap<>();

    public ScheduleExecutor(Scheduler scheduler, JobManager jobManager, BfsScheduler bfsScheduler) {
        this.scheduler = scheduler;
        this.jobManager = jobManager;
        this.bfsScheduler = bfsScheduler;
    }

    @PostConstruct
    public void loadSchedulesOnStartup() {
        try {
            scheduler.getContext().put(ScheduleExecutor.class.getName(), this);
        } catch (SchedulerException ex) {
            throw new IllegalStateException("Unable to initialize Quartz scheduler context", ex);
        }

        // TODO: Load persisted schedules from Azure Table Storage instead of returning an empty list.
        findSchedulesInAzureTable().forEach(this::registerSchedule);
    }

    public void registerSchedule(CrawlSchedule schedule) {
        schedules.put(schedule.getId(), schedule);
        if (!schedule.isEnabled()) {
            return;
        }

        try {
            JobDetail jobDetail = JobBuilder.newJob(QuartzScheduleJob.class)
                    .withIdentity(JobKey.jobKey(schedule.getId(), "crawler-schedules"))
                    .usingJobData(new JobDataMap(Map.of("scheduleId", schedule.getId())))
                    .build();

            CronTrigger trigger = TriggerBuilder.newTrigger()
                    .withIdentity(schedule.getId() + "-trigger", "crawler-schedules")
                    .withSchedule(CronScheduleBuilder.cronSchedule(schedule.getCronExpression()))
                    .build();

            scheduler.scheduleJob(jobDetail, trigger);
        } catch (SchedulerException ex) {
            throw new IllegalStateException("Unable to register schedule " + schedule.getId(), ex);
        }
    }

    public void removeSchedule(String scheduleId) {
        schedules.remove(scheduleId);
        try {
            scheduler.deleteJob(JobKey.jobKey(scheduleId, "crawler-schedules"));
        } catch (SchedulerException ex) {
            throw new IllegalStateException("Unable to remove schedule " + scheduleId, ex);
        }
    }

    public void triggerNow(String scheduleId) {
        triggerSchedule(scheduleId);
    }

    void triggerSchedule(String scheduleId) {
        CrawlSchedule schedule = schedules.get(scheduleId);
        if (schedule == null || !schedule.isEnabled()) {
            log.info("Skipping disabled or missing schedule {}", scheduleId);
            return;
        }

        if (jobManager.hasRunningJob()) {
            log.info("Skipping schedule {} because another job is already running", scheduleId);
            return;
        }

        JobRecord job = jobManager.createJob(
                schedule.getUserId(),
                schedule.getSeedUrls(),
                schedule.getMaxDepth(),
                schedule.getMaxUrls()
        );
        jobManager.startJob(job.getId());
        bfsScheduler.enqueueSeedUrls(job.getId(), schedule.getSeedUrls());
        schedule.setUpdatedAt(Instant.now());
    }

    private List<CrawlSchedule> findSchedulesInAzureTable() {
        return List.of();
    }

    public static class QuartzScheduleJob implements org.quartz.Job {

        @Override
        public void execute(JobExecutionContext context) throws JobExecutionException {
            String scheduleId = context.getMergedJobDataMap().getString("scheduleId");
            try {
                ScheduleExecutor executor = (ScheduleExecutor) context.getScheduler()
                        .getContext()
                        .get(ScheduleExecutor.class.getName());
                executor.triggerSchedule(scheduleId);
            } catch (SchedulerException ex) {
                throw new JobExecutionException("Unable to trigger scheduled crawl job", ex);
            }
        }
    }
}
