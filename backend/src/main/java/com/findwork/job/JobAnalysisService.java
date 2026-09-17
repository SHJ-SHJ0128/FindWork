package com.findwork.job;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.findwork.candidate.CandidateProfile;
import com.findwork.candidate.CandidateProfileRepository;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

public class JobAnalysisService {
    private final JobPostingRepository jobs;
    private final JobAiAnalysisRepository analyses;
    private final JobMatchResultRepository matches;
    private final CandidateProfileRepository profiles;
    private final AiGateway ai;
    private final ObjectMapper mapper;
    private final int maxPending;
    private final AtomicBoolean running = new AtomicBoolean();

    public JobAnalysisService(JobPostingRepository jobs, JobAiAnalysisRepository analyses,
                              JobMatchResultRepository matches, CandidateProfileRepository profiles,
                              AiGateway ai, ObjectMapper mapper, org.springframework.core.env.Environment environment) {
        this.jobs = jobs;
        this.analyses = analyses;
        this.matches = matches;
        this.profiles = profiles;
        this.ai = ai;
        this.mapper = mapper;
        this.maxPending = Math.max(1, parseInt(environment.getProperty("findwork.ai.max-pending-per-run", "3"), 3));
    }

    public ObjectNode analyze(UUID jobId) {
        JobPosting job = jobs.findById(jobId).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "岗位不存在"));
        String hash = hash(job.description());
        String version = ai instanceof DeepSeekAiGateway gateway ? gateway.analysisVersion() : "v1";
        Optional<JobAiAnalysisRecord> latest = analyses.findLatest(job.id());
        if (latest.isPresent() && "SUCCESS".equals(latest.get().status()) && current(latest.get(), hash, version)) return view(job);
        if (!ai.configured()) throw new ResponseStatusException(SERVICE_UNAVAILABLE, "DeepSeek 尚未配置");
        try {
            analyses.saveAnalyzing(job.id(), hash, version, ai.modelName());
            JobSemanticAnalysis analysis = ai.analyze(job);
            analyses.saveSuccess(job.id(), hash, version, ai.modelName(), analysis);
            calculateMatch(job, analysis);
        } catch (RuntimeException e) {
            analyses.saveFailure(job.id(), hash, version, ai.modelName(), safeError(e));
        }
        return view(job);
    }

    public AnalysisRunResult analyzePending(int limit) {
        if (limit < 1) return new AnalysisRunResult(0, 0, 0, 0);
        if (!ai.configured()) return new AnalysisRunResult(0, 0, 0, limit);
        if (!running.compareAndSet(false, true)) return new AnalysisRunResult(0, 0, 0, limit);
        int scanned = 0;
        int succeeded = 0;
        int failed = 0;
        try {
            int bounded = Math.min(Math.max(1, limit), maxPending);
            for (JobPosting job : jobs.findAll()) {
                if (scanned >= bounded) break;
                if ("DEMO".equals(job.source())) continue;
                String hash = hash(job.description());
                String version = ai instanceof DeepSeekAiGateway gateway ? gateway.analysisVersion() : "v1";
                Optional<JobAiAnalysisRecord> latest = analyses.findLatest(job.id());
                if (latest.isPresent() && "SUCCESS".equals(latest.get().status()) && current(latest.get(), hash, version)) continue;
                scanned++;
                ObjectNode result = analyze(job.id());
                if ("SUCCESS".equals(result.path("analysisStatus").asText())) succeeded++; else failed++;
            }
        } finally {
            running.set(false);
        }
        return new AnalysisRunResult(scanned, succeeded, failed, Math.max(0, limit - scanned));
    }

    public ObjectNode view(JobPosting job) {
        ObjectNode node = mapper.valueToTree(job);
        String hash = hash(job.description());
        String version = ai instanceof DeepSeekAiGateway gateway ? gateway.analysisVersion() : "v1";
        // ponytail: one analysis/match lookup per visible card; batch reads when the local list becomes large.
        JobAiAnalysisRecord analysis = analyses.findLatest(job.id()).orElse(null);
        boolean currentAnalysis = analysis != null && current(analysis, hash, version);
        String status = currentAnalysis ? analysis.status() : "PENDING";
        node.put("analysisStatus", status);
        if (analysis != null && currentAnalysis && analysis.errorMessage() != null) node.put("analysisError", analysis.errorMessage());
        if (analysis != null && currentAnalysis && "SUCCESS".equals(status)) node.set("analysis", mapper.valueToTree(analysis.analysis()));
        else node.putNull("analysis");
        CandidateProfile profile = profiles.find().orElse(null);
        JobMatchResult match = profile == null ? null : matches.findLatest(job.id()).orElse(null);
        if (match != null && profileHash(profile).equals(match.profileHash()) && currentAnalysis && "SUCCESS".equals(status)) {
            node.set("match", mapper.valueToTree(match));
            node.put("matchScore", match.hardFilterPassed() ? match.totalScore() : 0);
        } else {
            node.putNull("match");
            node.putNull("matchScore");
        }
        return node;
    }

    private void calculateMatch(JobPosting job, JobSemanticAnalysis analysis) {
        CandidateProfile profile = profiles.find().orElse(null);
        if (profile == null) return;
        JobMatchResult result = JobMatchEngine.calculate(profile, job, analysis, profileHash(profile), Instant.now());
        matches.save(job.id(), result);
        jobs.updateScore(job.id(), result.hardFilterPassed() ? result.totalScore() : 0);
    }

    public void rematchAll() {
        for (JobPosting job : jobs.findAll()) {
            JobAiAnalysisRecord analysis = analyses.findLatest(job.id()).orElse(null);
            if (analysis != null && "SUCCESS".equals(analysis.status())
                    && current(analysis, hash(job.description()), ai instanceof DeepSeekAiGateway gateway ? gateway.analysisVersion() : "v1")) {
                calculateMatch(job, analysis.analysis());
            }
        }
    }

    private boolean current(JobAiAnalysisRecord analysis, String hash, String version) {
        return hash.equals(analysis.descriptionHash()) && version.equals(analysis.analysisVersion())
                && ai.modelName().equals(analysis.modelName());
    }

    private String profileHash(CandidateProfile profile) {
        try {
            return hash(mapper.writeValueAsString(profile));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("候选人资料无法计算 hash", e);
        }
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    private String safeError(RuntimeException error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) return "AI 分析失败";
        return message.length() > 240 ? message.substring(0, 240) : message;
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public record AnalysisRunResult(int scanned, int succeeded, int failed, int skipped) {
    }
}
