# Job Copilot — Project Requirements

## Status

Architecture/preparation only. The Find Work directory was empty when this document was created. No application implementation is included in this phase.

## Product goal

Job Copilot is a local, personal job-discovery and matching tool. It collects software-engineering roles, normalizes and deduplicates them, removes only clearly unsuitable roles, ranks the remainder against an editable candidate profile, and presents evidence for human review.

China is the primary target region. Singapore remains supported. The allowed city set is Chongqing, Chengdu, Guangzhou, Shenzhen, and Hangzhou. Chongqing is preferred over Chengdu; the remaining allowed cities are equal by default. Remote roles receive equal ranking but are separately labelled.

## Users and operating assumptions

- V1 has one personal operator and one active candidate profile.
- The application is local and only syncs while it is open.
- It is not a SaaS product, but ownership boundaries should not prevent a later multi-user design.
- The operator reviews quality after the system maximizes recall.
- Original resume files remain local. Only redacted text may be sent to DeepSeek.

## V1 functional requirements

1. Maintain an editable candidate profile and one active resume.
2. Accept job records from LinkedIn alert email, BOSS-related user-approved collection, JobSpy/Playwright experimental adapters, manual URL or pasted description, Greenhouse, Lever, and reviewed company career pages.
3. Run LinkedIn email collection daily and other configured providers weekly while the application is open.
4. Convert every provider result into a provider-neutral import record with provenance.
5. Deduplicate exact records and mark uncertain cross-source matches without silently merging them.
6. Apply hard filters before AI matching, preserving a machine-readable reason for rejection.
7. Rank internship and entry-level/full-time roles in Backend, AI Application/AI Full-stack, Solutions Engineering, and related software-engineering work.
8. Use deterministic, explainable scoring for the initial rank. Use DeepSeek only for redacted extraction and evidence-grounded explanations.
9. Show matched skills, missing skills, concerns, source, original URL, score components, and data-confidence flags.
10. Retain raw job/email data for 30 days, then delete it; retain structured job metadata and matching history longer.
11. Keep a failed provider from aborting the complete collection run.

## Explicit non-goals for V1

- Automatic job application or form submission.
- CAPTCHA solving, anti-detection, proxy rotation, or bypassing access controls.
- A universal scraper for arbitrary career sites.
- Multi-user authentication and remote access.
- Redis, a message queue, pgvector, Kubernetes, or a permanent Python microservice.
- Application tracking; it is a later phase.

## Non-functional requirements

### Reliability

- Provider runs are isolated and idempotent.
- Each run records provider status, start/end time, counts, and an actionable error summary.
- Retries are bounded and use backoff; 429 and access-denied results stop that provider for the run.

### Explainability and correctness

- Unknown fields are neutral for scoring and do not cause rejection.
- DeepSeek output must be schema-validated and linked to source text evidence.
- LLM output cannot invent qualifications or override hard-filter rules.
- Scores and weights are versioned so a later algorithm can be compared with an earlier one.

### Security and privacy

- Bind local services to loopback by default and use strict CORS.
- Keep DeepSeek and Gmail secrets outside Git; provide only an `.env.example`.
- Use Gmail read-only OAuth and store tokens in a local application-data directory.
- Redact name, phone, email, address, and similar identifiers before an external AI request.

### Maintainability

- Spring Boot owns business state, the REST API, scheduling, filtering, scoring, and PostgreSQL access.
- Python is an optional bounded CLI/worker for JobSpy or browser automation only.
- Provider-specific code cannot write directly to the database; it returns the shared ingestion contract.

## Decision record

The user confirmed: DeepSeek API, Gmail automatic sync, China plus Singapore, allowed cities above, recall-first ranking, internships plus entry-level/full-time, broad related software-engineering roles, remote roles included and labelled, 30-day raw-data retention, app-open-only scheduling, and no application tracking in V1.
