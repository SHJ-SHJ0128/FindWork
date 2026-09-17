create table job_match_result (
    id uuid primary key,
    job_id uuid not null references job_posting(id) on delete cascade,
    profile_hash text not null,
    algorithm_version text not null,
    total_score integer not null check (total_score between 0 and 100),
    role_score integer not null check (role_score between 0 and 30),
    skill_score integer not null check (skill_score between 0 and 25),
    experience_score integer not null check (experience_score between 0 and 20),
    location_score integer not null check (location_score between 0 and 15),
    freshness_score integer not null check (freshness_score between 0 and 10),
    matched_skills jsonb not null default '[]'::jsonb,
    missing_required_skills jsonb not null default '[]'::jsonb,
    positive_reasons jsonb not null default '[]'::jsonb,
    concerns jsonb not null default '[]'::jsonb,
    hard_filter_passed boolean not null,
    hard_filter_reasons jsonb not null default '[]'::jsonb,
    calculated_at timestamptz not null default now(),
    unique (job_id, profile_hash, algorithm_version)
);

create index job_match_result_job_idx on job_match_result (job_id, calculated_at desc);
create index job_match_result_score_idx on job_match_result (total_score desc);
