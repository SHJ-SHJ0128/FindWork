# ADR-004: Local-first development

## Decision

Run Vue and Spring natively, run only PostgreSQL with Docker Compose, and bind services to loopback. Sync schedules run only while the application is open.

## Consequences

There is no always-on guarantee while the laptop is off or the app is closed. A visible missed-run state and explicit catch-up action are preferred over an unrequested cloud deployment.
