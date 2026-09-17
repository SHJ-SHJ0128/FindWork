package com.findwork.candidate;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/resumes")
@CrossOrigin(origins = "http://127.0.0.1:5173")
public class CandidateResumeController {
    private final CandidateResumeService service;

    public CandidateResumeController(CandidateResumeService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CandidateResume> upload(@RequestPart("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.upload(file));
    }

    @GetMapping("/current")
    public ResponseEntity<CandidateResume> current() {
        return service.current().map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/{id}")
    public CandidateResume get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping("/{id}/analyze")
    public CandidateResume analyze(@PathVariable UUID id) {
        return service.analyze(id);
    }

    @PostMapping("/{id}/apply-to-profile")
    public CandidateProfile apply(@PathVariable UUID id, @Valid @RequestBody ResumeApplyRequest request) {
        return service.apply(id, request.fields());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    public record ResumeApplyRequest(@NotEmpty Set<String> fields) {
    }
}
