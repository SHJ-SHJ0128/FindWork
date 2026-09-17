create table candidate_resume (
    id uuid primary key,
    original_filename text not null,
    media_type text not null,
    size_bytes bigint not null,
    sha256 text not null,
    storage_path text not null,
    extracted_text text not null,
    status text not null,
    error_message text,
    is_current boolean not null default true,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create unique index candidate_resume_sha_idx on candidate_resume (sha256);
create unique index candidate_resume_current_idx on candidate_resume (is_current) where is_current;

create table candidate_resume_analysis (
    id uuid primary key,
    resume_id uuid not null references candidate_resume(id) on delete cascade,
    analysis_version text not null,
    model_name text not null,
    status text not null,
    analysis_json jsonb,
    error_message text,
    analyzed_at timestamptz not null,
    unique (resume_id, analysis_version, model_name)
);

create index candidate_resume_analysis_latest_idx on candidate_resume_analysis (resume_id, analyzed_at desc);
