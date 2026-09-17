# Job Copilot — Implementation Plan

Status: Phase 3 foundation started on 2026-09-17. The local app now has a candidate profile, a persisted job-posting list, filters, score display, remote/needs-review labels, demo records, a public Greenhouse import path, and a Gmail read-only LinkedIn alert importer. Matching computation, authentication, and application-tracking code remain unimplemented.

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

**Acceptance:** a user can create/edit a profile and select/delete a local resume; secrets and raw files are excluded from Git.

**Tests:** validation, storage permissions, redaction fixtures, PostgreSQL integration.

## Phase 2 — Job ingestion contract and persistence

**Objective:** accept provider-neutral records and preserve source provenance.

**Tasks:** `job`/`job_posting` schema; normalization; exact upsert; raw payload retention metadata; collection run/attempt state.

**Expected modules:** `job`, `provider`, `collection`, migrations.

**Dependencies:** Phase 0.

**Acceptance:** duplicate provider IDs are idempotent; source occurrences remain traceable; partial run states are queryable.

**Tests:** contract fixtures, uniqueness, UTC timestamps, partial failure simulation.

**Current slice:** `V2__job_posting.sql` creates the first provider-neutral table and indexes; `V2` plus `V3__more_demo_jobs.sql` seed fifteen clearly labelled demo records. `POST /api/providers/greenhouse/import` reads a public Greenhouse Job Board, while `POST /api/providers/gmail/linkedin/import` reads a bounded Gmail query with `gmail.readonly`, extracts LinkedIn job links, upserts by `source + source_job_id`, stores the existing `canonical_url`, and marks imported records `needs_review=true` until matching is implemented. A local OAuth callback stores the refresh token under ignored `storage/`; the Gmail import is scheduled daily while the app is open. BOSS, direct LinkedIn collection, and weekly provider schedules remain unimplemented.

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

## Phase 6 — Hard filtering and explainable matching

**Objective:** maximize recall while producing a reviewable deterministic rank.

**Tasks:** configurable role/level/location filters; China/Singapore/city/remote rules; reason codes; weighted score; missing-data neutrality; evidence model; algorithm versioning.

**Expected modules:** `matching`, filter configuration, match persistence.

**Dependencies:** Phase 1, 2, and 5.

**Acceptance:** explicit exclusions are rejected with codes; uncertain jobs survive; score components explain rank; recall-labelled fixtures pass the agreed threshold.

**Tests:** filter matrix, score component tests, property tests for score bounds, labelled offline evaluation.

## Phase 7 — DeepSeek extraction and explanations

**Objective:** add useful AI without surrendering correctness or privacy.

**Tasks:** DeepSeek gateway; redaction; schema-validated extraction; evidence-grounded explanation; content-hash cache; unavailable/timeout fallback.

**Expected modules:** `matching/ai`, prompt/schema resources, `.env.example` entries.

**Dependencies:** Phase 6 and the user's DeepSeek API key.

**Acceptance:** no PII is sent in fixtures; deterministic score survives API failure; every explanation item has evidence or is omitted.

**Tests:** redaction tests, JSON schema tests, mocked API failure, cache key tests.

## Phase 8 — Vue dashboard

**Objective:** let the user review quantity and quality efficiently.

**Tasks:** job list/detail; score/evidence/concern panels; source and city/remote filters; collection status; profile/resume settings; needs-review labels.

**Expected modules:** `frontend` views/components and API client.

**Dependencies:** Phase 1–7.

**Acceptance:** the user can inspect every retained job, open the original URL, and understand why it ranked; no application-tracking UI is included.

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
