# Job Copilot — Matching Algorithm

## Principles

The system is recall-first. Hard filtering removes explicit impossibilities; missing or ambiguous data is retained and labelled for review. DeepSeek never silently invents qualifications or controls a rejection.

## Pipeline

```text
raw provider record
  -> normalized fields + extracted facts
  -> hard filters with reason codes
  -> deterministic feature score
  -> optional DeepSeek explanation
  -> score/evidence/version persisted
```

## Hard filters

Reject only when the source explicitly proves a configured exclusion:

- senior/staff/principal or experience clearly above the profile;
- location outside Chongqing, Chengdu, Guangzhou, Shenzhen, Hangzhou, Singapore, or an explicitly allowed remote role;
- incompatible work authorization when known;
- unwanted employment type, expired posting, excluded company, or excluded keyword;
- an explicit non-software job family.

Use reason codes such as `EXPERIENCE_TOO_HIGH`, `LOCATION_NOT_SUPPORTED`, `EXCLUDED_KEYWORD`, and `EXPIRED`. Unknown location, salary, education, and experience do not reject; they produce `NEEDS_REVIEW` flags.

## Initial score

Use a 0–100 weighted score with weights stored in configuration and copied into each match record. Initial weights are a starting point, not a claim of validated accuracy:

| Feature | Weight |
| --- | ---: |
| Skill overlap and required-skill coverage | 30 |
| Role-family/title similarity | 20 |
| Experience compatibility | 15 |
| Location/remote preference | 15 |
| Education compatibility | 10 |
| Employment type and internship/entry-level fit | 10 |

Renormalize across known features when a field is missing. Apply a soft location preference: China receives the preferred-region boost, Chongqing > Chengdu > other allowed cities, while Singapore and remote remain eligible. Remote receives equal ranking treatment but a `REMOTE` label.

## Evidence and explanation

For every scored job, persist matched skills, missing skills, concerns, filter flags, feature values, and source snippets. DeepSeek may convert these facts into a readable explanation. The prompt must require a JSON schema containing `summary`, `matched`, `missing`, `concerns`, and `evidence`; every item must cite a supplied snippet or structured field. If the API fails, show deterministic evidence and mark the explanation unavailable.

## DeepSeek responsibilities

- Extract structured skills, role family, experience bounds, location, employment type, and explicit requirements from redacted text.
- Normalize synonyms for review, retaining the original phrase.
- Generate an evidence-grounded explanation.

DeepSeek must not decide hard-filter outcomes, add candidate experience, or produce an untraceable score. Cache extraction/explanation by content hash, prompt/schema version, and model identifier. Do not add embeddings or pgvector in V1.

## Evaluation before tuning

Create a small manually labelled set of jobs (`relevant`, `borderline`, `irrelevant`) across China, Singapore, remote, internship, and entry-level roles. Measure recall@N and false-rejection count before changing weights. The user's stated priority makes false negatives more costly than review volume.
