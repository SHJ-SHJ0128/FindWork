create table candidate_profile (
    id uuid primary key,
    profile_json jsonb not null,
    created_at timestamptz not null,
    updated_at timestamptz not null
);
