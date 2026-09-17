package com.findwork.job;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@Repository
public class JobPostingRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public JobPostingRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public List<JobPosting> findAll() {
        return jdbc.query("""
                select id, title, company, country, city, remote_type, employment_type,
                       experience_level, source, canonical_url, description, summary, skills,
                       salary_min, salary_max, salary_currency, salary_period, salary_text, salary_source,
                       score, needs_review, posted_at
                from job_posting
                order by score desc, posted_at desc nulls last
                """, this::map);
    }

    public void upsert(String sourceJobId, JobPosting job) {
        try {
            jdbc.update("""
                    insert into job_posting (
                        id, title, company, country, city, remote_type, employment_type,
                        experience_level, source, source_job_id, canonical_url, description,
                        summary, skills, salary_min, salary_max, salary_currency, salary_period, salary_text,
                        salary_source, score, needs_review, posted_at
                    ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    on conflict (source, source_job_id) do update set
                        title = excluded.title,
                        company = excluded.company,
                        country = excluded.country,
                        city = excluded.city,
                        remote_type = excluded.remote_type,
                        employment_type = excluded.employment_type,
                        experience_level = excluded.experience_level,
                        canonical_url = excluded.canonical_url,
                        description = excluded.description,
                        summary = excluded.summary,
                        skills = excluded.skills,
                        salary_min = excluded.salary_min,
                        salary_max = excluded.salary_max,
                        salary_currency = excluded.salary_currency,
                        salary_period = excluded.salary_period,
                        salary_text = excluded.salary_text,
                        salary_source = excluded.salary_source,
                        score = excluded.score,
                        needs_review = excluded.needs_review,
                        posted_at = excluded.posted_at
                    """,
                    UUID.nameUUIDFromBytes((job.source() + ":" + sourceJobId).getBytes(StandardCharsets.UTF_8)),
                    job.title(), job.company(), job.country(), job.city(), job.remoteType(),
                    job.employmentType(), job.experienceLevel(), job.source(), sourceJobId,
                    job.canonicalUrl(), job.description(), job.summary(), mapper.writeValueAsString(job.skills()),
                    job.salaryMin(), job.salaryMax(), job.salaryCurrency(), job.salaryPeriod(), job.salaryText(),
                    job.salarySource(),
                    job.score(), job.needsReview(), job.postedAt() == null ? null : Timestamp.from(job.postedAt()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Job skills cannot be serialized", e);
        }
    }

    private JobPosting map(ResultSet rs, int rowNum) throws SQLException {
        try {
            return new JobPosting(
                    rs.getObject("id", java.util.UUID.class),
                    rs.getString("title"),
                    rs.getString("company"),
                    rs.getString("country"),
                    rs.getString("city"),
                    rs.getString("remote_type"),
                    rs.getString("employment_type"),
                    rs.getString("experience_level"),
                    rs.getString("source"),
                    rs.getString("canonical_url"),
                    rs.getString("description"),
                    rs.getString("summary"),
                    mapper.readValue(rs.getString("skills"), new TypeReference<>() {}),
                    rs.getBigDecimal("salary_min"),
                    rs.getBigDecimal("salary_max"),
                    rs.getString("salary_currency"),
                    rs.getString("salary_period"),
                    rs.getString("salary_text"),
                    rs.getString("salary_source"),
                    rs.getInt("score"),
                    rs.getBoolean("needs_review"),
                    rs.getTimestamp("posted_at") == null ? null : rs.getTimestamp("posted_at").toInstant()
            );
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored job skills are invalid", e);
        }
    }
}
