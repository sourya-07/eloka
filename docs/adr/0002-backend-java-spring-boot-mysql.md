# ADR-0002: Backend stack: Java 25, Spring Boot 4, MySQL 8.4

- **Status:** accepted
- **Date:** 2026-10-14
- **Deciders:** project owner and team

## Context

The onboarding backend handles KYC data, a state machine, staff review and a provisioning saga against EPS. It needs strong typing, mature security, transactions and long-term support. The team chose MySQL (OQ-T1). The full comparison is in [`onboarding/ARCHITECTURE.md`](../onboarding/ARCHITECTURE.md) §5.

## Decision

- **Java 25 (LTS)** with **Spring Boot 4.1**, **Spring Modulith** for module boundaries, Spring Security, Spring Data JPA.
- **MySQL 8.4 LTS**, schema managed by **Flyway**.
- **Gradle (Kotlin DSL)** build with the Gradle wrapper; Java toolchain downloaded automatically.
- Local infrastructure through `server/compose.yaml`, started by `bootRun` (spring-boot-docker-compose). Tests use Testcontainers.

## Consequences

- The owning team needs Java / Spring skills (OQ-T5 asks who owns it).
- Module boundaries are checked by a test (`ModularityTests`), so the monolith can be split later if needed.
