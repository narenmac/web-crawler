package com.webcrawler.gateway.service;

import com.azure.data.tables.TableClient;
import com.azure.data.tables.TableClientBuilder;
import com.azure.data.tables.TableServiceClient;
import com.azure.data.tables.TableServiceClientBuilder;
import com.azure.data.tables.models.ListEntitiesOptions;
import com.azure.data.tables.models.TableServiceException;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.webcrawler.gateway.dto.JobResponse;
import com.webcrawler.gateway.dto.ScheduleRequest;
import com.webcrawler.gateway.dto.ScheduleResponse;
import com.webcrawler.gateway.model.Schedule;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleService {

    @Value("${app.azure.storage.connection-string}")
    private String storageConnectionString;

    @Value("${app.azure.storage.seed-container:crawler-input}")
    private String seedContainerName;

    private final JobService jobService;

    public ScheduleResponse createSchedule(String userId, ScheduleRequest request) {
        validateScheduleRequest(request);

        Instant now = Instant.now();
        String scheduleId = UUID.randomUUID().toString();
        Schedule schedule = Schedule.builder()
                .partitionKey(userId)
                .rowKey(scheduleId)
                .intervalType(normalizeIntervalType(request.getIntervalType()))
                .cronExpression(request.getCronExpression())
                .seedFileUrl(request.getSeedFileId())
                .enabled(request.isEnabled())
                .lastRunStatus("NEVER_RUN")
                .nextRunAt(computeNextRunAt(request.getIntervalType(), request.getCronExpression(), request.isEnabled()))
                .createdAt(now)
                .updatedAt(now)
                .build();

        getScheduleTableClient().upsertEntity(schedule.toEntity());
        log.info("Created schedule {} for user {}", scheduleId, userId);
        return toResponse(schedule);
    }

    public List<ScheduleResponse> getSchedules(String userId) {
        String filter = "PartitionKey eq '%s'".formatted(escapeOData(userId));
        return StreamSupport.stream(getScheduleTableClient()
                        .listEntities(new ListEntitiesOptions().setFilter(filter), null, null)
                        .spliterator(), false)
                .map(Schedule::fromEntity)
                .sorted(Comparator.comparing(Schedule::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(this::toResponse)
                .toList();
    }

    public ScheduleResponse updateSchedule(String userId, String scheduleId, ScheduleRequest request) {
        validateScheduleRequest(request);
        Schedule schedule = findOwnedSchedule(userId, scheduleId);
        schedule.setIntervalType(normalizeIntervalType(request.getIntervalType()));
        schedule.setCronExpression(request.getCronExpression());
        schedule.setSeedFileUrl(request.getSeedFileId());
        schedule.setEnabled(request.isEnabled());
        schedule.setNextRunAt(computeNextRunAt(request.getIntervalType(), request.getCronExpression(), request.isEnabled()));
        schedule.setUpdatedAt(Instant.now());

        getScheduleTableClient().upsertEntity(schedule.toEntity());
        log.info("Updated schedule {} for user {}", scheduleId, userId);
        return toResponse(schedule);
    }

    public void deleteSchedule(String userId, String scheduleId) {
        findOwnedSchedule(userId, scheduleId);
        getScheduleTableClient().deleteEntity(userId, scheduleId);
        log.info("Deleted schedule {} for user {}", scheduleId, userId);
    }

    public JobResponse triggerSchedule(String userId, String scheduleId) {
        Schedule schedule = findOwnedSchedule(userId, scheduleId);
        if (!schedule.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Schedule is disabled");
        }

        JobResponse job = jobService.createJobFromSeedSource(userId, schedule.getSeedFileUrl());
        schedule.setLastRunJobId(job.getId());
        schedule.setLastRunStatus(job.getStatus());
        schedule.setNextRunAt(computeNextRunAt(schedule.getIntervalType(), schedule.getCronExpression(), schedule.isEnabled()));
        schedule.setUpdatedAt(Instant.now());
        getScheduleTableClient().upsertEntity(schedule.toEntity());

        log.info("Triggered schedule {} and created job {}", scheduleId, job.getId());
        return job;
    }

    private Schedule findOwnedSchedule(String userId, String scheduleId) {
        try {
            return Schedule.fromEntity(getScheduleTableClient().getEntity(userId, scheduleId));
        } catch (TableServiceException ex) {
            if (ex.getResponse() != null && ex.getResponse().getStatusCode() == 404) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Schedule not found");
            }
            throw ex;
        }
    }

    private void validateScheduleRequest(ScheduleRequest request) {
        String intervalType = normalizeIntervalType(request.getIntervalType());
        if (!List.of("CRON", "HOURLY", "DAILY", "WEEKLY").contains(intervalType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "intervalType must be CRON, HOURLY, DAILY, or WEEKLY");
        }
        if (!StringUtils.hasText(request.getSeedFileId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "seedFileId is required");
        }
        if (!seedBlobContainerClient().getBlobClient(request.getSeedFileId()).exists()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Referenced seed file was not found");
        }
        if ("CRON".equals(intervalType)) {
            if (!StringUtils.hasText(request.getCronExpression()) || !CronExpression.isValidExpression(request.getCronExpression())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A valid cronExpression is required for CRON schedules");
            }
        } else if (StringUtils.hasText(request.getCronExpression()) && !CronExpression.isValidExpression(request.getCronExpression())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cronExpression must be a valid cron expression when supplied");
        }
    }

    private Instant computeNextRunAt(String intervalType, String cronExpression, boolean enabled) {
        if (!enabled) {
            return null;
        }

        String normalizedType = normalizeIntervalType(intervalType);
        Instant now = Instant.now();
        return switch (normalizedType) {
            case "HOURLY" -> now.plusSeconds(3600);
            case "DAILY" -> now.plusSeconds(86_400);
            case "WEEKLY" -> now.plusSeconds(604_800);
            case "CRON" -> {
                ZonedDateTime next = CronExpression.parse(cronExpression).next(ZonedDateTime.now(ZoneOffset.UTC));
                yield next != null ? next.toInstant() : null;
            }
            default -> null;
        };
    }

    private ScheduleResponse toResponse(Schedule schedule) {
        return ScheduleResponse.builder()
                .id(schedule.getRowKey())
                .seedFileName(extractSeedFileName(schedule.getSeedFileUrl()))
                .intervalType(schedule.getIntervalType())
                .cronExpression(schedule.getCronExpression())
                .nextRunAt(schedule.getNextRunAt())
                .lastRunStatus(schedule.getLastRunStatus())
                .enabled(schedule.isEnabled())
                .build();
    }

    private String extractSeedFileName(String seedFileUrl) {
        if (!StringUtils.hasText(seedFileUrl)) {
            return null;
        }
        int slashIndex = seedFileUrl.lastIndexOf('/');
        return slashIndex >= 0 ? seedFileUrl.substring(slashIndex + 1) : seedFileUrl;
    }

    private String normalizeIntervalType(String intervalType) {
        return intervalType == null ? "" : intervalType.trim().toUpperCase(Locale.ROOT);
    }

    private TableClient getScheduleTableClient() {
        TableServiceClient serviceClient = new TableServiceClientBuilder()
                .connectionString(storageConnectionString)
                .buildClient();
        serviceClient.createTableIfNotExists("schedules");
        return new TableClientBuilder()
                .connectionString(storageConnectionString)
                .tableName("schedules")
                .buildClient();
    }

    private BlobContainerClient seedBlobContainerClient() {
        BlobContainerClient client = new BlobServiceClientBuilder()
                .connectionString(storageConnectionString)
                .buildClient()
                .getBlobContainerClient(seedContainerName);
        client.createIfNotExists();
        return client;
    }

    private String escapeOData(String value) {
        return value.replace("'", "''");
    }
}
