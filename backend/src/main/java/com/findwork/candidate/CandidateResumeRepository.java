package com.findwork.candidate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class CandidateResumeRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public CandidateResumeRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public Optional<CandidateResume> findCurrent() {
        return query("where r.is_current = true order by r.created_at desc limit 1").stream().findFirst();
    }

    public Optional<CandidateResume> findById(UUID id) {
        return query("where r.id = ?", id).stream().findFirst();
    }

    public Optional<UUID> findIdByHash(String sha256) {
        List<UUID> ids = jdbc.query("select id from candidate_resume where sha256 = ?", (rs, row) -> rs.getObject("id", UUID.class), sha256);
        return ids.stream().findFirst();
    }

    public void save(CandidateResumeDraft draft) {
        jdbc.update("update candidate_resume set is_current = false, updated_at = now() where is_current = true");
        jdbc.update("""
                insert into candidate_resume (id, original_filename, media_type, size_bytes, sha256,
                    storage_path, extracted_text, status, error_message, is_current, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, true, now(), now())
                """, draft.id(), draft.originalFilename(), draft.mediaType(), draft.sizeBytes(), draft.sha256(),
                draft.storagePath(), draft.extractedText(), draft.status(), draft.errorMessage());
    }

    public void updateStatus(UUID id, String status, String errorMessage) {
        jdbc.update("update candidate_resume set status = ?, error_message = ?, updated_at = now() where id = ?", status, errorMessage, id);
    }

    public void markCurrent(UUID id) {
        jdbc.update("update candidate_resume set is_current = false, updated_at = now() where is_current = true");
        jdbc.update("update candidate_resume set is_current = true, updated_at = now() where id = ?", id);
    }

    public void saveAnalysis(UUID resumeId, String version, String model, String status, JsonNode analysis, String error) {
        jdbc.update("""
                insert into candidate_resume_analysis
                    (id, resume_id, analysis_version, model_name, status, analysis_json, error_message, analyzed_at)
                values (?, ?, ?, ?, ?, cast(? as jsonb), ?, now())
                on conflict (resume_id, analysis_version, model_name) do update set
                    status = excluded.status,
                    analysis_json = excluded.analysis_json,
                    error_message = excluded.error_message,
                    analyzed_at = now()
                """, UUID.randomUUID(), resumeId, version, model, status,
                analysis == null ? null : analysis.toString(), error);
    }

    public void delete(UUID id) {
        jdbc.update("delete from candidate_resume where id = ?", id);
        jdbc.update("""
                update candidate_resume set is_current = true, updated_at = now()
                where id = (select id from candidate_resume order by created_at desc limit 1)
                  and not exists (select 1 from candidate_resume where is_current = true)
                """);
    }

    private List<CandidateResume> query(String predicate, Object... args) {
        String sql = """
                select r.id, r.original_filename, r.media_type, r.size_bytes, r.sha256, r.storage_path, r.extracted_text, r.status,
                       r.error_message, r.is_current, r.created_at, r.updated_at,
                       a.status as analysis_status, a.model_name as analysis_model_name,
                       a.analysis_json, a.error_message as analysis_error_message, a.analyzed_at
                from candidate_resume r
                left join lateral (
                    select status, model_name, analysis_json, error_message, analyzed_at
                    from candidate_resume_analysis
                    where resume_id = r.id
                    order by analyzed_at desc limit 1
                ) a on true
                """ + predicate;
        return jdbc.query(sql, this::map, args);
    }

    private CandidateResume map(ResultSet rs, int rowNum) throws SQLException {
        try {
            Timestamp created = rs.getTimestamp("created_at");
            Timestamp updated = rs.getTimestamp("updated_at");
            String json = rs.getString("analysis_json");
            JsonNode analysis = json == null ? null : mapper.readTree(json);
            String analysisStatus = rs.getString("analysis_status");
            CandidateResume.ResumeAnalysisRecord record = analysisStatus == null ? null
                    : new CandidateResume.ResumeAnalysisRecord(analysisStatus, rs.getString("analysis_model_name"), analysis,
                    rs.getString("analysis_error_message"), rs.getTimestamp("analyzed_at").toInstant());
            return new CandidateResume(rs.getObject("id", UUID.class), rs.getString("original_filename"),
                    rs.getString("media_type"), rs.getLong("size_bytes"), rs.getString("sha256"), rs.getString("storage_path"), rs.getString("extracted_text"), rs.getString("status"),
                    rs.getString("error_message"), rs.getBoolean("is_current"), created.toInstant(), updated.toInstant(), record);
        } catch (Exception e) {
            throw new IllegalStateException("Stored resume is invalid", e);
        }
    }

    public record CandidateResumeDraft(UUID id, String originalFilename, String mediaType, long sizeBytes,
                                       String sha256, String storagePath, String extractedText,
                                       String status, String errorMessage) {
    }
}
