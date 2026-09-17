package com.findwork.job;

import java.time.Instant;
import java.util.UUID;

public record JobAiAnalysisRecord(
        UUID id,
        UUID jobId,
        String descriptionHash,
        String analysisVersion,
        String modelName,
        String status,
        JobSemanticAnalysis analysis,
        String errorMessage,
        Instant analyzedAt
) {
}
