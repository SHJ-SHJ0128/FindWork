package com.findwork.job;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/jobs")
@CrossOrigin(origins = "http://127.0.0.1:5173")
public class JobIntelligenceController {
    private final JobIntelligenceService service;

    public JobIntelligenceController(JobIntelligenceService service) {
        this.service = service;
    }

    @GetMapping("/{jobId}/analysis")
    public Object current(@PathVariable UUID jobId) {
        return service.current(jobId);
    }

    @PostMapping("/{jobId}/analysis")
    public Object analyze(@PathVariable UUID jobId) {
        return service.analyze(jobId);
    }
}
