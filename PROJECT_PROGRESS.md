# Project Progress

Ticked items are committed, compile and pass their relevant checks. Work pauses for review
at the end of each phase. All eight phases are complete; the CI pipeline is validated with
actionlint but has not run on GitHub yet because the repository has not been pushed.

## Phase 1 – Project foundation
- [x] Initialize repository and add README
- [x] Configure Spring Boot and Maven
- [x] Add project package structure
- [x] Configure PostgreSQL and Docker Compose
- [x] Add Flyway database migration setup

## Phase 2 – Database and authentication
- [x] User entity and repository
- [x] Registration DTOs and validation
- [x] User registration service
- [x] Authentication and JWT security
- [x] Authentication unit and integration tests

## Phase 3 – Account management
- [x] Account entity and migration
- [x] Account repository
- [x] Account creation service
- [x] Account REST endpoints
- [x] Account validation and error handling tests

## Phase 4 – Transactions
- [x] Transaction entity and migration
- [x] Income and expense services
- [x] Transaction REST endpoints
- [x] Filtering and pagination
- [x] Transaction unit and integration tests

## Phase 5 – Transfers
- [x] Transfer entity and migration
- [x] Atomic money transfer service
- [x] Idempotency protection
- [x] Transfer API endpoints
- [x] Rollback and concurrency tests

## Phase 6 – Dashboard and budgets
- [x] Dashboard aggregation queries
- [x] Financial summary endpoints
- [x] Categories and budget management
- [x] Budget validation and tests

## Phase 7 – Frontend
- [x] React + TypeScript setup
- [x] Authentication pages
- [x] Dashboard layout
- [x] Account screens
- [x] Transaction screens
- [x] Transfer and budget screens
- [x] API integration (every page runs against the real API; the profile page was added)

## Phase 8 – Quality and delivery
- [x] Dockerfiles
- [x] GitHub Actions CI
- [x] API documentation and Postman collection
- [x] Architecture diagrams and screenshots
- [x] Final README and setup documentation

## Daily improvements
Small follow-up features, one or two commits each.

- [x] 2026-11-25 Search transactions by description (API parameter `q` and a search box on the transactions page)
