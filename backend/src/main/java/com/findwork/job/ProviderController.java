package com.findwork.job;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/providers")
@CrossOrigin(origins = "http://127.0.0.1:5173")
public class ProviderController {
    private final JobProviderService service;

    public ProviderController(JobProviderService service) {
        this.service = service;
    }

    @PostMapping("/greenhouse/import")
    public ProviderImportResult importGreenhouse(@Valid @RequestBody ProviderImportRequest request) {
        return service.importGreenhouse(request.board());
    }
}
