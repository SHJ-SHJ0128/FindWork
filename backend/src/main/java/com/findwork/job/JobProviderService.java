package com.findwork.job;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Service
public class JobProviderService {
    private static final Pattern SLUG = Pattern.compile("[A-Za-z0-9_-]{1,120}");
    private static final List<String> TARGET_CITIES = List.of("重庆", "成都", "广州", "深圳", "杭州");
    private final RestClient client;
    private final JobPostingRepository repository;

    public JobProviderService(RestClient.Builder builder, JobPostingRepository repository) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(20));
        this.client = builder.requestFactory(factory).build();
        this.repository = repository;
    }

    public ProviderImportResult importGreenhouse(String boardInput) {
        String board = parseBoardSlug(boardInput);
        JsonNode root;
        try {
            root = client.get()
                    .uri("https://boards-api.greenhouse.io/v1/boards/{board}/jobs?content=true", board)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException e) {
            throw new ResponseStatusException(BAD_GATEWAY, "Greenhouse 岗位板读取失败", e);
        }
        if (root == null || !root.path("jobs").isArray()) {
            throw new ResponseStatusException(BAD_GATEWAY, "Greenhouse 返回格式不可识别");
        }

        int imported = 0;
        for (JsonNode item : root.path("jobs")) {
            String sourceJobId = item.path("id").asText("");
            if (sourceJobId.isBlank()) continue;
            repository.upsert(board + ":" + sourceJobId, toJob(item));
            imported++;
        }
        return new ProviderImportResult("GREENHOUSE", board, imported);
    }

    private JobPosting toJob(JsonNode item) {
        String title = item.path("title").asText("Untitled role");
        String location = item.path("location").path("name").asText("").trim();
        String content = stripHtml(item.path("content").asText(""));
        String searchable = (title + " " + location + " " + content).toLowerCase(Locale.ROOT);
        String city = city(location);
        String country = TARGET_CITIES.contains(city) ? "China" : "Singapore".equals(city) ? "Singapore" : null;
        String remoteType = searchable.contains("remote") || searchable.contains("work from home")
                ? "REMOTE" : searchable.contains("hybrid") ? "HYBRID" : location.isBlank() ? "UNKNOWN" : "ONSITE";
        String titleSearch = title.toLowerCase(Locale.ROOT);
        String experience = titleSearch.contains("intern") || titleSearch.contains("co-op") ? "实习"
                : titleSearch.contains("graduate") || titleSearch.contains("entry") || titleSearch.contains("junior") ? "应届/初级" : "待确认";
        String employment = titleSearch.contains("intern") || titleSearch.contains("co-op") ? "实习"
                : titleSearch.contains("contract") ? "合同" : "全职";
        Instant postedAt = parseInstant(item.path("updated_at").asText(null));
        return new JobPosting(
                UUID.randomUUID(), title, item.path("company_name").asText("Greenhouse company"),
                country, city, remoteType, employment, experience, "GREENHOUSE",
                item.path("absolute_url").asText(null), content.isBlank() ? "暂无职位描述" : content,
                List.of(), 0, true, postedAt);
    }

    private String parseBoardSlug(String input) {
        String value = input == null ? "" : input.trim();
        if (SLUG.matcher(value).matches()) return value;
        try {
            URI uri = URI.create(value);
            if (!"https".equalsIgnoreCase(uri.getScheme())) throw new IllegalArgumentException();
            String host = uri.getHost();
            if (host == null || !(host.equals("boards.greenhouse.io") || host.equals("job-boards.greenhouse.io"))) {
                throw new IllegalArgumentException();
            }
            String[] parts = uri.getPath().split("/");
            if (parts.length < 2 || !SLUG.matcher(parts[1]).matches()) throw new IllegalArgumentException();
            return parts[1];
        } catch (RuntimeException e) {
            throw new ResponseStatusException(BAD_REQUEST, "请输入 Greenhouse 岗位板 slug 或 https://boards.greenhouse.io/... URL");
        }
    }

    private String city(String location) {
        for (String target : TARGET_CITIES) if (location.contains(target)) return target;
        if (location.contains("Singapore")) return "Singapore";
        return location.isBlank() ? null : location;
    }

    private String stripHtml(String value) {
        return value.replace("&amp;", "&")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&nbsp;", " ")
                .replace("&mdash;", "—")
                .replace("&ndash;", "–")
                .replaceAll("(?s)<[^>]*>", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Instant.parse(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
