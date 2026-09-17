package com.findwork.candidate;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.CrossOrigin;

@RestController
@RequestMapping("/api/candidate-profile")
@CrossOrigin(origins = "http://127.0.0.1:5173")
public class CandidateProfileController {
    private final CandidateProfileRepository repository;

    public CandidateProfileController(CandidateProfileRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public ResponseEntity<CandidateProfile> get() {
        return repository.find().map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PutMapping
    public CandidateProfile save(@Valid @RequestBody CandidateProfile profile) {
        repository.save(profile);
        return profile;
    }
}
