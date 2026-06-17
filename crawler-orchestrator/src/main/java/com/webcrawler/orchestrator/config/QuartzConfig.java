package com.webcrawler.orchestrator.config;

import java.util.Properties;
import org.springframework.boot.autoconfigure.quartz.SchedulerFactoryBeanCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class QuartzConfig {

    @Bean
    public SchedulerFactoryBeanCustomizer schedulerFactoryBeanCustomizer() {
        return schedulerFactoryBean -> {
            Properties properties = new Properties();
            properties.setProperty("org.quartz.scheduler.instanceName", "CrawlerOrchestratorScheduler");
            properties.setProperty("org.quartz.threadPool.class", "org.quartz.simpl.SimpleThreadPool");
            properties.setProperty("org.quartz.threadPool.threadCount", "2");
            properties.setProperty("org.quartz.threadPool.threadPriority", "5");
            properties.setProperty("org.quartz.jobStore.class", "org.quartz.simpl.RAMJobStore");

            schedulerFactoryBean.setQuartzProperties(properties);
            schedulerFactoryBean.setAutoStartup(true);
            schedulerFactoryBean.setOverwriteExistingJobs(true);
            schedulerFactoryBean.setWaitForJobsToCompleteOnShutdown(true);
            schedulerFactoryBean.setStartupDelay(0);
        };
    }
}
