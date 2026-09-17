package com.findwork.job;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JobAiAnalysisRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public JobAiAnalysisRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public Optional<JobAiAnalysisRecord> findLatest(UUID jobId) {
        List<JobAiAnalysisRecord> rows = jdbc.query("""
                select * from job_ai_analysis where job_id = ? order by analyzed_at desc limit 1
                """, this::map, jobId);
        return rows.stream().findFirst();
    }

    public void saveSuccess(UUID jobId, String hash, String version, String model, JobSemanticAnalysis analysis) {
        save(jobId, hash, version, model, "SUCCESS", analysis, null);
    }

    public void saveFailure(UUID jobId, String hash, String version, String model, String error) {
        save(jobId, hash, version, model, "FAILED", null, error);
    }

    public void saveAnalyzing(UUID jobId, String hash, String version, String model) {
        save(jobId, hash, version, model, "ANALYZING", null, null);
    }

    private void save(UUID jobId, String hash, String version, String model, String status,
                      JobSemanticAnalysis analysis, String error) {
        try {
            jdbc.update("""
                    insert into job_ai_analysis (
                        id, job_id, description_hash, analysis_version, model_name, status,
                        job_category, required_skills, preferred_skills, minimum_experience_years,
                        maximum_experience_years, education_level, seniority_level, graduate_friendly,
                        employment_type, workplace_type, responsibilities, work_authorization_required,
                        visa_sponsorship, salary, evidence, error_message, analyzed_at
                    ) values (?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), cast(? as jsonb), ?, ?, ?, ?, ?, ?, ?,
                              cast(? as jsonb), ?, ?, cast(? as jsonb), cast(? as jsonb), ?, now())
                    on conflict (job_id, description_hash, analysis_version, model_name) do update set
                        status = excluded.status,
                        job_category = excluded.job_category,
                        required_skills = excluded.required_skills,
                        preferred_skills = excluded.preferred_skills,
                        minimum_experience_years = excluded.minimum_experience_years,
                        maximum_experience_years = excluded.maximum_experience_years,
                        education_level = excluded.education_level,
                        seniority_level = excluded.seniority_level,
                        graduate_friendly = excluded.graduate_friendly,
                        employment_type = excluded.employment_type,
                        workplace_type = excluded.workplace_type,
                        responsibilities = excluded.responsibilities,
                        work_authorization_required = excluded.work_authorization_required,
                        visa_sponsorship = excluded.visa_sponsorship,
                        salary = excluded.salary,
                        evidence = excluded.evidence,
                        error_message = excluded.error_message,
                        analyzed_at = now()
                    """,
                    UUID.randomUUID(), jobId, hash, version, model, status,
                    analysis == null ? null : analysis.jobCategory(), json(analysis == null ? List.of() : analysis.requiredSkills()),
                    json(analysis == null ? List.of() : analysis.preferredSkills()),
                    analysis == null ? null : analysis.minimumExperienceYears(), analysis == null ? null : analysis.maximumExperienceYears(),
                    analysis == null ? null : analysis.educationLevel(), analysis == null ? null : analysis.seniorityLevel(),
                    analysis == null ? null : analysis.graduateFriendly(), analysis == null ? null : analysis.employmentType(),
                    analysis == null ? null : analysis.workplaceType(), json(analysis == null ? List.of() : analysis.responsibilities()),
                    analysis == null ? null : analysis.workAuthorizationRequired(), analysis == null ? null : analysis.visaSponsorship(),
                    analysis == null || analysis.salary() == null ? null : analysis.salary().toString(),
                    json(analysis == null ? Map.of() : analysis.evidence()), error);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("AI analysis cannot be serialized", e);
        }
    }

    private JobAiAnalysisRecord map(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        try {
            JobSemanticAnalysis analysis = "SUCCESS".equals(rs.getString("status"))
                    ? new JobSemanticAnalysis(
                    rs.getString("job_category"), readList(rs.getString("required_skills")),
                    readList(rs.getString("preferred_skills")), (Integer) rs.getObject("minimum_experience_years"),
                    (Integer) rs.getObject("maximum_experience_years"), rs.getString("education_level"),
                    rs.getString("seniority_level"), (Boolean) rs.getObject("graduate_friendly"),
                    rs.getString("employment_type"), rs.getString("workplace_type"), readList(rs.getString("responsibilities")),
                    (Boolean) rs.getObject("work_authorization_required"), (Boolean) rs.getObject("visa_sponsorship"),
                    readNode(rs.getString("salary")), readMap(rs.getString("evidence"))) : null;
            Timestamp timestamp = rs.getTimestamp("analyzed_at");
            return new JobAiAnalysisRecord(rs.getObject("id", UUID.class), rs.getObject("job_id", UUID.class),
                    rs.getString("description_hash"), rs.getString("analysis_version"), rs.getString("model_name"),
                    rs.getString("status"), analysis, rs.getString("error_message"), timestamp == null ? null : timestamp.toInstant());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored AI analysis is invalid", e);
        }
    }

    private List<String> readList(String value) throws JsonProcessingException {
        return value == null ? List.of() : mapper.readValue(value, new TypeReference<>() {});
    }

    private Map<String, Object> readMap(String value) throws JsonProcessingException {
        return value == null ? Map.of() : mapper.readValue(value, new TypeReference<>() {});
    }

    private JsonNode readNode(String value) throws JsonProcessingException {
        return value == null ? null : mapper.readTree(value);
    }

    private String json(Object value) throws JsonProcessingException {
        return mapper.writeValueAsString(value);
    }
}
