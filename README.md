# FinTrack – Personal Finance and Transaction Management System

> **Status: early development.** This repository is being built incrementally, one small
> reviewed commit at a time. Nothing below is claimed as working until it is ticked in
> [PROJECT_PROGRESS.md](PROJECT_PROGRESS.md).

FinTrack is a simulated personal finance platform. Users manage multiple accounts, record
income and expenses, move money between their own accounts and see where their money goes.
It does **not** connect to real banks, process real payments or store card details.

It is a portfolio project focused on backend engineering: correctness of money handling,
database integrity, secure APIs and meaningful automated tests.

## Problem statement

Spreadsheets and notes make it easy to lose track of spending and hard to trust balances.
FinTrack explores how a small finance backend can keep balances consistent, keep each
user's data isolated, and turn raw transactions into useful monthly summaries.

## Planned features

- User registration and JWT authentication (BCrypt password hashing)
- Multiple accounts per user (Current, Savings, Cash)
- Income, expense and transfer tracking with filtering and pagination
- Atomic, idempotent transfers between a user's own accounts
- Dashboard: total balance, monthly income/expenses/savings, spending by category, trends
- Categories and monthly budgets with utilisation tracking
- Audit history for important financial operations
- React + TypeScript dashboard

## Planned technology stack

| Area | Technology |
| --- | --- |
| Backend | Java 21, Spring Boot 3.x, Spring Web / Data JPA / Security / Validation, Maven |
| Database | PostgreSQL, Flyway migrations |
| Testing | JUnit 5, Mockito, Spring Boot Test, MockMvc, Testcontainers, JaCoCo |
| API docs | Swagger / OpenAPI, Postman collection |
| DevOps | Docker, Docker Compose, GitHub Actions |
| Frontend | React, TypeScript, Vite, Tailwind CSS, Recharts |

## Architecture (planned)

Layered, feature-oriented backend: `Controller → Service → Repository → Database`.
Controllers only handle HTTP and DTO mapping, services own business rules and
transactions, repositories own persistence. JPA entities are never exposed through the API.
Money is always `BigDecimal` backed by `NUMERIC` columns.

Detailed architecture and ER diagrams will be added with the database design.

## Getting started

Prerequisites: JDK 21 and Docker. Maven is provided by the wrapper (`backend/mvnw`).

Start PostgreSQL locally:

```bash
cp .env.example .env        # then set your own POSTGRES_PASSWORD in .env
docker compose up -d --wait
```

The database listens on `127.0.0.1:5432` only. `.env` is git-ignored; never commit it.
Full setup instructions will be added as the project foundation lands. See
[PROJECT_PROGRESS.md](PROJECT_PROGRESS.md) for current status.

## Future improvements

To be refined once the core is complete.

## Disclaimer

FinTrack is a learning and portfolio project using simulated data only. It has not been
security audited and is not production ready.

## License

A license file will be added to the repository.
