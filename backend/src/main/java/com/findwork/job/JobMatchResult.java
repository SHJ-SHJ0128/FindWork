package com.findwork.job;

import java.time.Instant;
import java.util.List;

public record JobMatchResult(
        int totalScore,
        int roleScore,
        int skillScore,
        int experienceScore,
        int locationScore,
        int freshnessScore,
        List<String> matchedSkills,
        List<String> missingRequiredSkills,
        List<String> positiveReasons,
        List<String> concerns,
        boolean hardFilterPassed,
        List<String> hardFilterReasons,
        String profileHash,
        String algorithmVersion,
        Instant calculatedAt
) {
}
