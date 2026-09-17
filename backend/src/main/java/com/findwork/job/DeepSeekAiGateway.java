package com.findwork.job;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestClientException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
public class DeepSeekAiGateway implements AiGateway {
    private static final String SYSTEM_PROMPT = """
            You are a job posting semantic analysis engine.
            Return valid JSON only. Extract only information explicitly supported by the job posting.
            Unknown values must be null. Do not infer onsite because remote is not mentioned.
            Distinguish required skills from preferred skills. Preserve evidence from the original text.
            Do not generate a candidate match score. Do not make employment eligibility conclusions without explicit evidence.
            Salary must only be extracted when supported by explicit text.
            Use these enums when applicable: jobCategory=software_engineering|solutions_engineering|data|security|product|other;
            seniorityLevel=intern|entry|mid|senior|staff|principal|manager|director|unknown;
            employmentType=full-time|part-time|contract|internship|unknown;
            workplaceType=remote|hybrid|onsite|unknown; educationLevel=none|high_school|associate|bachelor|master|phd|unknown.
            Evidence values must be exact snippets from the supplied job description.
            Output this exact shape; arrays must stay JSON arrays and evidence must stay an object:
            {"jobCategory":null,"requiredSkills":[],"preferredSkills":[],"minimumExperienceYears":null,"maximumExperienceYears":null,"educationLevel":null,"seniorityLevel":null,"graduateFriendly":null,"employmentType":null,"workplaceType":null,"responsibilities":[],"workAuthorizationRequired":null,"visaSponsorship":null,"salary":null,"evidence":{}}
            """;

    private final RestClient client;
    private final ObjectMapper mapper;
    private final String apiKey;
    private final String model;
    private final String analysisVersion;
    private final int maxDescriptionChars;
    private final boolean enabled;

    public DeepSeekAiGateway(RestClient.Builder builder, ObjectMapper mapper, Environment environment) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(30));
        String baseUrl = property(environment, "findwork.ai.base-url", "https://api.deepseek.com/v1");
        this.client = builder.baseUrl(baseUrl).requestFactory(factory).build();
        this.mapper = mapper;
        this.apiKey = property(environment, "findwork.ai.api-key");
        this.model = property(environment, "findwork.ai.model", "deepseek-chat");
        this.analysisVersion = property(environment, "findwork.ai.analysis-version", "v1");
        this.maxDescriptionChars = Math.max(1000, parseInt(property(environment, "findwork.ai.max-description-chars", "12000"), 12000));
        this.enabled = Boolean.parseBoolean(property(environment, "findwork.ai.enabled", "true"));
    }

    @Override
    public JobSemanticAnalysis analyze(JobPosting job) {
        if (!configured()) throw new AiGatewayException("DeepSeek 未配置");
        String description = compact(job.description());
        String location = job.city() != null ? job.city() : job.country() != null ? job.country() : "未知";
        Map<String, Object> user = Map.of(
                "title", job.title(),
                "company", job.company(),
                "location", location,
                "description", description
        );
        Map<String, Object> request = Map.of(
                "model", model,
                "temperature", 0,
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(
                        Map.of("role", "system", "content", SYSTEM_PROMPT),
                        Map.of("role", "user", "content", toJson(user))
                )
        );
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                JsonNode response = client.post()
                        .uri("/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + apiKey)
                        .body(request)
                        .retrieve()
                        .body(JsonNode.class);
                String content = response == null ? "" : response.path("choices").path(0).path("message").path("content").asText("");
                return SemanticAnalysisParser.parse(content, job.description(), mapper);
            } catch (RestClientResponseException e) {
                int status = e.getStatusCode().value();
                if (attempt == 0 && (status == 429 || status >= 500)) continue;
                throw new AiGatewayException("DeepSeek 请求失败 HTTP " + status);
            } catch (RestClientException e) {
                if (attempt == 0) continue;
                throw new AiGatewayException("DeepSeek 响应不可用");
            } catch (IllegalArgumentException e) {
                throw new AiGatewayException("DeepSeek JSON 不符合约定：" + safeParserMessage(e));
            }
        }
        throw new AiGatewayException("DeepSeek 响应不可用");
    }

    @Override
    public String modelName() {
        return model;
    }

    @Override
    public boolean configured() {
        return enabled && !apiKey.isBlank() && !model.isBlank();
    }

    String analysisVersion() {
        return analysisVersion;
    }

    private String compact(String value) {
        if (value == null) return "";
        String cleaned = value.replaceAll("(?is)cookie|privacy policy|unsubscribe|all rights reserved", " ")
                .replaceAll("(?i)\\b[\\w.+-]+@[\\w.-]+\\.[A-Za-z]{2,}\\b", "[redacted-email]")
                .replaceAll("\\s+", " ").trim();
        return cleaned.length() <= maxDescriptionChars ? cleaned : cleaned.substring(0, maxDescriptionChars) + "…";
    }

    private String toJson(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new AiGatewayException("AI 请求序列化失败");
        }
    }

    private static String property(Environment environment, String key) {
        return property(environment, key, "");
    }

    private static String property(Environment environment, String key, String fallback) {
        return environment.getProperty(key, fallback).trim();
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String safeParserMessage(IllegalArgumentException error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) return "字段校验失败";
        return message.length() <= 120 ? message : message.substring(0, 120);
    }

    static class AiGatewayException extends RuntimeException {
        AiGatewayException(String message) {
            super(message);
        }
    }
}
