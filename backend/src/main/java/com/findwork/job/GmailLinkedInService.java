package com.findwork.job;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

@Service
public class GmailLinkedInService {
    private static final Logger LOG = Logger.getLogger(GmailLinkedInService.class.getName());
    private static final String READ_ONLY_SCOPE = "https://www.googleapis.com/auth/gmail.readonly";
    private static final Pattern JOB_URL = Pattern.compile(
            "https?://(?:www\\.)?linkedin\\.com/(?:comm/)?jobs/view/(\\d+)[^\\s\\\"'<>)]*",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern HTML_TAG = Pattern.compile("(?s)<[^>]*>");

    private final RestClient client;
    private final JobPostingRepository repository;
    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final String envRefreshToken;
    private final String envAccessToken;
    private final Path tokenFile;
    private final String query;
    private final int maxResults;
    private final boolean enabled;
    private final AtomicReference<OAuthState> pendingState = new AtomicReference<>();

    public GmailLinkedInService(RestClient.Builder builder, JobPostingRepository repository, Environment environment) {
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(20));
        this.client = builder.requestFactory(factory).build();
        this.repository = repository;
        this.clientId = property(environment, "findwork.gmail.client-id");
        this.clientSecret = property(environment, "findwork.gmail.client-secret");
        this.redirectUri = property(environment, "findwork.gmail.redirect-uri", "http://127.0.0.1:8080/api/providers/gmail/callback");
        this.envRefreshToken = property(environment, "findwork.gmail.refresh-token");
        this.envAccessToken = property(environment, "findwork.gmail.access-token");
        this.tokenFile = Path.of(property(environment, "findwork.gmail.token-file", "storage/gmail-refresh-token"));
        this.query = property(environment, "findwork.gmail.linkedin-query", "from:(linkedin.com) newer_than:7d");
        this.maxResults = Math.min(500, Math.max(1, parseInt(property(environment, "findwork.gmail.max-results", "100"), 100)));
        this.enabled = Boolean.parseBoolean(property(environment, "findwork.gmail.enabled", "true"));
    }

    public boolean oauthConfigured() {
        return !clientId.isBlank() && !clientSecret.isBlank();
    }

    public URI beginAuthorization() {
        if (!enabled) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "LinkedIn Gmail 同步已关闭");
        }
        if (!oauthConfigured()) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "请先配置 GMAIL_CLIENT_ID 和 GMAIL_CLIENT_SECRET");
        }
        String state = UUID.randomUUID().toString();
        pendingState.set(new OAuthState(state, Instant.now().plus(Duration.ofMinutes(10))));
        String url = "https://accounts.google.com/o/oauth2/v2/auth?" +
                "client_id=" + encode(clientId) +
                "&redirect_uri=" + encode(redirectUri) +
                "&response_type=code" +
                "&access_type=offline" +
                "&prompt=consent" +
                "&scope=" + encode(READ_ONLY_SCOPE) +
                "&state=" + encode(state);
        return URI.create(url);
    }

    public boolean consumeState(String state) {
        OAuthState expected = pendingState.getAndSet(null);
        return expected != null && expected.value().equals(state) && Instant.now().isBefore(expected.expiresAt());
    }

    public void exchangeAuthorizationCode(String code) {
        if (!oauthConfigured() || code == null || code.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Gmail OAuth 回调参数不完整");
        }
        var form = new LinkedMultiValueMap<String, String>();
        form.add("code", code);
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("redirect_uri", redirectUri);
        form.add("grant_type", "authorization_code");
        try {
            JsonNode response = client.post()
                    .uri("https://oauth2.googleapis.com/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);
            String refreshToken = response == null ? "" : response.path("refresh_token").asText("");
            if (refreshToken.isBlank()) {
                throw new ResponseStatusException(BAD_GATEWAY, "Google 未返回 refresh token，请重新授权");
            }
            saveRefreshToken(refreshToken);
        } catch (RestClientResponseException e) {
            throw new ResponseStatusException(BAD_GATEWAY, "Gmail OAuth 授权失败");
        } catch (RestClientException e) {
            throw new ResponseStatusException(BAD_GATEWAY, "Gmail OAuth 服务不可用", e);
        }
    }

    public GmailImportResult importLinkedInAlerts() {
        if (!enabled) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "LinkedIn Gmail 同步已关闭");
        }
        String token = accessToken();
        JsonNode list;
        try {
            list = client.get()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("https")
                            .host("gmail.googleapis.com")
                            .path("/gmail/v1/users/me/messages")
                            .queryParam("q", query)
                            .queryParam("maxResults", maxResults)
                            .build())
                    .header("Authorization", "Bearer " + token)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException e) {
            throw new ResponseStatusException(BAD_GATEWAY, "Gmail 邮件列表读取失败");
        } catch (RestClientException e) {
            throw new ResponseStatusException(BAD_GATEWAY, "Gmail 服务不可用", e);
        }

        int scanned = 0;
        int imported = 0;
        int failed = 0;
        JsonNode messages = list == null ? null : list.path("messages");
        if (messages == null || !messages.isArray()) {
            return new GmailImportResult("LINKEDIN_GMAIL", query, 0, 0, 0);
        }
        for (JsonNode message : messages) {
            String id = message.path("id").asText("");
            if (id.isBlank()) continue;
            scanned++;
            try {
                JsonNode full = client.get()
                        .uri(uriBuilder -> uriBuilder
                                .scheme("https")
                                .host("gmail.googleapis.com")
                                .path("/gmail/v1/users/me/messages/{id}")
                                .build(id))
                        .header("Authorization", "Bearer " + token)
                        .retrieve()
                        .body(JsonNode.class);
                String subject = header(full, "Subject");
                Instant receivedAt = parseMillis(full == null ? "" : full.path("internalDate").asText(""));
                for (JobPosting job : parseJobs(extractBody(full), subject, receivedAt)) {
                    repository.upsert(job.canonicalUrl(), job);
                    imported++;
                }
            } catch (RestClientException | IllegalArgumentException e) {
                failed++;
                LOG.warning("Skipping one Gmail message during LinkedIn import: " + id);
            }
        }
        return new GmailImportResult("LINKEDIN_GMAIL", query, scanned, imported, failed);
    }

    @Scheduled(cron = "${findwork.gmail.sync-cron:0 0 8 * * *}", zone = "${findwork.gmail.time-zone:Asia/Shanghai}")
    public void scheduledImport() {
        if (!enabled || !hasToken()) return;
        try {
            importLinkedInAlerts();
        } catch (ResponseStatusException e) {
            LOG.warning("Scheduled LinkedIn Gmail sync skipped: " + e.getReason());
        }
    }

    static List<JobPosting> parseJobs(String content, String subject, Instant receivedAt) {
        if (content == null || content.isBlank()) return List.of();
        List<JobPosting> jobs = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Matcher matcher = JOB_URL.matcher(content);
        while (matcher.find()) {
            String jobId = matcher.group(1);
            if (!seen.add(jobId)) continue;
            String url = "https://www.linkedin.com/jobs/view/" + jobId;
            String title = anchorTitle(content, matcher.start(), matcher.end());
            if (title.isBlank()) title = subject == null || subject.isBlank() ? "LinkedIn 岗位提醒" : subject;
            String description = stripHtml(content);
            if (description.length() > 2000) description = description.substring(0, 2000) + "…";
            jobs.add(new JobPosting(
                    UUID.randomUUID(), title, "公司待确认", null, null, "UNKNOWN", "全职", "待确认",
                    "LINKEDIN", url, "来自 Gmail 的 LinkedIn 岗位提醒：" + description,
                    List.of(), 0, true, receivedAt));
        }
        return jobs;
    }

    private String accessToken() {
        String refreshToken = refreshToken();
        if (!refreshToken.isBlank()) {
            if (!oauthConfigured()) {
                throw new ResponseStatusException(SERVICE_UNAVAILABLE, "配置 refresh token 时还需要 GMAIL_CLIENT_ID 和 GMAIL_CLIENT_SECRET");
            }
            var form = new LinkedMultiValueMap<String, String>();
            form.add("client_id", clientId);
            form.add("client_secret", clientSecret);
            form.add("refresh_token", refreshToken);
            form.add("grant_type", "refresh_token");
            try {
                JsonNode response = client.post()
                        .uri("https://oauth2.googleapis.com/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .body(form)
                        .retrieve()
                        .body(JsonNode.class);
                String token = response == null ? "" : response.path("access_token").asText("");
                if (!token.isBlank()) return token;
            } catch (RestClientException e) {
                throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Gmail refresh token 不可用，请重新连接 Gmail", e);
            }
        }
        if (!envAccessToken.isBlank()) return envAccessToken;
        throw new ResponseStatusException(SERVICE_UNAVAILABLE, "请先点击连接 Gmail，或配置 GMAIL_ACCESS_TOKEN");
    }

    private boolean hasToken() {
        return !envAccessToken.isBlank() || !refreshToken().isBlank();
    }

    private String refreshToken() {
        try {
            if (Files.exists(tokenFile)) {
                String stored = Files.readString(tokenFile).trim();
                if (!stored.isBlank()) return stored;
            }
        } catch (IOException e) {
            LOG.warning("Unable to read local Gmail token file");
        }
        return envRefreshToken;
    }

    private void saveRefreshToken(String token) {
        try {
            if (tokenFile.getParent() != null) Files.createDirectories(tokenFile.getParent());
            Files.writeString(tokenFile, token, StandardCharsets.UTF_8);
            try {
                Files.setPosixFilePermissions(tokenFile, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
            } catch (UnsupportedOperationException ignored) {
                // Windows filesystems do not expose POSIX permissions.
            }
        } catch (IOException e) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "无法保存 Gmail refresh token", e);
        }
    }

    private static String extractBody(JsonNode node) {
        if (node == null || node.isMissingNode()) return "";
        StringBuilder result = new StringBuilder();
        appendBody(node.path("payload"), result);
        return result.toString();
    }

    private static void appendBody(JsonNode node, StringBuilder result) {
        String data = node.path("body").path("data").asText("");
        if (!data.isBlank()) {
            try {
                result.append(new String(Base64.getUrlDecoder().decode(data), StandardCharsets.UTF_8)).append('\n');
            } catch (IllegalArgumentException ignored) {
                // Ignore malformed MIME parts and keep parsing sibling parts.
            }
        }
        for (JsonNode part : node.path("parts")) appendBody(part, result);
    }

    private static String header(JsonNode message, String name) {
        JsonNode headers = message == null ? null : message.path("payload").path("headers");
        if (headers == null || !headers.isArray()) return "";
        for (JsonNode header : headers) {
            if (name.equalsIgnoreCase(header.path("name").asText(""))) return header.path("value").asText("");
        }
        return "";
    }

    private static String anchorTitle(String content, int start, int end) {
        int open = content.lastIndexOf("<a", start);
        int close = content.indexOf("</a>", end);
        if (open < 0 || close < 0 || close - open > 2000) return "";
        int textStart = content.indexOf('>', open);
        return textStart < 0 ? "" : stripHtml(content.substring(textStart + 1, close));
    }

    private static String stripHtml(String value) {
        return HTML_TAG.matcher(value)
                .replaceAll(" ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&nbsp;", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static Instant parseMillis(String value) {
        try {
            return value == null || value.isBlank() ? null : Instant.ofEpochMilli(Long.parseLong(value));
        } catch (RuntimeException e) {
            return null;
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

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private record OAuthState(String value, Instant expiresAt) {
    }
}
