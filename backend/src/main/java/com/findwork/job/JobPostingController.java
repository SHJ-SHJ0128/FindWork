package com.findwork.job;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

@RestController
@RequestMapping("/api/jobs")
@CrossOrigin(origins = "http://127.0.0.1:5173")
public class JobPostingController {
    private final JobPostingRepository repository;

    public JobPostingController(JobPostingRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<JobPosting> list(@RequestParam(defaultValue = "0") int page,
                                 @RequestParam(defaultValue = "100") int size,
                                 @RequestParam(defaultValue = "score") String sort,
                                 @RequestParam(required = false) Integer minScore,
                                 @RequestParam(required = false) String country,
                                 @RequestParam(required = false) String city,
                                 @RequestParam(required = false) String source) {
        if (page < 0 || size < 1 || size > 200) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "分页参数无效");
        Comparator<JobPosting> comparator = switch (sort) {
            case "score" -> Comparator.comparingInt(JobPosting::score).reversed().thenComparing(JobPosting::postedAt, Comparator.nullsLast(Comparator.reverseOrder()));
            case "postedAt" -> Comparator.comparing(JobPosting::postedAt, Comparator.nullsLast(Comparator.reverseOrder()));
            default -> throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "sort 只支持 score 或 postedAt");
        };
        // ponytail: in-memory filter/page is enough for this local dataset; move predicates to SQL when volume grows.
        Stream<JobPosting> stream = repository.findAll().stream();
        if (minScore != null) stream = stream.filter(job -> job.score() >= minScore);
        if (country != null && !country.isBlank()) stream = stream.filter(job -> country.equals(job.country()));
        if (city != null && !city.isBlank()) stream = stream.filter(job -> normalizeCity(city).equals(normalizeCity(job.city())));
        if (source != null && !source.isBlank()) stream = stream.filter(job -> source.equals(job.source()));
        return stream.sorted(comparator)
                .skip((long) page * size)
                .limit(size)
                .toList();
    }

    private static String normalizeCity(String value) {
        return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT).replaceFirst("市$", "");
    }
}
