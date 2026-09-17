package com.findwork.job;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class GmailLinkedInServiceTest {
    @Test
    void extractsUniqueLinkedInJobLinksAndKeepsTheOriginalUrl() {
        var jobs = GmailLinkedInService.parseJobs(
                "<a href=\"https://www.linkedin.com/comm/jobs/view/123456/?trk=alert\">Backend Engineer</a> " +
                        "<a href=\"https://www.linkedin.com/jobs/view/123456/\">duplicate</a>",
                "LinkedIn jobs for you",
                Instant.parse("2026-09-17T00:00:00Z"));

        assertThat(jobs).singleElement().satisfies(job -> {
            assertThat(job.title()).isEqualTo("Backend Engineer");
            assertThat(job.source()).isEqualTo("LINKEDIN");
            assertThat(job.canonicalUrl()).isEqualTo("https://www.linkedin.com/jobs/view/123456");
            assertThat(job.needsReview()).isTrue();
        });
    }
}
