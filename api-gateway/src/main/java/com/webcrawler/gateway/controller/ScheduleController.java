package com.webcrawler.gateway.controller;

import com.webcrawler.gateway.dto.JobResponse;
import com.webcrawler.gateway.dto.ScheduleRequest;
import com.webcrawler.gateway.dto.ScheduleResponse;
import com.webcrawler.gateway.security.CurrentUserProvider;
import com.webcrawler.gateway.service.ScheduleService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/schedules")
public class ScheduleController {

    private final ScheduleService scheduleService;
    private final CurrentUserProvider currentUserProvider;

    @PostMapping
    public ResponseEntity<ScheduleResponse> createSchedule(@Valid @RequestBody ScheduleRequest request) {
        ScheduleResponse response = scheduleService.createSchedule(currentUserProvider.getCurrentUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public List<ScheduleResponse> listSchedules() {
        return scheduleService.getSchedules(currentUserProvider.getCurrentUserId());
    }

    @PutMapping("/{id}")
    public ScheduleResponse updateSchedule(@PathVariable String id, @Valid @RequestBody ScheduleRequest request) {
        return scheduleService.updateSchedule(currentUserProvider.getCurrentUserId(), id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSchedule(@PathVariable String id) {
        scheduleService.deleteSchedule(currentUserProvider.getCurrentUserId(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/trigger")
    public JobResponse triggerSchedule(@PathVariable String id) {
        return scheduleService.triggerSchedule(currentUserProvider.getCurrentUserId(), id);
    }
}
