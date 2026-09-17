package com.findwork.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JobIntelligenceServiceTest {
    @Test
    void analyzesOnDemandWithoutReturningMatchScore() {
        UUID id = UUID.randomUUID();
        JobPosting job = new JobPosting(id, "Backend Engineer", "Example", "China", "重庆", "ONSITE", "全职", "应届/初级",
                "TEST", "https://example.com/job", "Java Spring Boot experience required", null, List.of("Java"),
                null, null, null, null, null, null, 0, true, Instant.now());
        JobSemanticAnalysis semantic = new JobSemanticAnalysis("software_engineering", List.of("Java"), List.of(), null, null,
                "unknown", "entry", true, "full-time", "onsite", List.of("Build APIs"), null, null, null, Map.of());
        FakeAnalyses analyses = new FakeAnalyses();
        FakeGateway gateway = new FakeGateway(semantic);
        JobPostingRepository jobs = new JobPostingRepository(null, new ObjectMapper()) {
            @Override public Optional<JobPosting> findById(UUID value) { return Optional.of(job); }
        };

        JobIntelligenceService service = new JobIntelligenceService(jobs, analyses, gateway, new ObjectMapper());

        var result = service.analyze(id);
        assertThat(result.path("analysisStatus").asText()).isEqualTo("SUCCESS");
        assertThat(result.has("matchScore")).isFalse();
        assertThat(gateway.calls).isEqualTo(1);
        assertThat(service.analyze(id).path("analysisStatus").asText()).isEqualTo("SUCCESS");
        assertThat(gateway.calls).isEqualTo(1);
    }

    private static final class FakeGateway implements AiGateway {
        private final JobSemanticAnalysis result;
        private int calls;

        private FakeGateway(JobSemanticAnalysis result) { this.result = result; }
        @Override public JobSemanticAnalysis analyze(JobPosting job) { calls++; return result; }
        @Override public String modelName() { return "mock-model"; }
        @Override public boolean configured() { return true; }
    }

    private static final class FakeAnalyses extends JobAiAnalysisRepository {
        private JobAiAnalysisRecord cached;

        private FakeAnalyses() { super(null, new ObjectMapper()); }
        @Override public Optional<JobAiAnalysisRecord> findLatest(UUID jobId) { return Optional.ofNullable(cached); }
        @Override public void saveAnalyzing(UUID jobId, String hash, String version, String model) {
            cached = new JobAiAnalysisRecord(UUID.randomUUID(), jobId, hash, version, model, "ANALYZING", null, null, Instant.now());
        }
        @Override public void saveSuccess(UUID jobId, String hash, String version, String model, JobSemanticAnalysis analysis) {
            cached = new JobAiAnalysisRecord(UUID.randomUUID(), jobId, hash, version, model, "SUCCESS", analysis, null, Instant.now());
        }
        @Override public void saveFailure(UUID jobId, String hash, String version, String model, String error) {
            cached = new JobAiAnalysisRecord(UUID.randomUUID(), jobId, hash, version, model, "FAILED", null, error, Instant.now());
        }
    }
}
