package com.findwork.job;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.mockito.Mockito.*;

class JobAnalysisServiceTest {
    @Test
    void usesMockedGatewayAndCachesCurrentSuccessfulAnalysis() {
        UUID id = UUID.randomUUID();
        JobPosting job = new JobPosting(id, "Backend Engineer", "Example", "China", "重庆", "ONSITE", "全职", "应届/初级",
                "TEST", null, "Java service description", null, List.of("Java"), null, null, null, null, null, null, 0, true, Instant.now());
        JobSemanticAnalysis semantic = new JobSemanticAnalysis("software_engineering", List.of("Java"), List.of(), null, null,
                "unknown", "entry", true, "full-time", "onsite", List.of(), null, null, null, Map.of());
        AiGateway gateway = mock(AiGateway.class);
        when(gateway.configured()).thenReturn(true);
        when(gateway.modelName()).thenReturn("mock-model");
        when(gateway.analyze(job)).thenReturn(semantic);

        JobPostingRepository jobs = mock(JobPostingRepository.class);
        when(jobs.findById(id)).thenReturn(Optional.of(job));
        JobAiAnalysisRepository analyses = mock(JobAiAnalysisRepository.class);
        JobAiAnalysisRecord cached = new JobAiAnalysisRecord(UUID.randomUUID(), id, sha256(job.description()), "v1", "mock-model",
                "SUCCESS", semantic, null, Instant.now());
        when(analyses.findLatest(id)).thenReturn(Optional.empty(), Optional.of(cached), Optional.of(cached));
        CandidateProfileRepository profiles = mock(CandidateProfileRepository.class);
        when(profiles.find()).thenReturn(Optional.empty());

        JobAnalysisService service = new JobAnalysisService(jobs, analyses, mock(JobMatchResultRepository.class), profiles,
                gateway, new ObjectMapper(), new MockEnvironment());

        assertThat(service.analyze(id).path("analysisStatus").asText()).isEqualTo("SUCCESS");
        assertThat(service.analyze(id).path("analysisStatus").asText()).isEqualTo("SUCCESS");
        verify(gateway, times(1)).analyze(job);
        verify(analyses, times(1)).saveSuccess(eq(id), eq(sha256(job.description())), eq("v1"), eq("mock-model"), eq(semantic));
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
