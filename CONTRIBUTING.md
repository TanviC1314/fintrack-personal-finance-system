# Contributing to FinTrack

FinTrack is a portfolio project, but it is built like a real one. Contributions, suggestions and
bug reports are welcome. This page explains how to set up, what a good change looks like and how
to check it before you open a pull request.

## Set up

You need Git, JDK 21, Node 22 and Docker (the integration tests start PostgreSQL containers).

```bash
git clone <repository-url> && cd fintrack-personal-finance-system
cp .env.example .env     # then set POSTGRES_PASSWORD and JWT_SECRET to your own values
docker compose up -d --build --wait
```

The app is then at <http://localhost:3000> and the API documentation at
<http://localhost:8080/swagger-ui.html>. For day-to-day development, run the database from
Compose and the two apps directly (see the README, "Running without Docker for the apps").

## Check your change

Run these before opening a pull request. The CI pipeline runs the same checks.

| What | Command | Needs Docker |
| --- | --- | --- |
| Backend unit tests | `cd backend && ./mvnw test` | No |
| Backend unit and integration tests, coverage | `cd backend && ./mvnw verify` | Yes |
| Frontend lint, tests, build | `cd frontend && npm run lint && npm test && npm run build` | No |
| Whole stack | `bash .github/scripts/smoke-test.sh http://localhost:3000` | Yes |

If you change the REST API, refresh the stored API description and commit it with the change:

```bash
cd backend && ./mvnw verify -Dopenapi.update=true
```

A test fails when `docs/openapi.json` no longer matches the running API.

## Commits

One small, logical change per commit, and every commit should build and pass its tests. Use
[Conventional Commits](https://www.conventionalcommits.org/):

```
feat(transfers): add idempotency protection
fix(accounts): reject archiving an account that still holds money
test(budgets): cover the first and last day of the month
docs(readme): document the Docker setup
ci(github-actions): add the frontend job
```

Explain *why* in the body when it is not obvious. Please do not mix formatting-only changes or
generated files into a feature commit.

## Standards

**Money.** Use `BigDecimal` in Java and `NUMERIC` in PostgreSQL, never `float` or `double`.
Amounts have at most two decimals, and amounts in different currencies are never added together.

**Layers.** Controller, service, repository. Controllers translate HTTP and never hold business
rules. Services own the rules and the transactions, and are the only code that changes a balance.
Entities are never returned from the API; use DTOs.

**Data isolation.** Every query that returns user data is scoped to the caller's user id.
A resource that belongs to someone else must look exactly like one that does not exist (404).

**Database changes.** Add a new Flyway migration (`V<next>__description.sql`). Never edit a
migration that has already been applied. If a migration moves existing data, test it against rows
created under the old schema (see `CategoryMigrationIntegrationTest`).

**Tests.** Test behaviour, not implementation. A new rule needs a test for the success path and
for each way it can fail. Name unit tests `*Test` and tests that need Docker `*IntegrationTest`,
because the build runs them in separate phases. When a test is meant to catch a bug, check that
it actually fails when the bug is present.

**Secrets.** Never commit passwords, keys or tokens. `.env` is git-ignored; add new settings to
`.env.example` with a placeholder value.

**Frontend.** Validate in the form (zod schemas that mirror the backend) and show the server's own
message when it disagrees. Every data view needs loading, empty and error states, and state must
never be conveyed by colour alone.

## Reporting a problem

Open an issue with what you did, what you expected and what happened, plus the browser or request
involved. For anything that looks like a security problem, please do not post details publicly;
contact the maintainer directly instead.
