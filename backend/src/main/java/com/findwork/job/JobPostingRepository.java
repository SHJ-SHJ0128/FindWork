package com.findwork.job;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

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
                       experience_level, source, canonical_url, description, skills,
                       score, needs_review, posted_at
                from job_posting
                order by score desc, posted_at desc nulls last
                """, this::map);
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
                    mapper.readValue(rs.getString("skills"), new TypeReference<>() {}),
                    rs.getInt("score"),
                    rs.getBoolean("needs_review"),
                    rs.getTimestamp("posted_at") == null ? null : rs.getTimestamp("posted_at").toInstant()
            );
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored job skills are invalid", e);
        }
    }
}
