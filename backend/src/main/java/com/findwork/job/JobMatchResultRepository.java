package com.findwork.job;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JobMatchResultRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public JobMatchResultRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public Optional<JobMatchResult> findLatest(UUID jobId) {
        List<JobMatchResult> rows = jdbc.query("""
                select * from job_match_result where job_id = ? order by calculated_at desc limit 1
                """, this::map, jobId);
        return rows.stream().findFirst();
    }

    public void save(UUID jobId, JobMatchResult result) {
        try {
            jdbc.update("""
                    insert into job_match_result (
                        id, job_id, profile_hash, algorithm_version, total_score, role_score, skill_score,
                        experience_score, location_score, freshness_score, matched_skills, missing_required_skills,
                        positive_reasons, concerns, hard_filter_passed, hard_filter_reasons, calculated_at
                    ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), cast(? as jsonb), cast(? as jsonb),
                              cast(? as jsonb), ?, cast(? as jsonb), ?)
                    on conflict (job_id, profile_hash, algorithm_version) do update set
                        total_score = excluded.total_score,
                        role_score = excluded.role_score,
                        skill_score = excluded.skill_score,
                        experience_score = excluded.experience_score,
                        location_score = excluded.location_score,
                        freshness_score = excluded.freshness_score,
                        matched_skills = excluded.matched_skills,
                        missing_required_skills = excluded.missing_required_skills,
                        positive_reasons = excluded.positive_reasons,
                        concerns = excluded.concerns,
                        hard_filter_passed = excluded.hard_filter_passed,
                        hard_filter_reasons = excluded.hard_filter_reasons,
                        calculated_at = excluded.calculated_at
                    """,
                    UUID.randomUUID(), jobId, result.profileHash(), result.algorithmVersion(), result.totalScore(),
                    result.roleScore(), result.skillScore(), result.experienceScore(), result.locationScore(), result.freshnessScore(),
                    json(result.matchedSkills()), json(result.missingRequiredSkills()), json(result.positiveReasons()),
                    json(result.concerns()), result.hardFilterPassed(), json(result.hardFilterReasons()),
                    result.calculatedAt() == null ? null : Timestamp.from(result.calculatedAt()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Match result cannot be serialized", e);
        }
    }

    private JobMatchResult map(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        try {
            Timestamp timestamp = rs.getTimestamp("calculated_at");
            return new JobMatchResult(rs.getInt("total_score"), rs.getInt("role_score"), rs.getInt("skill_score"),
                    rs.getInt("experience_score"), rs.getInt("location_score"), rs.getInt("freshness_score"),
                    readList(rs.getString("matched_skills")), readList(rs.getString("missing_required_skills")),
                    readList(rs.getString("positive_reasons")), readList(rs.getString("concerns")),
                    rs.getBoolean("hard_filter_passed"), readList(rs.getString("hard_filter_reasons")),
                    rs.getString("profile_hash"), rs.getString("algorithm_version"),
                    timestamp == null ? null : timestamp.toInstant());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored match result is invalid", e);
        }
    }

    private List<String> readList(String value) throws JsonProcessingException {
        return value == null ? List.of() : mapper.readValue(value, new TypeReference<>() {});
    }

    private String json(Object value) throws JsonProcessingException {
        return mapper.writeValueAsString(value);
    }
}
