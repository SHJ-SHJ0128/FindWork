package com.findwork.candidate;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class CandidateResumeService {
    private static final long MAX_BYTES = 10L * 1024 * 1024;
    private static final Set<String> APPLY_FIELDS = Set.of("skills", "targetRoleFamilies", "experienceLevel");

    private final CandidateResumeRepository resumes;
    private final CandidateProfileRepository profiles;
    private final ResumeTextExtractor extractor;
    private final ResumeRedactor redactor;
    private final ResumeAiGateway ai;
    private final Path storageRoot;

    public CandidateResumeService(CandidateResumeRepository resumes, CandidateProfileRepository profiles,
                                  ResumeTextExtractor extractor, ResumeRedactor redactor, ResumeAiGateway ai,
                                  Environment environment) {
        this.resumes = resumes;
        this.profiles = profiles;
        this.extractor = extractor;
        this.redactor = redactor;
        this.ai = ai;
        this.storageRoot = Path.of(environment.getProperty("findwork.resume.storage-dir", "storage/resumes"));
    }

    @Transactional
    public CandidateResume upload(MultipartFile file) {
        validate(file);
        String filename = safeFilename(file.getOriginalFilename());
        byte[] content = read(file);
        validateSignature(filename, content);
        String hash = sha256(content);
        Optional<CandidateResume> existing = resumes.findIdByHash(hash).flatMap(resumes::findById);
        if (existing.isPresent()) {
            resumes.markCurrent(existing.get().id());
            return resumes.findById(existing.get().id()).orElse(existing.get());
        }
        String text = extractor.extract(filename, content);
        if (text.isBlank()) {
            String message = filename.toLowerCase(Locale.ROOT).endsWith(".pdf")
                    ? "当前 PDF 无法提取有效文字。请上传可复制文本的 PDF 或 DOCX 简历。"
                    : "简历未提取到有效文字，请检查文件内容。";
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, message);
        }
        UUID id = UUID.randomUUID();
        Path path = storageRoot.resolve(id + extension(filename));
        try {
            Files.createDirectories(storageRoot);
            Files.write(path, content);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "简历文件无法保存到本机", e);
        }
        resumes.save(new CandidateResumeRepository.CandidateResumeDraft(id, filename, mediaType(filename), content.length,
                hash, path.toString(), text, ai.configured() ? "ANALYZING" : "EXTRACTED", null));
        if (ai.configured()) {
            try {
                analyzeStored(id, text);
            } catch (ResumeAiGateway.ResumeAiException e) {
                markFailed(id, e.getMessage());
            }
        }
        return resumes.findById(id).orElseThrow();
    }

    public CandidateResume analyze(UUID id) {
        CandidateResume resume = get(id);
        if (!ai.configured()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "DeepSeek 尚未启用，请设置 DEEPSEEK_ENABLED=true");
        try {
            analyzeStored(id, resume.extractedText());
            return resumes.findById(id).orElseThrow();
        } catch (ResumeAiGateway.ResumeAiException e) {
            markFailed(id, e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, e.getMessage(), e);
        }
    }

    public Optional<CandidateResume> current() {
        return resumes.findCurrent();
    }

    public CandidateResume get(UUID id) {
        return resumes.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "简历不存在"));
    }

    @Transactional
    public CandidateProfile apply(UUID id, Set<String> fields) {
        CandidateResume resume = get(id);
        if (resume.analysis() == null || !"SUCCESS".equals(resume.analysis().status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "请先完成简历分析");
        }
        Set<String> selected = fields == null ? Set.of() : fields;
        if (selected.isEmpty() || !APPLY_FIELDS.containsAll(selected)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择有效的资料字段");
        }
        JsonNode analysis = resume.analysis().analysis();
        CandidateProfile current = profiles.find().orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "请先保存候选人资料"));
        List<String> roles = selected.contains("targetRoleFamilies")
                ? merge(current.targetRoleFamilies(), strings(analysis, "targetRoleSuggestions")) : current.targetRoleFamilies();
        List<String> skills = selected.contains("skills")
                ? merge(current.skills(), strings(analysis, "technicalSkills")) : current.skills();
        String experience = selected.contains("experienceLevel") && analysis.path("experienceLevel").isTextual()
                && !"unknown".equals(analysis.path("experienceLevel").asText())
                ? analysis.path("experienceLevel").asText() : current.experienceLevel();
        CandidateProfile updated = new CandidateProfile(roles, current.allowedCities(), current.preferredRegions(),
                current.remoteAllowed(), experience, skills);
        profiles.save(updated);
        return updated;
    }

    @Transactional
    public void delete(UUID id) {
        CandidateResume resume = get(id);
        resumes.delete(id);
        try {
            Files.deleteIfExists(Path.of(resume.storagePath()));
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "简历记录已删除，但本机文件清理失败", e);
        }
    }

    private void analyzeStored(UUID id, String text) {
        resumes.saveAnalysis(id, ai.analysisVersion(), ai.modelName(), "ANALYZING", null, null);
        JsonNode analysis = ai.analyze(redactor.redact(text));
        resumes.saveAnalysis(id, ai.analysisVersion(), ai.modelName(), "SUCCESS", analysis, null);
        resumes.updateStatus(id, "SUCCESS", null);
    }

    private void markFailed(UUID id, String error) {
        resumes.saveAnalysis(id, ai.analysisVersion(), ai.modelName(), "FAILED", null, error);
        resumes.updateStatus(id, "FAILED", error);
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请上传 PDF 或 DOCX 简历");
        if (file.getSize() > MAX_BYTES) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "简历文件不能超过 10MB");
        String filename = safeFilename(file.getOriginalFilename());
        if (!(filename.toLowerCase(Locale.ROOT).endsWith(".pdf") || filename.toLowerCase(Locale.ROOT).endsWith(".docx"))) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "仅支持 PDF 或 DOCX 简历");
        }
    }

    private byte[] read(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "简历文件无法读取", e);
        }
    }

    private void validateSignature(String filename, byte[] content) {
        String lower = filename.toLowerCase(Locale.ROOT);
        boolean valid = lower.endsWith(".pdf") ? new String(content, 0, Math.min(content.length, 5), java.nio.charset.StandardCharsets.US_ASCII).startsWith("%PDF-")
                : content.length >= 2 && content[0] == 'P' && content[1] == 'K';
        if (!valid) throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "文件内容与扩展名不匹配");
    }

    private String safeFilename(String value) {
        if (value == null || value.isBlank()) return "resume.pdf";
        String name = Path.of(value).getFileName().toString();
        return name.length() > 120 ? name.substring(name.length() - 120) : name;
    }

    private String extension(String filename) {
        return filename.toLowerCase(Locale.ROOT).endsWith(".docx") ? ".docx" : ".pdf";
    }

    private String mediaType(String filename) {
        return extension(filename).equals(".docx")
                ? "application/vnd.openxmlformats-officedocument.wordprocessingml.document" : "application/pdf";
    }

    private String sha256(byte[] content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte value : digest) result.append(String.format("%02x", value));
            return result.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    private List<String> strings(JsonNode root, String field) {
        List<String> values = new ArrayList<>();
        root.path(field).forEach(node -> { if (node.isTextual() && !node.asText().isBlank()) values.add(node.asText().trim()); });
        return values;
    }

    private List<String> merge(List<String> current, List<String> additions) {
        LinkedHashSet<String> merged = new LinkedHashSet<>(current == null ? List.of() : current);
        merged.addAll(additions);
        return List.copyOf(merged);
    }

}
