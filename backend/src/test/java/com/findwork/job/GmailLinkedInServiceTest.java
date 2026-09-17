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

    @Test
    void normalizesLinkedInAlertEntryIntoDemoCardFields() {
        var jobs = GmailLinkedInService.parseJobs(
                "有符合您的搜索偏好的新发布职位时，您将收到通知。 前端开发工程师（深圳、杭州、成都） 招银网络科技有限公司 中国 浙江省 杭州 2 位校友 查看职位: https://www.linkedin.com/comm/jobs/view/123456?trk=one " +
                        "--------------------------------------------------------- 【offer来袭！】Web前端研发工程师-大厂 拼多多 中国 上海市 上海市 15 位校友 查看职位: https://www.linkedin.com/jobs/view/789012",
                "LinkedIn jobs for you",
                Instant.parse("2026-09-17T00:00:00Z"));

        assertThat(jobs).hasSize(2);
        assertThat(jobs.get(0)).satisfies(job -> {
            assertThat(job.title()).isEqualTo("前端开发工程师（深圳、杭州、成都）");
            assertThat(job.company()).isEqualTo("招银网络科技有限公司");
            assertThat(job.country()).isEqualTo("China");
            assertThat(job.city()).isEqualTo("杭州");
            assertThat(job.remoteType()).isEqualTo("UNKNOWN");
        });
        assertThat(jobs.get(1)).satisfies(job -> {
            assertThat(job.title()).isEqualTo("Web前端研发工程师-大厂");
            assertThat(job.company()).isEqualTo("拼多多");
            assertThat(job.city()).isEqualTo("上海");
        });
    }

    @Test
    void keepsMultiWordEnglishCompaniesAndRemovesLocationTail() {
        var jobs = GmailLinkedInService.parseJobs(
                "Backend Engineer MeshyAI Shenzhen, Guangdong, China 查看职位: https://www.linkedin.com/jobs/view/123457 " +
                        "--------------------------------------------------------- Frontend Application Engineer HBK - Hottinger Brüel & Kjær 中国 上海市 上海市 查看职位: https://www.linkedin.com/jobs/view/123458",
                "LinkedIn jobs for you",
                Instant.parse("2026-09-17T00:00:00Z"));

        assertThat(jobs).hasSize(2);
        assertThat(jobs.get(0).title()).isEqualTo("Backend Engineer");
        assertThat(jobs.get(0).company()).isEqualTo("MeshyAI");
        assertThat(jobs.get(0).city()).isEqualTo("深圳");
        assertThat(jobs.get(1).title()).isEqualTo("Frontend Application Engineer HBK");
        assertThat(jobs.get(1).company()).isEqualTo("Hottinger Brüel & Kjær");
    }
}
