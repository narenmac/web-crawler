package com.webcrawler.gateway.controller;

import com.webcrawler.gateway.dto.ScheduleRequest;
import com.webcrawler.gateway.dto.ScheduleResponse;
import com.webcrawler.gateway.service.ScheduleService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/schedules")
public class ScheduleController {

    private final ScheduleService scheduleService;

    public ScheduleController(ScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    @PostMapping
    public ResponseEntity<ScheduleResponse> createSchedule(@Valid @RequestBody ScheduleRequest request,
                                                           Authentication authentication) {
        ScheduleResponse response = scheduleService.createSchedule(currentUser(authentication), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public List<ScheduleResponse> listSchedules(Authentication authentication) {
        return scheduleService.listSchedules(currentUser(authentication));
    }

    @PutMapping("/{id}")
    public ScheduleResponse updateSchedule(@PathVariable String id,
                                           @Valid @RequestBody ScheduleRequest request,
                                           Authentication authentication) {
        return scheduleService.updateSchedule(id, currentUser(authentication), request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSchedule(@PathVariable String id, Authentication authentication) {
        scheduleService.deleteSchedule(id, currentUser(authentication));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/trigger")
    public ScheduleResponse triggerSchedule(@PathVariable String id, Authentication authentication) {
        return scheduleService.triggerSchedule(id, currentUser(authentication));
    }

    private String currentUser(Authentication authentication) {
        return authentication != null ? authentication.getName() : "anonymous";
    }
}
