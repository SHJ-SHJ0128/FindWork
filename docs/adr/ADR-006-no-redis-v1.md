# ADR-006: No Redis in V1

## Decision

Do not add Redis, a queue, or Quartz. PostgreSQL run state plus Spring scheduling and bounded provider attempts are enough for one local operator.

## Consequences

The design does not promise distributed locks or multi-instance throughput. Add a coordination layer only when the runtime becomes multi-process or collection work demonstrably exceeds the local scheduler's safe window.
