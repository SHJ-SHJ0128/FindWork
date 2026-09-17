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
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    private static final Pattern HTML_ANCHOR = Pattern.compile(
            "(?is)<a\\b[^>]*\\bhref\\s*=\\s*[\\\"']([^\\\"']+)[\\\"'][^>]*>(.*?)</a>");
    private static final String JOB_DELIMITER = "---------------------------------------------------------";
    private static final Pattern COUNTRY = Pattern.compile("中国|China|新加坡|Singapore", Pattern.CASE_INSENSITIVE);
    private static final Pattern CITY = Pattern.compile(
            "重庆|成都|广州|深圳|杭州|上海|北京|南京|武汉|西安|苏州|厦门|天津|Singapore|Shanghai|Beijing|Shenzhen|Guangzhou|Hangzhou|Chengdu|Chongqing",
            Pattern.CASE_INSENSITIVE);
    private static final Map<String, String> CITY_LABELS = Map.ofEntries(
            Map.entry("singapore", "Singapore"), Map.entry("shanghai", "上海"),
            Map.entry("beijing", "北京"), Map.entry("shenzhen", "深圳"),
            Map.entry("guangzhou", "广州"), Map.entry("hangzhou", "杭州"),
            Map.entry("chengdu", "成都"), Map.entry("chongqing", "重庆"));
    private static final List<String> SKILL_KEYWORDS = List.of(
            "Spring Boot", "JavaScript", "TypeScript", "Machine Learning", "Kubernetes",
            "Java", "Python", "SQL", "REST API", "API", "Vue", "React", "LLM", "AI",
            "Security", "网络安全", "Docker", "Cloud", "C++", "Go", "Linux");

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
        String text = normalizeEmailText(content);
        List<JobPosting> jobs = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Matcher matcher = JOB_URL.matcher(text);
        while (matcher.find()) {
            String jobId = matcher.group(1);
            if (!seen.add(jobId)) continue;
            String url = "https://www.linkedin.com/jobs/view/" + jobId;
            ParsedJob parsed = parseEntry(jobEntry(text, matcher.start()), subject);
            String description = parsed.description();
            if (description.length() > 2000) description = description.substring(0, 2000) + "…";
            jobs.add(new JobPosting(
                    UUID.randomUUID(), parsed.title(), parsed.company(), parsed.country(), parsed.city(),
                    parsed.remoteType(), parsed.employmentType(), parsed.experienceLevel(),
                    "LINKEDIN", url, description,
                    parsed.skills(), 0, true, receivedAt));
        }
        return jobs;
    }

    private static String jobEntry(String text, int urlStart) {
        int marker = lastMarker(text, urlStart);
        if (marker < 0) return cleanEntry(text.substring(Math.max(0, urlStart - 2000), urlStart));
        int boundary = Math.max(text.lastIndexOf("---------------------------------------------------------", marker),
                lastMarker(text, marker));
        String entry = text.substring(Math.max(0, boundary + 1), Math.max(0, marker)).trim();
        if (entry.contains(JOB_DELIMITER)) {
            entry = entry.substring(entry.lastIndexOf(JOB_DELIMITER) + JOB_DELIMITER.length()).trim();
        }
        return cleanEntry(entry);
    }

    private static int lastMarker(String text, int before) {
        int end = Math.max(0, Math.min(before, text.length()));
        int marker = -1;
        for (String candidate : List.of("查看职位", "查看工作", "View job", "Apply now")) {
            marker = Math.max(marker, text.lastIndexOf(candidate, end - 1));
        }
        return marker;
    }

    private static ParsedJob parseEntry(String entry, String subject) {
        String value = cleanEntry(entry);
        Matcher countryMatcher = COUNTRY.matcher(value);
        String country = null;
        int countryStart = -1;
        int countryEnd = -1;
        String countryValue = "";
        while (countryMatcher.find()) {
            countryStart = countryMatcher.start();
            countryEnd = countryMatcher.end();
            countryValue = countryMatcher.group();
        }
        if (countryStart >= 0) {
            country = normalizeCountry(countryValue);
        }

        String beforeCountry = countryStart < 0 ? value : value.substring(0, countryStart).trim();
        String afterCountry = countryStart < 0 ? "" : value.substring(countryEnd).trim();
        String city = extractCity(afterCountry.isBlank() ? beforeCountry : afterCountry);
        if (city == null) city = extractCity(beforeCountry);
        String header = removeTrailingCity(beforeCountry, city);
        HeaderParts parts = splitHeader(header, subject);
        String title = parts.title();
        String company = parts.company();
        String searchable = (title + " " + value).toLowerCase(Locale.ROOT);
        String remoteType = containsAny(searchable, "remote", "远程", "work from home", "居家") ? "REMOTE"
                : containsAny(searchable, "hybrid", "混合办公") ? "HYBRID"
                : city == null ? "UNKNOWN" : "ONSITE";
        String employmentType = containsAny(searchable, "intern", "internship", "实习") ? "实习" : "全职";
        String experienceLevel = containsAny(searchable, "intern", "internship", "实习") ? "实习"
                : containsAny(searchable, "graduate", "entry", "junior", "校招", "校园招聘", "应届") ? "应届/初级" : "待确认";
        List<String> skills = extractSkills(searchable);
        String summary = skills.isEmpty()
                ? "职位提醒已整理，完整职责请打开原始职位。"
                : "已识别技能线索：" + String.join("、", skills) + "；完整职责请打开原始职位。";
        return new ParsedJob(title, company, country, city, remoteType, employmentType, experienceLevel, skills, summary);
    }

    private static HeaderParts splitHeader(String header, String subject) {
        String value = header.replaceFirst("^\\s*HuaJian\\s*:\\s*", "").trim();
        int dash = value.lastIndexOf(" - ");
        if (dash > 0 && value.substring(dash + 3).contains("&")) {
            return new HeaderParts(value.substring(0, dash).trim(), value.substring(dash + 3).trim());
        }
        String[] words = value.split("\\s+");
        int companyStart = -1;
        for (int i = words.length - 1; i >= 1; i--) {
            if (isCompanyToken(words[i])) {
                companyStart = i;
                if (i > 1 && isMultiWordCompanySuffix(words[i]) && isCompanyWord(words[i - 1]) && !isTitleWord(words[i - 1])) companyStart--;
                break;
            }
        }
        if (companyStart < 0 && words.length == 2 && isTitleWord(words[0]) && !isTitleWord(words[1])) companyStart = 1;
        if (companyStart < 0 && words.length >= 3) companyStart = words.length - 1;
        if (companyStart < 1) {
            String fallbackTitle = value.isBlank()
                    ? (subject == null || subject.isBlank() ? "LinkedIn 岗位提醒" : subject.trim())
                    : value;
            return new HeaderParts(fallbackTitle, "公司待确认");
        }
        String title = String.join(" ", Arrays.copyOfRange(words, 0, companyStart)).trim();
        String company = String.join(" ", Arrays.copyOfRange(words, companyStart, words.length)).trim();
        if (title.isBlank()) title = subject == null || subject.isBlank() ? "LinkedIn 岗位提醒" : subject.trim();
        return new HeaderParts(title, company.isBlank() ? "公司待确认" : company);
    }

    private static boolean isCompanyToken(String token) {
        String value = token.toLowerCase(Locale.ROOT);
        return value.matches(".*(公司|科技|集团|股份|有限公司|银行|大学|研究院|学院|医院|证券|基金|保险|医疗器材|technolog(?:y|ies)|solutions|systems|labs?|vacuum|holdings?|inc|llc|ltd|corp|拼多多|nvidia|abbvie|abb|dyna\\.ai|google|amazon|microsoft|apple|meta|百度|京东|腾讯|阿里|华为|思科|高通|英伟达|法国巴黎银行|契约锁|biotech|meshyai|traveloka).*");
    }

    private static boolean isMultiWordCompanySuffix(String token) {
        return token.toLowerCase(Locale.ROOT).matches(".*(technolog(?:y|ies)|solutions|systems|labs?|vacuum|holdings?|inc|llc|ltd|corp).*");
    }

    private static boolean isCompanyWord(String token) {
        String value = token.replaceAll("[^\\p{L}\\p{N}]", "");
        return value.length() > 1 && Character.isUpperCase(value.charAt(0));
    }

    private static boolean isTitleWord(String token) {
        return token.toLowerCase(Locale.ROOT).matches(".*(engineer|developer|architect|manager|analyst|intern|backend|frontend|software|network|security|工程师|开发|经理|顾问|前端|后端|网络|安全|软件|技术).*");
    }

    private static String cleanEntry(String value) {
        String result = value == null ? "" : value.replaceAll("[-]{5,}", " ").trim();
        int bracket = result.lastIndexOf('】');
        if (bracket >= 0) result = result.substring(bracket + 1).trim();
        if (result.contains("订阅") || result.contains("搜索偏好") || result.contains("通知")) {
            int intro = Math.max(result.lastIndexOf('。'), Math.max(result.lastIndexOf(". "), result.lastIndexOf('！')));
            if (intro >= 0) result = result.substring(intro + 1).trim();
        }
        result = result.replaceFirst("\\s+使用简历和职业档案申请.*$", "")
                .replaceFirst("\\s+\\d+\\s*位校友.*$", "")
                .replaceFirst("\\s+该公司正在热招中$", "")
                .replaceAll("\\s+", " ")
                .trim();
        return result;
    }

    private static String removeTrailingCity(String value, String city) {
        if (value == null || value.isBlank() || city == null || city.isBlank()) return value;
        String result = value.trim();
        String escaped = Pattern.quote(city);
        result = result.replaceFirst("(?i)\\s+(?:重庆|成都|广州|深圳|杭州|上海|北京|南京|武汉|西安|苏州|厦门|天津|Singapore|Shanghai|Beijing|Shenzhen|Guangzhou|Hangzhou|Chengdu|Chongqing)(?:[-—–][^\\s,]+)?(?:\\s*,\\s*[\\p{L}.-]+)*\\s*,?\\s*$", "");
        return result.replaceFirst("(?i)(?:\\s*,?\\s*" + escaped + ")+\\s*$", "").trim();
    }

    private static String extractCity(String value) {
        if (value == null || value.isBlank()) return null;
        Matcher matcher = CITY.matcher(value);
        String found = null;
        while (matcher.find()) found = matcher.group();
        if (found == null) return null;
        String normalized = CITY_LABELS.get(found.toLowerCase(Locale.ROOT));
        return normalized == null ? found.replaceFirst("市$", "") : normalized;
    }

    private static String normalizeCountry(String value) {
        return value.equalsIgnoreCase("新加坡") || value.equalsIgnoreCase("Singapore") ? "Singapore" : "China";
    }

    private static boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) if (value.contains(candidate.toLowerCase(Locale.ROOT))) return true;
        return false;
    }

    private static List<String> extractSkills(String value) {
        List<String> skills = new ArrayList<>();
        for (String skill : SKILL_KEYWORDS) {
            if (value.contains(skill.toLowerCase(Locale.ROOT)) || value.contains(skill.toLowerCase(Locale.CHINA))) {
                skills.add(skill);
            }
        }
        addSkillIfPresent(skills, value, "前端", "Frontend");
        addSkillIfPresent(skills, value, "后端", "Backend");
        addSkillIfPresent(skills, value, "网络", "Networking");
        addSkillIfPresent(skills, value, "安全", "Security");
        addSkillIfPresent(skills, value, "人工智能", "AI Application");
        addSkillIfPresent(skills, value, "大模型", "LLM");
        return skills;
    }

    private static void addSkillIfPresent(List<String> skills, String value, String keyword, String skill) {
        if (value.contains(keyword.toLowerCase(Locale.ROOT)) && !skills.contains(skill)) skills.add(skill);
    }

    private static String normalizeEmailText(String value) {
        Matcher anchor = HTML_ANCHOR.matcher(value);
        StringBuffer withLinks = new StringBuffer();
        while (anchor.find()) {
            anchor.appendReplacement(withLinks, Matcher.quoteReplacement(anchor.group(2) + " " + anchor.group(1)));
        }
        anchor.appendTail(withLinks);
        return stripHtml(withLinks.toString()
                        .replaceAll("(?i)<br\\s*/?>", "\\n")
                        .replaceAll("(?i)</(?:p|div|li|tr|h[1-6])>", "\\n"))
                .replaceAll("\\r", "\\n")
                .replaceAll("[ \\t\\f]+", " ")
                .replaceAll("\\n{2,}", "\\n")
                .trim();
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

    private record HeaderParts(String title, String company) {
    }

    private record ParsedJob(
            String title,
            String company,
            String country,
            String city,
            String remoteType,
            String employmentType,
            String experienceLevel,
            List<String> skills,
            String description
    ) {
    }

    private record OAuthState(String value, Instant expiresAt) {
    }
}
