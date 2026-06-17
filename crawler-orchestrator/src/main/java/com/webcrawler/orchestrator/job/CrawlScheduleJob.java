package com.webcrawler.orchestrator.job;

import com.webcrawler.orchestrator.service.ScheduleExecutor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.SchedulerException;

@Slf4j
@DisallowConcurrentExecution
public class CrawlScheduleJob implements Job {

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        String scheduleId = context.getMergedJobDataMap().getString("scheduleId");
        String userId = context.getMergedJobDataMap().getString("userId");

        try {
            ScheduleExecutor executor = (ScheduleExecutor) context.getScheduler()
                    .getContext()
                    .get(ScheduleExecutor.class.getName());
            executor.executeScheduledCrawl(scheduleId, userId, context.getNextFireTime());
        } catch (SchedulerException ex) {
            log.error("Quartz scheduler error while executing schedule {}", scheduleId, ex);
            throw new JobExecutionException("Unable to access scheduler context", ex, false);
        } catch (Exception ex) {
            log.error("Failed to execute scheduled crawl for schedule {}", scheduleId, ex);
            throw new JobExecutionException("Scheduled crawl execution failed", ex, false);
        }
    }
}
