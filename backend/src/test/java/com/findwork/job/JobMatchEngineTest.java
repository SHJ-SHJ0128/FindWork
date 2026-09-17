package com.findwork.job;

import com.findwork.candidate.CandidateProfile;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JobMatchEngineTest {
    private static final Instant NOW = Instant.parse("2026-09-18T00:00:00Z");
    private final CandidateProfile profile = new CandidateProfile(
            List.of("Backend", "AI Application"), List.of("重庆", "成都", "广州", "深圳", "杭州"),
            List.of("China", "Singapore"), true, "internship-and-entry-level", List.of("Java", "Spring Boot", "SQL"));

    @Test
    void scoresComponentsWithinTheHundredPointContract() {
        JobMatchResult result = JobMatchEngine.calculate(profile, job("Backend Engineer", "China", "重庆", NOW.minusSeconds(3600)), analysis(), "profile", NOW);

        assertThat(result.hardFilterPassed()).isTrue();
        assertThat(result.roleScore()).isEqualTo(30);
        assertThat(result.skillScore()).isEqualTo(25);
        assertThat(result.experienceScore()).isEqualTo(20);
        assertThat(result.locationScore()).isEqualTo(15);
        assertThat(result.totalScore()).isBetween(0, 100);
    }

    @Test
    void hardFiltersUnsupportedCountryAndChinaCity() {
        assertThat(JobMatchEngine.calculate(profile, job("Backend Engineer", "United States", "New York", NOW), analysis(), "p", NOW).hardFilterReasons())
                .contains("LOCATION_NOT_SUPPORTED");
        assertThat(JobMatchEngine.calculate(profile, job("Backend Engineer", "China", "上海", NOW), analysis(), "p", NOW).hardFilterReasons())
                .contains("CITY_NOT_SUPPORTED");
    }

    @Test
    void reportsMissingRequiredSkillsWithoutRandomScore() {
        JobSemanticAnalysis missing = new JobSemanticAnalysis("software_engineering", List.of("Rust"), List.of(), null, null,
                "unknown", "entry", true, "full-time", "onsite", List.of(), null, null, null, Map.of());
        JobMatchResult result = JobMatchEngine.calculate(profile, job("Backend Engineer", "China", "成都", NOW), missing, "p", NOW);

        assertThat(result.missingRequiredSkills()).containsExactly("Rust");
        assertThat(result.concerns()).anyMatch(value -> value.contains("缺少必需技能"));
        assertThat(result.totalScore()).isBetween(0, 100);
    }

    private JobSemanticAnalysis analysis() {
        return new JobSemanticAnalysis("software_engineering", List.of("Java", "Spring Boot"), List.of("SQL"),
                null, null, "bachelor", "entry", true, "full-time", "onsite", List.of(), null, null, null, Map.of());
    }

    private JobPosting job(String title, String country, String city, Instant postedAt) {
        return new JobPosting(null, title, "Example", country, city, "ONSITE", "全职", "应届/初级", "TEST", null,
                "A job description", null, List.of("Java", "Spring Boot", "SQL"), null, null, null, null, null, null, 0, true, postedAt);
    }
}
