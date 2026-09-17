package com.findwork.job;

import java.time.Instant;
import java.math.BigDecimal;
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
        String summary,
        List<String> skills,
        BigDecimal salaryMin,
        BigDecimal salaryMax,
        String salaryCurrency,
        String salaryPeriod,
        String salaryText,
        String salarySource,
        int score,
        boolean needsReview,
        Instant postedAt
) {
}
