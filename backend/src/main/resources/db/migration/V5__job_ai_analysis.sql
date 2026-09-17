create table job_ai_analysis (
    id uuid primary key,
    job_id uuid not null references job_posting(id) on delete cascade,
    description_hash text not null,
    analysis_version text not null,
    model_name text not null,
    status text not null check (status in ('PENDING', 'ANALYZING', 'SUCCESS', 'FAILED')),
    job_category text,
    required_skills jsonb not null default '[]'::jsonb,
    preferred_skills jsonb not null default '[]'::jsonb,
    minimum_experience_years integer,
    maximum_experience_years integer,
    education_level text,
    seniority_level text,
    graduate_friendly boolean,
    employment_type text,
    workplace_type text,
    responsibilities jsonb not null default '[]'::jsonb,
    work_authorization_required boolean,
    visa_sponsorship boolean,
    salary jsonb,
    evidence jsonb not null default '{}'::jsonb,
    error_message text,
    analyzed_at timestamptz not null default now(),
    unique (job_id, description_hash, analysis_version, model_name)
);

create index job_ai_analysis_job_idx on job_ai_analysis (job_id, analyzed_at desc);
create index job_ai_analysis_status_idx on job_ai_analysis (status, analyzed_at);
