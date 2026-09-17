package com.findwork.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.findwork.candidate.CandidateProfileRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JobAnalysisServiceTest {
    @Test
    void usesMockedGatewayAndCachesCurrentSuccessfulAnalysis() {
        UUID id = UUID.randomUUID();
        JobPosting job = new JobPosting(id, "Backend Engineer", "Example", "China", "重庆", "ONSITE", "全职", "应届/初级",
                "TEST", null, "Java service description", null, List.of("Java"), null, null, null, null, null, null, 0, true, Instant.now());
        JobSemanticAnalysis semantic = new JobSemanticAnalysis("software_engineering", List.of("Java"), List.of(), null, null,
                "unknown", "entry", true, "full-time", "onsite", List.of(), null, null, null, Map.of());
        FakeGateway gateway = new FakeGateway(semantic);
        JobPostingRepository jobs = new JobPostingRepository(null, new ObjectMapper()) {
            @Override public Optional<JobPosting> findById(UUID value) { return Optional.of(job); }
        };
        FakeAnalyses analyses = new FakeAnalyses();
        JobAiAnalysisRecord cached = new JobAiAnalysisRecord(UUID.randomUUID(), id, sha256(job.description()), "v1", "mock-model",
                "SUCCESS", semantic, null, Instant.now());
        analyses.cached = cached;
        analyses.firstLookup = true;
        CandidateProfileRepository profiles = new CandidateProfileRepository(null, new ObjectMapper()) {
            @Override public Optional<com.findwork.candidate.CandidateProfile> find() { return Optional.empty(); }
        };
        JobMatchResultRepository matches = new JobMatchResultRepository(null, new ObjectMapper()) {
            @Override public Optional<JobMatchResult> findLatest(UUID value) { return Optional.empty(); }
        };

        JobAnalysisService service = new JobAnalysisService(jobs, analyses, matches, profiles,
                gateway, new ObjectMapper().registerModule(new JavaTimeModule()), new MockEnvironment());

        assertThat(service.analyze(id).path("analysisStatus").asText()).isEqualTo("SUCCESS");
        assertThat(service.analyze(id).path("analysisStatus").asText()).isEqualTo("SUCCESS");
        assertThat(gateway.calls).isEqualTo(1);
        assertThat(analyses.saved).isEqualTo(1);
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
        private boolean firstLookup;
        private int saved;

        private FakeAnalyses() { super(null, new ObjectMapper()); }
        @Override public Optional<JobAiAnalysisRecord> findLatest(UUID jobId) {
            if (firstLookup) { firstLookup = false; return Optional.empty(); }
            return Optional.ofNullable(cached);
        }
        @Override public void saveAnalyzing(UUID jobId, String hash, String version, String model) { }
        @Override public void saveSuccess(UUID jobId, String hash, String version, String model, JobSemanticAnalysis analysis) {
            saved++;
            cached = new JobAiAnalysisRecord(UUID.randomUUID(), jobId, hash, version, model, "SUCCESS", analysis, null, Instant.now());
        }
        @Override public void saveFailure(UUID jobId, String hash, String version, String model, String error) { }
    }

    private static String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
