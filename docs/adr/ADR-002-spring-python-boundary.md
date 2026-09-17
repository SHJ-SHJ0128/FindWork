# ADR-002: Spring modular monolith with bounded Python collector

## Decision

Spring Boot owns the API, database, scheduler, business rules, and DeepSeek gateway. Python is a short-lived CLI/worker only for JobSpy or browser automation that cannot be maintained in Java. Python emits the shared import contract and never writes PostgreSQL.

## Consequences

There is no permanent FastAPI service, service discovery, or cross-service transaction. If Python later serves multiple clients, promote the contract to an API in a separate ADR.
