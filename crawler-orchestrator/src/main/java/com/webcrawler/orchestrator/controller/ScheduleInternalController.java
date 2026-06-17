package com.webcrawler.orchestrator.controller;

import com.webcrawler.orchestrator.service.ScheduleExecutor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/schedules")
public class ScheduleInternalController {

    private final ScheduleExecutor scheduleExecutor;

    @PostMapping("/{id}/register")
    public ResponseEntity<Void> registerSchedule(@PathVariable String id,
                                                 @RequestBody RegisterScheduleRequest request) {
        scheduleExecutor.registerSchedule(id, request.userId(), request.cronExpression());
        return ResponseEntity.accepted().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> unregisterSchedule(@PathVariable String id) {
        scheduleExecutor.unregisterSchedule(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/trigger")
    public ResponseEntity<Void> triggerSchedule(@PathVariable String id) {
        scheduleExecutor.triggerNow(id);
        return ResponseEntity.accepted().build();
    }

    public record RegisterScheduleRequest(String userId, String cronExpression) {
    }
}
