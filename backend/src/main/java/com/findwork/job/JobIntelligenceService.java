package com.findwork.job;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

/** On-demand JD organization; deliberately has no candidate matching or score calculation. */
@Service
public class JobIntelligenceService {
    private final JobPostingRepository jobs;
    private final JobAiAnalysisRepository analyses;
    private final AiGateway ai;
    private final ObjectMapper mapper;

    public JobIntelligenceService(JobPostingRepository jobs, JobAiAnalysisRepository analyses,
                                  AiGateway ai, ObjectMapper mapper) {
        this.jobs = jobs;
        this.analyses = analyses;
        this.ai = ai;
        this.mapper = mapper;
    }

    public ObjectNode analyze(UUID jobId) {
        JobPosting job = find(jobId);
        String hash = hash(job.description());
        String version = version();
        Optional<JobAiAnalysisRecord> latest = analyses.findLatest(job.id());
        if (latest.isPresent() && "SUCCESS".equals(latest.get().status()) && current(latest.get(), hash, version)) {
            return view(job, latest.get());
        }
        if (!ai.configured()) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "DeepSeek 尚未启用，请设置 DEEPSEEK_ENABLED=true");
        }
        analyses.saveAnalyzing(job.id(), hash, version, ai.modelName());
        try {
            analyses.saveSuccess(job.id(), hash, version, ai.modelName(), ai.analyze(job));
        } catch (RuntimeException error) {
            analyses.saveFailure(job.id(), hash, version, ai.modelName(), safeError(error));
        }
        return view(job, analyses.findLatest(job.id()).orElse(null));
    }

    public ObjectNode current(UUID jobId) {
        JobPosting job = find(jobId);
        JobAiAnalysisRecord latest = analyses.findLatest(job.id()).orElse(null);
        return view(job, latest);
    }

    private JobPosting find(UUID jobId) {
        return jobs.findById(jobId).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "岗位不存在"));
    }

    private ObjectNode view(JobPosting job, JobAiAnalysisRecord record) {
        ObjectNode node = mapper.createObjectNode();
        node.put("jobId", job.id().toString());
        String hash = hash(job.description());
        boolean current = record != null && current(record, hash, version());
        node.put("analysisStatus", current ? record.status() : "PENDING");
        node.put("modelName", ai.modelName());
        if (current && record.analyzedAt() != null) node.put("analyzedAt", record.analyzedAt().toString());
        else node.putNull("analyzedAt");
        if (current && record.errorMessage() != null) node.put("analysisError", record.errorMessage());
        else node.putNull("analysisError");
        if (current && "SUCCESS".equals(record.status()) && record.analysis() != null) {
            node.set("analysis", mapper.valueToTree(record.analysis()));
        } else {
            node.putNull("analysis");
        }
        return node;
    }

    private boolean current(JobAiAnalysisRecord record, String hash, String version) {
        return hash.equals(record.descriptionHash())
                && version.equals(record.analysisVersion())
                && ai.modelName().equals(record.modelName());
    }

    private String version() {
        return ai instanceof DeepSeekAiGateway gateway ? gateway.analysisVersion() : "v1";
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) {
            throw new IllegalStateException("岗位描述 hash 计算失败", error);
        }
    }

    private String safeError(RuntimeException error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) return "AI 分析失败";
        return message.length() > 240 ? message.substring(0, 240) : message;
    }
}
