package com.findwork.job;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

final class SemanticAnalysisParser {
    private static final Set<String> JOB_CATEGORIES = Set.of("software_engineering", "solutions_engineering", "data", "security", "product", "other");
    private static final Set<String> SENIORITY = Set.of("intern", "entry", "mid", "senior", "staff", "principal", "manager", "director", "unknown");
    private static final Set<String> EMPLOYMENT = Set.of("full-time", "part-time", "contract", "internship", "unknown");
    private static final Set<String> WORKPLACE = Set.of("remote", "hybrid", "onsite", "unknown");
    private static final Set<String> EDUCATION = Set.of("none", "high_school", "associate", "bachelor", "master", "phd", "unknown");
    private static final Set<String> FIELDS = Set.of("jobCategory", "requiredSkills", "preferredSkills",
            "minimumExperienceYears", "maximumExperienceYears", "educationLevel", "seniorityLevel",
            "graduateFriendly", "employmentType", "workplaceType", "responsibilities",
            "workAuthorizationRequired", "visaSponsorship", "salary", "evidence");

    private SemanticAnalysisParser() {
    }

    static JobSemanticAnalysis parse(String content, String description, ObjectMapper mapper) {
        JsonNode root = readJson(content, mapper);
        if (root == null || !root.isObject()) throw new IllegalArgumentException("AI 响应不是 JSON 对象");
        root = normalizeCommonShapes((ObjectNode) root.deepCopy(), mapper);
        root.fieldNames().forEachRemaining(field -> {
            if (!FIELDS.contains(field)) throw new IllegalArgumentException("AI 响应包含未知字段: " + field);
        });
        String category = enumValue(root, "jobCategory", JOB_CATEGORIES);
        String seniority = enumValue(root, "seniorityLevel", SENIORITY);
        String employment = enumValue(root, "employmentType", EMPLOYMENT);
        String workplace = enumValue(root, "workplaceType", WORKPLACE);
        String education = enumValue(root, "educationLevel", EDUCATION);
        List<String> required = strings(root.path("requiredSkills"), 40, 120);
        List<String> preferred = strings(root.path("preferredSkills"), 40, 120);
        List<String> responsibilities = strings(root.path("responsibilities"), 20, 500);
        Integer minimum = integer(root, "minimumExperienceYears");
        Integer maximum = integer(root, "maximumExperienceYears");
        if (minimum != null && maximum != null && minimum > maximum) throw new IllegalArgumentException("经验年限范围无效");
        JsonNode evidenceNode = root.path("evidence");
        Map<String, Object> evidence = evidenceNode.isObject()
                ? mapper.convertValue(evidenceNode, new TypeReference<>() {}) : Map.of();
        validateEvidence(evidenceNode, description);
        JsonNode salary = root.path("salary").isObject() ? root.path("salary") : null;
        return new JobSemanticAnalysis(
                category, required, preferred, minimum, maximum, education, seniority,
                nullableBoolean(root, "graduateFriendly"), employment, workplace, responsibilities,
                nullableBoolean(root, "workAuthorizationRequired"), nullableBoolean(root, "visaSponsorship"), salary, evidence);
    }

    private static ObjectNode normalizeCommonShapes(ObjectNode root, ObjectMapper mapper) {
        for (String field : List.of("requiredSkills", "preferredSkills", "responsibilities")) {
            JsonNode value = root.get(field);
            if (value != null && value.isTextual()) {
                var array = mapper.createArrayNode();
                for (String item : value.asText().split("[,，;；\\n]")) {
                    if (!item.isBlank()) array.add(item.trim());
                }
                root.set(field, array);
            }
        }
        JsonNode evidence = root.get("evidence");
        if (evidence != null && evidence.isTextual()) {
            ObjectNode object = mapper.createObjectNode();
            object.put("text", evidence.asText());
            root.set("evidence", object);
        }
        return root;
    }

    private static JsonNode readJson(String content, ObjectMapper mapper) {
        if (content == null || content.isBlank()) throw new IllegalArgumentException("AI 响应为空");
        String value = content.trim();
        if (value.startsWith("```") && value.endsWith("```")) {
            value = value.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "").trim();
        }
        if (!(value.startsWith("{") && value.endsWith("}"))) throw new IllegalArgumentException("AI 响应包含额外文本");
        try {
            return mapper.readTree(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("AI JSON 无法解析", e);
        }
    }

    private static String enumValue(JsonNode root, String field, Set<String> allowed) {
        JsonNode value = root.get(field);
        if (value == null || value.isNull()) return null;
        if (!value.isTextual() || !allowed.contains(value.asText())) throw new IllegalArgumentException("AI 枚举字段无效: " + field);
        return value.asText();
    }

    private static List<String> strings(JsonNode node, int maxItems, int maxLength) {
        if (node == null || node.isMissingNode() || node.isNull()) return List.of();
        if (!node.isArray() || node.size() > maxItems) throw new IllegalArgumentException("AI 数组字段无效");
        java.util.ArrayList<String> result = new java.util.ArrayList<>();
        for (JsonNode item : node) {
            if (!item.isTextual() || item.asText().isBlank() || item.asText().length() > maxLength) {
                throw new IllegalArgumentException("AI 文本数组字段无效");
            }
            result.add(item.asText().trim());
        }
        return List.copyOf(result);
    }

    private static Integer integer(JsonNode root, String field) {
        JsonNode value = root.get(field);
        if (value == null || value.isNull()) return null;
        if (!value.canConvertToInt() || value.asInt() < 0 || value.asInt() > 80) throw new IllegalArgumentException("AI 经验字段无效");
        return value.asInt();
    }

    private static Boolean nullableBoolean(JsonNode root, String field) {
        JsonNode value = root.get(field);
        if (value == null || value.isNull()) return null;
        if (!value.isBoolean()) throw new IllegalArgumentException("AI 布尔字段无效: " + field);
        return value.asBoolean();
    }

    private static void validateEvidence(JsonNode evidence, String description) {
        if (evidence == null || evidence.isMissingNode() || evidence.isNull()) return;
        if (!evidence.isObject()) throw new IllegalArgumentException("AI evidence 必须是对象");
        String source = normalize(description);
        validateEvidenceNode(evidence, source);
    }

    private static void validateEvidenceNode(JsonNode node, String normalizedDescription) {
        if (node.isTextual()) {
            String evidence = normalize(node.asText());
            if (!evidence.isBlank() && !normalizedDescription.contains(evidence)) {
                throw new IllegalArgumentException("AI evidence 不存在于原始 JD");
            }
            return;
        }
        if (node.isContainerNode()) {
            for (JsonNode child : node) validateEvidenceNode(child, normalizedDescription);
        }
    }

    private static String normalize(String value) {
        return Pattern.compile("\\s+").matcher(value == null ? "" : value).replaceAll(" ").trim().toLowerCase();
    }
}
