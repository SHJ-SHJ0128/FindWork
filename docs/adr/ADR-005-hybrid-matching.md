# ADR-005: Explainable hybrid matching

## Decision

Hard filters and the initial 0–100 score are deterministic and versioned. DeepSeek performs redacted structured extraction and evidence-grounded explanation only. No embeddings/pgvector in V1.

## Consequences

The product remains useful if DeepSeek is unavailable, and a reviewer can see why a job ranked highly. Embeddings can be evaluated later against a labelled set rather than added speculatively.
