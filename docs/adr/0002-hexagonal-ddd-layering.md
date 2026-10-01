# 2. Hexagonal architecture with DDD bounded contexts

Date: 2026-10-01

## Status
Accepted

## Context
Business rules must stay testable in isolation and independent of frameworks and infrastructure.

## Decision
- Each bounded context is a top-level package with `domain`, `application`, `adapter.in` and `adapter.out` sub-packages.
- Dependencies point inward only; the domain depends on nothing but the JDK.
- ArchUnit tests enforce the layering and the absence of cycles between contexts.
- Quality gates: JaCoCo ≥ 90% line coverage on domain/application, PIT ≥ 80% mutation score on domain.

## Consequences
Frameworks (e.g. Spring Boot) can be added later in adapters only. Cross-context interaction needs explicit ports or events, which costs some boilerplate.
