# Job Copilot — Implementation Plan

Status: Provider and review foundation completed on 2026-09-18. The local app has candidate preferences, provider-neutral persistence, Greenhouse and Gmail/LinkedIn alert import, salary extraction, city/source/remote filters, and a Vue review dashboard. The approved Resume AI + Job Intelligence sprint is in progress: local resume upload/text extraction plus gated resume and JD analysis endpoints are implemented; job match scoring remains retired.

## Current Sprint — Manual Review Simplification (2026-09-18)

- Provider imports persist normalized `JobPosting` records for direct human review; page loading performs no AI calls.
- `GET /api/jobs` supports `page`, `size`, `sort=score|postedAt`, `minScore`, `country`, `city`, and `source`. City matching normalizes whitespace/case and a trailing `市` on both API and UI paths.
- The Vue dashboard shows source/date, title, company/location, salary, summary, skills, workplace/review labels, and the original job link. Match scores and recommendations are removed; JD analysis is an explicit per-card action.
- Job DeepSeek analysis is available only through an explicit per-card action; the jobs list still performs no AI calls and match scoring is not registered in the running flow. Resume endpoints are available, but external analysis is opt-in and remains disabled by default. V5/V6 tables and legacy match-scoring classes remain only for compatibility with existing local data.

The implementation deliberately does not add new providers, auto-apply, login automation, CAPTCHA handling, embeddings, or a queue.

## Approved next sprint — Resume AI + Job Intelligence (in progress)

The user confirmed this scope on 2026-09-18. Resume upload/extraction and on-demand job analysis are now implemented; Gmail triage remains pending:

- Resume AI supports PDF/DOCX up to 10 MB, keeps local version history with one active resume, and auto-analyzes uploads.
- Resume suggestions are field-level diffs; nothing is written to the candidate profile until the user confirms it.
- Full resume files remain local. Only redacted resume text and job descriptions may be sent to the configured SiliconFlow DeepSeek endpoint.
- Gmail AI processes LinkedIn job-alert mail only. A successfully imported message may be marked read; it must not be archived, deleted, or moved.
- New jobs are intended to be analyzed automatically; the current safe slice exposes analysis on demand from each job card so an external call is never hidden or triggered by page loading.
- AI returns Chinese summaries plus structured tags, requirement lists, source evidence, and confidence. Unsupported claims are marked for manual review.
- AI failure is fail-open for ingestion: retain the job and mark it pending/failed for retry.
- Store normalized analysis and a content hash, not indefinite full Prompt/Response logs.
- AI does not produce a numeric match score or change current filtering, sorting, or job counts. Automatic application, login/CAPTCHA handling, and cover-letter generation remain out of scope.

Implementation order: Resume AI → Job AI → Gmail Triage. Resume external analysis remains disabled until `DEEPSEEK_ENABLED=true` is explicitly set in the local environment.

## Phase 0 — Repository and architecture baseline

**Objective:** create the smallest runnable project skeleton and lock the contracts in these documents.

**Tasks:** initialize the monorepo; add Spring Boot, Vue, and PostgreSQL Compose skeletons; add Flyway; define `.env.example`; define provider import JSON schema; add formatting/linting/test commands.

**Expected files/modules:** `backend/`, `frontend/`, `collector/` placeholder, `infra/docker-compose.yml`, root build/readme files.

**Dependencies:** none.

**Acceptance:** services can start locally with no secrets; database migration smoke test passes; no business workflow is implemented.

**Tests:** build/lint smoke checks and migration connectivity.

## Phase 1 — Candidate profile and local resume boundary

**Objective:** persist one editable profile and a local active resume without external AI calls.

**Tasks:** profile fields/preferences; resume metadata and local storage policy; PII-redaction contract; input size/type validation.

**Expected modules:** `candidate`, resume storage adapter, profile REST endpoints.

**Dependencies:** Phase 0.

**Acceptance:** a user can create/edit a profile, upload a PDF/DOCX resume, keep one active local version, and apply confirmed field-level suggestions; secrets and raw files are excluded from Git.

**Tests:** validation, storage permissions, redaction fixtures, PostgreSQL integration.

**Current slice:** `V8__candidate_resume.sql` adds local resume metadata, extracted text, content-hash deduplication, one-current-version semantics, and versioned analysis records. `POST /api/resumes` validates PDF/DOCX signatures and a 10 MB limit, extracts text with PDFBox/Apache POI, stores the file under ignored `storage/resumes/`, and auto-analyzes only when `DEEPSEEK_ENABLED=true`. `POST /api/resumes/{id}/apply-to-profile` merges only user-selected skills, target roles, and experience fields.

## Phase 2 — Job ingestion contract and persistence

**Objective:** accept provider-neutral records and preserve source provenance.

**Tasks:** `job`/`job_posting` schema; normalization; exact upsert; raw payload retention metadata; collection run/attempt state.

**Expected modules:** `job`, `provider`, `collection`, migrations.

**Dependencies:** Phase 0.

**Acceptance:** duplicate provider IDs are idempotent; source occurrences remain traceable; partial run states are queryable.

**Tests:** contract fixtures, uniqueness, UTC timestamps, partial failure simulation.

**Current slice:** `V2__job_posting.sql` and `V3__more_demo_jobs.sql` are historical fixtures; `V7__remove_demo_jobs.sql` removes all `DEMO` records after migration so the running database and fresh databases contain only imported sources. `V4__job_summary_and_salary.sql` adds nullable summary and salary fields without changing existing rows. `POST /api/providers/greenhouse/import` reads a public Greenhouse Job Board and keeps the full JD in `description` while deriving a compact `summary`; `POST /api/providers/gmail/linkedin/import` reads a bounded Gmail query with `gmail.readonly`, splits each LinkedIn job link into the same normalized card record (title, company, country, city, work mode, experience, keyword skills and salary), upserts by `source + source_job_id`, stores the existing `canonical_url`, and marks imported records `needs_review=true` until matching is implemented. The shared frontend card formats dates and salaries once, hides full descriptions, supports client-side city/source/remote/search filters, and only displays China/Singapore records. A local OAuth callback stores the refresh token under ignored `storage/`; the Gmail import is scheduled daily while the app is open and is not exposed as a manual page action. BOSS, direct LinkedIn collection, and weekly provider schedules remain unimplemented.

## Phase 3 — Stable and user-controlled providers

**Objective:** prove collection with low-risk sources before enabling risky adapters.

**Tasks:** manual URL/pasted JD; Gmail read-only OAuth and LinkedIn alert parser; Greenhouse; Lever; reviewed JSON-LD company pages; source attribution and rate limits.

**Expected modules:** provider adapters and parser fixtures; no generic crawler.

**Dependencies:** Phase 2.

**Acceptance:** LinkedIn email sync can run daily; other stable providers can run weekly; malformed one-provider input does not abort siblings.

**Tests:** email fixtures, OAuth error handling, ATS fixtures, robots/timeout/429 handling.

## Phase 4 — Opt-in LinkedIn/BOSS/JobSpy/browser adapters

**Objective:** support the user's priority sources within the accepted safety boundary.

**Tasks:** define explicit provider enable flags; bounded Python CLI; JobSpy adapter; user-controlled Playwright/BOSS and LinkedIn adapter; no password storage, proxy bypass, CAPTCHA handling, or stealth behavior; NDJSON handoff.

**Expected modules:** `collector/`, Spring process runner, provider attempt telemetry.

**Dependencies:** Phase 2 and Phase 3.

**Acceptance:** each adapter can be run independently; access challenge stops only that attempt; output is attributed and normalized; disabled adapters never run.

**Tests:** CLI contract, timeout/exit-code handling, fixture normalization, provider isolation.

## Phase 5 — Deduplication and retention

**Objective:** avoid double counting while preserving uncertain cases and 30-day raw-data policy.

**Tasks:** URL/source-ID exact dedup; normalized fingerprint; description hash; probable-duplicate review flag; repost occurrence rules; cleanup job.

**Expected modules:** `job` dedup service, retention service, migrations/indexes.

**Dependencies:** Phase 2–4.

**Acceptance:** exact duplicates do not multiply; different locations remain distinct; raw job/email artifacts older than 30 days are deleted.

**Tests:** duplicate matrix, repost cases, cleanup boundary tests.

## Phase 6 — Hard filtering and explainable review

**Objective:** maximize recall with user-controlled filters while keeping review decisions human-readable; no numeric match score is part of the approved product.

**Tasks:** configurable role/level/location filters; China/Singapore/city/remote rules; reason codes; missing-data neutrality; evidence model. Any historical score tables/classes remain compatibility-only.

**Expected modules:** `matching`, filter configuration, match persistence.

**Dependencies:** Phase 1, 2, and 5.

**Acceptance:** explicit exclusions are rejected with codes; uncertain jobs survive; filter behavior is inspectable; recall-labelled fixtures pass the agreed threshold.

**Tests:** filter matrix, compatibility checks for legacy score data, labelled offline evaluation.

## Phase 7 — DeepSeek extraction and explanations (partial)

**Objective:** add evidence-grounded AI extraction without surrendering correctness or privacy; this phase does not restore numeric matching.

**Tasks:** DeepSeek gateway; redaction; schema-validated extraction; evidence-grounded explanation; content-hash cache; unavailable/timeout fallback. Automatic new-job scheduling remains deferred until provider cost and retry behavior are confirmed.

**Expected modules:** `matching/ai`, prompt/schema resources, `.env.example` entries.

**Dependencies:** Phase 2–3, the approved privacy boundary, and the user's DeepSeek API key.

**Current slice:** `POST /api/jobs/{id}/analysis` performs one explicit, cached JD analysis and `GET /api/jobs/{id}/analysis` returns the current structured result. The Vue card shows category, required/preferred skills, and up to three responsibilities without a numeric score. API failure is stored as `FAILED` and does not alter the job record or list ordering.

**Acceptance:** no PII is sent in fixtures; job ingestion survives API failure; every explanation item has evidence or is marked for review; filters, sorting, and job counts are unchanged.

**Tests:** redaction tests, JSON schema tests, mocked API failure, cache key tests.

## Phase 8 — Vue dashboard

**Objective:** let the user review quantity and quality efficiently without match-score UI.

**Tasks:** job list/detail; source and city/remote filters; salary/summary display; profile/resume settings; needs-review labels.

**Expected modules:** `frontend` views/components and API client.

**Dependencies:** Phase 1–3 and the approved AI sprint design; AI UI remains optional until implementation is explicitly started.

**Acceptance:** the user can inspect every retained job and open the original URL; no application-tracking or match-score UI is included.

**Tests:** component tests, accessibility checks, API contract tests, one end-to-end review flow.

## Phase 9 — Evaluation and operational hardening

**Objective:** make the local tool dependable before broadening sources.

**Tasks:** labelled recall/precision review; provider health view; structured logs; retry tuning; backup/export; dependency/license review; docs refresh.

**Expected modules:** test fixtures, run-history view, scripts.

**Dependencies:** Phase 8.

**Acceptance:** a complete daily/weekly run produces auditable partial-success output; raw retention is verified; known risks and unverified sources are visible.

**Tests:** full local integration, failure-injection matrix, retention/security review.

## Deferred phases

Application tracking, approved partner APIs, wider company/career sources, Workday tenant feeds, embeddings/pgvector, multi-user auth, cloud deployment, Redis/queue, and automatic application remain future work. None should be pulled into V1 without a new decision record.
