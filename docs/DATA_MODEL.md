# Job Copilot — Data Model

## V1 entities

```mermaid
erDiagram
  CANDIDATE_PROFILE ||--o{ RESUME : owns
  JOB ||--o{ JOB_POSTING : appears_as
  JOB ||--o{ JOB_MATCH : receives
  CANDIDATE_PROFILE ||--o{ JOB_MATCH : evaluated_against
  COLLECTION_RUN ||--o{ COLLECTION_ATTEMPT : contains
  JOB_POSTING }o--|| COLLECTION_ATTEMPT : imported_by

  CANDIDATE_PROFILE { uuid id PK string status jsonb preferences timestamptz updated_at }
  RESUME { uuid id PK uuid profile_id FK string storage_key text extracted_text string sha256 timestamptz }
  JOB { uuid id PK string title string normalized_title string company string normalized_company string country string city string remote_type string employment_type date date_posted jsonb requirements jsonb skills jsonb }
  JOB_POSTING { uuid id PK uuid job_id FK string provider string source_job_id string canonical_url string description_hash jsonb raw_payload timestamptz first_seen timestamptz last_seen }
  JOB_MATCH { uuid id PK uuid job_id FK uuid profile_id FK int score string decision jsonb score_components jsonb evidence jsonb string algorithm_version timestamptz computed_at }
  COLLECTION_RUN { uuid id string trigger string status timestamptz started_at timestamptz finished_at int imported_count int failed_count }
  COLLECTION_ATTEMPT { uuid id uuid run_id FK string provider string status string error_code int imported_count timestamptz started_at timestamptz finished_at }
```

## Storage decisions

### Normal columns

Store title, normalized title, company, normalized company, country, city, remote type, employment type, experience bounds, salary bounds/currency, posted/collected/expiry timestamps, provider, source ID, canonical URL, and status fields as columns. These are used for filtering, indexing, and sorting.

### Child/provenance rows

`job` is the canonical role; `job_posting` is a source occurrence. This preserves LinkedIn/BOSS/ATS provenance, repost history, source URLs, first/last seen timestamps, and per-source parse warnings. Different locations remain different canonical jobs unless a human confirms a relationship.

### JSONB

Use JSONB for provider-specific payload, extracted requirements, education details, evidence snippets, score components, and parser warnings. Do not put arbitrary searchable business fields only in JSONB.

### Derived values

Description fingerprints, normalized strings, filter decision, score, score components, and algorithm version are derived and recomputable. Retain them for auditability but never treat them as immutable source truth.

### Resume files

Store the binary in a local application-data directory with restrictive permissions; store only a storage key, hash, MIME type, size, extracted text, and timestamps in PostgreSQL. Delete source/raw artifacts after 30 days where applicable; the active resume remains until the user deletes it.

## Candidate preferences

The active profile stores editable structured JSON or columns for education, university, degree, major, graduation date, skills, projects, work history, target role families, allowed cities, China preference, Singapore inclusion, remote preference, experience level, required keywords, excluded keywords, and work authorization. A single profile is enough for V1; a profile ID boundary permits later multiple profiles.

## Indexes and constraints

- Unique `(provider, source_job_id)` when the provider ID is present.
- Unique canonical URL after normalization when non-null.
- Index `job(country, city, remote_type)` and `job(normalized_title, normalized_company)`.
- Index `job_posting(last_seen, provider)` for retention and stale detection.
- Index `job_match(profile_id, score DESC)`.
- Foreign keys with restrictive deletes for profile/job provenance; explicit cleanup jobs for raw payload.
- Check constraints for score `0..100`, valid remote type, and valid run/attempt statuses.
- UTC timestamps everywhere.

## Deferred tables

Do not create `users`, `companies`, `provider_configs`, `job_match_reasons`, or `applications` until a concrete query or workflow needs them. Application tracking is outside V1.

## Deduplication semantics

1. Same provider ID is an exact duplicate/upsert.
2. Same canonical URL is an exact duplicate.
3. Same normalized company + title + location plus matching description hash is a strong probable duplicate.
4. Fuzzy similarity is review-only in V1.
5. A repost is a new posting occurrence linked to the same canonical job when evidence is strong; it does not overwrite provenance.
