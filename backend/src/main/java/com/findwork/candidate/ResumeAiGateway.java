package com.findwork.candidate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
public class ResumeAiGateway {
    private static final String SYSTEM_PROMPT = """
            You extract facts from resumes. Return valid JSON only.
            Do not infer facts that are not explicitly supported. Unknown values must be null or []
            and every evidence value must be an exact snippet from the supplied resume text.
            Use experienceLevel values: internship|entry|mid|senior|unknown.
            Return exactly this shape:
            {"summary":null,"experienceLevel":"unknown","yearsOfExperience":null,
            "technicalSkills":[],"targetRoleSuggestions":[],"strengths":[],"evidence":{}}
            """;

    private final RestClient client;
    private final ObjectMapper mapper;
    private final String apiKey;
    private final String model;
    private final String version;
    private final boolean enabled;

    public ResumeAiGateway(RestClient.Builder builder, ObjectMapper mapper, Environment environment) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(30));
        this.client = builder.baseUrl(property(environment, "findwork.ai.base-url", "https://api.deepseek.com/v1"))
                .requestFactory(factory).build();
        this.mapper = mapper;
        this.apiKey = property(environment, "findwork.ai.api-key");
        this.model = property(environment, "findwork.ai.model", "deepseek-chat");
        this.version = property(environment, "findwork.ai.analysis-version", "v1");
        this.enabled = Boolean.parseBoolean(property(environment, "findwork.ai.enabled", "false"));
    }

    public JsonNode analyze(String redactedResumeText) {
        if (!configured()) throw new ResumeAiException("DeepSeek 尚未启用，请设置 DEEPSEEK_ENABLED=true");
        Map<String, Object> request = Map.of(
                "model", model,
                "temperature", 0,
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(
                        Map.of("role", "system", "content", SYSTEM_PROMPT),
                        Map.of("role", "user", "content", redactedResumeText)
                )
        );
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                JsonNode response = client.post().uri("/chat/completions").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + apiKey).body(request).retrieve().body(JsonNode.class);
                String content = response == null ? "" : response.path("choices").path(0).path("message").path("content").asText("");
                return ResumeAnalysisParser.parse(content, redactedResumeText, mapper);
            } catch (RestClientResponseException e) {
                int status = e.getStatusCode().value();
                if (attempt == 0 && (status == 429 || status >= 500)) continue;
                throw new ResumeAiException("DeepSeek 请求失败 HTTP " + status);
            } catch (RestClientException e) {
                if (attempt == 0) continue;
                throw new ResumeAiException("DeepSeek 响应不可用");
            } catch (IllegalArgumentException e) {
                throw new ResumeAiException("简历分析结果不符合约定：" + safeMessage(e));
            }
        }
        throw new ResumeAiException("DeepSeek 响应不可用");
    }

    public String modelName() {
        return model;
    }

    public String analysisVersion() {
        return version;
    }

    public boolean configured() {
        return enabled && !apiKey.isBlank() && !model.isBlank();
    }

    private static String property(Environment environment, String key) {
        return property(environment, key, "");
    }

    private static String property(Environment environment, String key, String fallback) {
        return environment.getProperty(key, fallback).trim();
    }

    private static String safeMessage(IllegalArgumentException error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? "字段校验失败" : message.substring(0, Math.min(message.length(), 120));
    }

    static class ResumeAiException extends RuntimeException {
        ResumeAiException(String message) {
            super(message);
        }
    }
}
