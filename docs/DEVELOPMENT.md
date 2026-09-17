# Job Copilot — Development Guide

## Prerequisites

- Java 21 and Maven or Gradle for Spring Boot 3.
- Node.js LTS and npm/pnpm for Vue 3 + Vite.
- Python 3.10+ only when JobSpy/Playwright adapters are enabled.
- Docker Desktop for PostgreSQL.
- A DeepSeek API key and a Gmail OAuth client only when those integrations are enabled.

Exact versions are intentionally not frozen until implementation starts; verify current compatible versions then.

## Local topology

```text
Vue dev server       http://127.0.0.1:5173
Spring Boot API      http://127.0.0.1:8080
PostgreSQL           127.0.0.1:5432
Python collector     spawned CLI, no public port
```

Only PostgreSQL runs in Docker Compose for V1. Vue and Spring run natively. Redis, FastAPI, and a queue are not reserved “just in case”.

## Environment

Copy `.env.example` to an untracked `.env` and fill in values. Never commit the copy. The Spring process should load these values; the Python CLI receives only the bounded inputs it needs and never receives database credentials.

## Expected implementation standards

- Flyway migrations and parameterized SQL.
- Java formatter/checkstyle plus unit and PostgreSQL integration tests.
- Vue TypeScript strict mode, formatter, linter, and component tests.
- Python Ruff/formatter and small adapter contract tests.
- Structured logs with provider/run IDs; no sensitive payloads.
- OpenAPI for the Spring REST boundary.
- Provider contract tests with fixtures; one provider failure test proving partial success.

## Verification gates

Before each phase is accepted, run focused tests for the changed module and the smallest relevant integration test. Full end-to-end collection is not required until provider adapters exist. Unexecuted checks must be reported as `NOT VERIFIED`.
