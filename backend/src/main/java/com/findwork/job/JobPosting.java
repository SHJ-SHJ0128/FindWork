package com.findwork.job;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record JobPosting(
        UUID id,
        String title,
        String company,
        String country,
        String city,
        String remoteType,
        String employmentType,
        String experienceLevel,
        String source,
        String canonicalUrl,
        String description,
        List<String> skills,
        int score,
        boolean needsReview,
        Instant postedAt
) {
}
