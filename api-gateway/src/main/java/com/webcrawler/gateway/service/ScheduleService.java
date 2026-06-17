package com.webcrawler.gateway.service;

import com.azure.data.tables.TableClient;
import com.azure.data.tables.TableClientBuilder;
import com.webcrawler.gateway.dto.ScheduleRequest;
import com.webcrawler.gateway.dto.ScheduleResponse;
import com.webcrawler.gateway.model.Schedule;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ScheduleService {

    private final String storageConnectionString;
    private final Map<String, Schedule> schedules = new ConcurrentHashMap<>();

    public ScheduleService(@Value("${app.azure.storage.connection-string}") String storageConnectionString) {
        this.storageConnectionString = storageConnectionString;
    }

    public ScheduleResponse createSchedule(String userId, ScheduleRequest request) {
        Instant now = Instant.now();
        String scheduleId = UUID.randomUUID().toString();
        Schedule schedule = Schedule.builder()
                .id(scheduleId)
                .partitionKey(userId)
                .rowKey(scheduleId)
                .userId(userId)
                .name(request.getName())
                .cronExpression(request.getCronExpression())
                .seedUrls(new ArrayList<>(request.getSeedUrls()))
                .maxDepth(request.getMaxDepth())
                .maxUrls(request.getMaxUrls())
                .enabled(request.getEnabled())
                .nextExecutionAt(now.plusSeconds(3600))
                .createdAt(now)
                .updatedAt(now)
                .build();

        schedules.put(scheduleId, schedule);

        // TODO: Persist the schedule definition in Azure Table Storage for orchestrator startup hydration.
        getScheduleTableClient();
        return toResponse(schedule);
    }

    public List<ScheduleResponse> listSchedules(String userId) {
        // TODO: Replace the in-memory list with an Azure Table query scoped to the current user.
        getScheduleTableClient();
        return schedules.values().stream()
                .filter(schedule -> userId.equals(schedule.getUserId()))
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public ScheduleResponse updateSchedule(String id, String userId, ScheduleRequest request) {
        Schedule schedule = findOwnedSchedule(id, userId);
        schedule.setName(request.getName());
        schedule.setCronExpression(request.getCronExpression());
        schedule.setSeedUrls(new ArrayList<>(request.getSeedUrls()));
        schedule.setMaxDepth(request.getMaxDepth());
        schedule.setMaxUrls(request.getMaxUrls());
        schedule.setEnabled(request.getEnabled());
        schedule.setUpdatedAt(Instant.now());

        // TODO: Upsert the updated schedule entity in Azure Table Storage.
        getScheduleTableClient();
        return toResponse(schedule);
    }

    public void deleteSchedule(String id, String userId) {
        Schedule schedule = findOwnedSchedule(id, userId);
        schedules.remove(schedule.getId());

        // TODO: Delete the Azure Table entity and unschedule the corresponding Quartz trigger.
        getScheduleTableClient();
    }

    public ScheduleResponse triggerSchedule(String id, String userId) {
        Schedule schedule = findOwnedSchedule(id, userId);
        schedule.setLastTriggeredJobId(UUID.randomUUID().toString());
        schedule.setNextExecutionAt(Instant.now().plusSeconds(3600));
        schedule.setUpdatedAt(Instant.now());

        // TODO: Send an on-demand trigger message to crawler-orchestrator.
        getScheduleTableClient();
        return toResponse(schedule);
    }

    private Schedule findOwnedSchedule(String id, String userId) {
        Schedule schedule = schedules.get(id);
        if (schedule == null || !userId.equals(schedule.getUserId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Schedule not found");
        }
        return schedule;
    }

    private ScheduleResponse toResponse(Schedule schedule) {
        return ScheduleResponse.builder()
                .id(schedule.getId())
                .userId(schedule.getUserId())
                .name(schedule.getName())
                .cronExpression(schedule.getCronExpression())
                .seedUrls(schedule.getSeedUrls())
                .maxDepth(schedule.getMaxDepth())
                .maxUrls(schedule.getMaxUrls())
                .enabled(schedule.getEnabled())
                .lastTriggeredJobId(schedule.getLastTriggeredJobId())
                .nextExecutionAt(schedule.getNextExecutionAt())
                .createdAt(schedule.getCreatedAt())
                .updatedAt(schedule.getUpdatedAt())
                .build();
    }

    private TableClient getScheduleTableClient() {
        return new TableClientBuilder()
                .connectionString(storageConnectionString)
                .tableName("schedules")
                .buildClient();
    }
}
