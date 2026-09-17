package com.findwork.candidate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

final class ResumeAnalysisParser {
    private static final Set<String> FIELDS = Set.of("summary", "experienceLevel", "yearsOfExperience",
            "technicalSkills", "targetRoleSuggestions", "strengths", "evidence");
    private static final Set<String> LEVELS = Set.of("internship", "entry", "mid", "senior", "unknown");

    private ResumeAnalysisParser() {
    }

    static JsonNode parse(String content, String sourceText, ObjectMapper mapper) {
        if (content == null || content.isBlank()) throw new IllegalArgumentException("AI 响应为空");
        String value = content.trim();
        if (value.startsWith("```") && value.endsWith("```")) {
            value = value.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "").trim();
        }
        try {
            JsonNode node = mapper.readTree(value);
            if (node == null || !node.isObject()) throw new IllegalArgumentException("AI 响应不是 JSON 对象");
            ObjectNode root = (ObjectNode) node;
            root.fieldNames().forEachRemaining(field -> {
                if (!FIELDS.contains(field)) throw new IllegalArgumentException("AI 响应包含未知字段: " + field);
            });
            text(root, "summary", 1200);
            enumValue(root, "experienceLevel", LEVELS);
            integer(root, "yearsOfExperience");
            strings(root.path("technicalSkills"), 100, 80);
            strings(root.path("targetRoleSuggestions"), 8, 120);
            strings(root.path("strengths"), 12, 240);
            validateEvidence(root.path("evidence"), sourceText);
            return root;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("AI JSON 无法解析", e);
        }
    }

    private static void text(ObjectNode root, String field, int maxLength) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull()) return;
        if (!node.isTextual() || node.asText().length() > maxLength) throw new IllegalArgumentException("AI 文本字段无效: " + field);
    }

    private static void enumValue(ObjectNode root, String field, Set<String> allowed) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull()) return;
        if (!node.isTextual() || !allowed.contains(node.asText())) throw new IllegalArgumentException("AI 枚举字段无效: " + field);
    }

    private static void integer(ObjectNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull()) return;
        if (!node.canConvertToInt() || node.asInt() < 0 || node.asInt() > 80) throw new IllegalArgumentException("AI 数字字段无效: " + field);
    }

    private static List<String> strings(JsonNode node, int maxItems, int maxLength) {
        if (node == null || node.isMissingNode() || node.isNull()) return List.of();
        if (!node.isArray() || node.size() > maxItems) throw new IllegalArgumentException("AI 数组字段无效");
        List<String> result = new ArrayList<>();
        for (JsonNode item : node) {
            if (!item.isTextual() || item.asText().isBlank() || item.asText().length() > maxLength) throw new IllegalArgumentException("AI 文本数组字段无效");
            result.add(item.asText().trim());
        }
        return List.copyOf(result);
    }

    private static void validateEvidence(JsonNode evidence, String sourceText) {
        if (evidence == null || evidence.isMissingNode() || evidence.isNull()) return;
        if (!evidence.isObject()) throw new IllegalArgumentException("AI evidence 必须是对象");
        String source = normalize(sourceText);
        validateEvidenceNode(evidence, source);
    }

    private static void validateEvidenceNode(JsonNode node, String source) {
        if (node.isTextual()) {
            String evidence = normalize(node.asText());
            if (!evidence.isBlank() && !source.contains(evidence)) throw new IllegalArgumentException("AI evidence 不存在于简历原文");
            return;
        }
        if (node.isContainerNode()) node.elements().forEachRemaining(child -> validateEvidenceNode(child, source));
    }

    private static String normalize(String value) {
        return Pattern.compile("\\s+").matcher(value == null ? "" : value).replaceAll(" ").trim().toLowerCase();
    }
}
