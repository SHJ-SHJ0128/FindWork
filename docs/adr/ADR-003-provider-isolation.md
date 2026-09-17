# ADR-003: Isolated provider adapters

## Decision

Every source is an independently enabled adapter. Adapters return provider-neutral import records, carry adapter/version/provenance metadata, and cannot abort sibling providers.

## Consequences

LinkedIn/BOSS/JobSpy/browser sources can be opt-in experiments without becoming a platform dependency. Greenhouse, Lever, Gmail, and manual import can remain stable even when a risky provider is blocked.
