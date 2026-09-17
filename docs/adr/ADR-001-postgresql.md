# ADR-001: PostgreSQL as the V1 durable store

## Decision

Use PostgreSQL for canonical jobs, source occurrences, candidate profile, collection runs, and deterministic matches. Use JSONB only for provider-specific and derived evidence.

## Context

The product needs relational uniqueness, provenance, filtering, timestamps, and repeatable cleanup. The user prefers PostgreSQL. A document store would make exact deduplication and constraints less explicit.

## Consequences

The schema uses columns for queryable fields and a `job_posting` occurrence table for cross-source provenance. Redis and pgvector are not installed until measured requirements justify them.
