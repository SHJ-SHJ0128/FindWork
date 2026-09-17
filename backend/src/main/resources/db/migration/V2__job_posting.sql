create table job_posting (
    id uuid primary key,
    title text not null,
    company text not null,
    country text,
    city text,
    remote_type text not null check (remote_type in ('ONSITE', 'HYBRID', 'REMOTE', 'UNKNOWN')),
    employment_type text not null,
    experience_level text not null,
    source text not null,
    source_job_id text,
    canonical_url text,
    description text not null,
    skills jsonb not null default '[]'::jsonb,
    score integer not null check (score between 0 and 100),
    needs_review boolean not null default false,
    posted_at timestamptz,
    first_seen timestamptz not null default now(),
    unique (source, source_job_id)
);

create index job_posting_location_idx on job_posting (country, city, remote_type);
create index job_posting_score_idx on job_posting (score desc);

insert into job_posting (
    id, title, company, country, city, remote_type, employment_type,
    experience_level, source, source_job_id, canonical_url, description,
    skills, score, needs_review, posted_at
) values
    ('10000000-0000-0000-0000-000000000001', 'Backend Engineer（Java）', 'FindWork Demo', 'China', '重庆', 'ONSITE', '全职', '应届/初级', 'DEMO', 'demo-chongqing-backend', 'https://example.com/findwork/demo/chongqing-backend', '负责 Java/Spring Boot 服务开发与 SQL 性能优化。', '["Java", "Spring Boot", "SQL"]', 92, false, now() - interval '1 day'),
    ('10000000-0000-0000-0000-000000000002', 'AI Application Engineer', 'FindWork Demo', 'China', '成都', 'HYBRID', '全职', '应届/初级', 'DEMO', 'demo-chengdu-ai', 'https://example.com/findwork/demo/chengdu-ai', '参与 LLM 应用、检索流程和后端 API 的交付。', '["Java", "Python", "LLM", "API"]', 88, false, now() - interval '2 days'),
    ('10000000-0000-0000-0000-000000000003', 'Solutions Engineer Intern', 'FindWork Demo', 'Singapore', 'Singapore', 'HYBRID', '实习', '实习', 'DEMO', 'demo-singapore-solutions', 'https://example.com/findwork/demo/singapore-solutions', '面向客户演示技术方案，连接产品、工程与客户反馈。', '["Solutions Engineering", "English", "API"]', 84, false, now() - interval '3 days'),
    ('10000000-0000-0000-0000-000000000004', 'AI Full-stack Developer', 'FindWork Demo', 'China', '广州', 'REMOTE', '全职', '应届/初级', 'DEMO', 'demo-remote-fullstack', 'https://example.com/findwork/demo/remote-fullstack', '构建 AI 功能和前端交互；远程职位，地点需进一步确认。', '["TypeScript", "Vue", "AI Application"]', 79, true, now() - interval '4 days'),
    ('10000000-0000-0000-0000-000000000005', 'Software Engineer', 'FindWork Demo', null, null, 'UNKNOWN', '全职', '应届/初级', 'DEMO', 'demo-unknown-location', 'https://example.com/findwork/demo/unknown-location', '职位内容符合目标方向，但原始来源没有明确工作地点。', '["Java", "Backend"]', 71, true, now() - interval '5 days');
