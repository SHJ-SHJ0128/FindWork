package com.findwork.candidate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.Instant;
import java.util.UUID;

public record CandidateResume(
        UUID id,
        String originalFilename,
        String mediaType,
        long sizeBytes,
        String sha256,
        @JsonIgnore String storagePath,
        @JsonIgnore String extractedText,
        String status,
        String errorMessage,
        boolean current,
        Instant createdAt,
        Instant updatedAt,
        ResumeAnalysisRecord analysis
) {
    public record ResumeAnalysisRecord(
            String status,
            String modelName,
            JsonNode analysis,
            String errorMessage,
            Instant analyzedAt
    ) {
    }
}
