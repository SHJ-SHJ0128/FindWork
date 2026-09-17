package com.findwork.candidate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class CandidateProfileRepository {
    private static final UUID PROFILE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public CandidateProfileRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public Optional<CandidateProfile> find() {
        return jdbc.query("select profile_json from candidate_profile where id = ?", rs ->
                rs.next() ? Optional.of(read(rs.getString("profile_json"))) : Optional.empty(), PROFILE_ID);
    }

    public void save(CandidateProfile profile) {
        jdbc.update("""
                insert into candidate_profile (id, profile_json, created_at, updated_at)
                values (?, cast(? as jsonb), now(), now())
                on conflict (id) do update set profile_json = excluded.profile_json, updated_at = now()
                """, PROFILE_ID, write(profile));
    }

    private CandidateProfile read(String json) {
        try {
            return mapper.readValue(json, CandidateProfile.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored candidate profile is invalid", e);
        }
    }

    private String write(CandidateProfile profile) {
        try {
            return mapper.writeValueAsString(profile);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Candidate profile cannot be serialized", e);
        }
    }
}
