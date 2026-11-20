#!/usr/bin/env bash
# Smoke test for a running FinTrack stack, through the web server (nginx), the way a browser reaches it.
# Usage: smoke-test.sh [base-url]      (default: http://localhost:3000)
# It registers a throwaway user, logs in, creates an account and a transaction, and checks the result.
set -euo pipefail

BASE="${1:-http://localhost:3000}"
EMAIL="smoke-$(date +%s%N)@example.com"
PASSWORD="smokeTest123"
JSON='Content-Type: application/json'

step() { printf '\n== %s\n' "$1"; }
json_field() { python3 -c "import sys, json; print(json.load(sys.stdin)$1)"; }
status_of() { curl -s -o /dev/null -w '%{http_code}' "$@"; }
expect() {  # expect <description> <expected> <actual>
  if [ "$2" != "$3" ]; then echo "FAIL: $1 (expected $2, got $3)"; exit 1; fi
  echo "ok: $1"
}

step "The web app and a client-side route are served"
expect "GET /" 200 "$(status_of "$BASE/")"
expect "GET /transactions (SPA fallback)" 200 "$(status_of "$BASE/transactions")"

step "The API rejects an unauthenticated request"
expect "GET /api/v1/users/me without a token" 401 "$(status_of "$BASE/api/v1/users/me")"

step "Register and log in"
expect "POST /auth/register" 201 "$(status_of -X POST "$BASE/api/v1/auth/register" -H "$JSON" \
  -d "{\"name\":\"Smoke Test\",\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}")"
TOKEN="$(curl -fsS -X POST "$BASE/api/v1/auth/login" -H "$JSON" \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}" | json_field "['accessToken']")"
AUTH="Authorization: Bearer $TOKEN"
expect "GET /users/me with the token" 200 "$(status_of "$BASE/api/v1/users/me" -H "$AUTH")"

step "Create an account and record income and an expense"
ACCOUNT="$(curl -fsS -X POST "$BASE/api/v1/accounts" -H "$JSON" -H "$AUTH" \
  -d '{"name":"Smoke account","type":"CURRENT","currency":"EUR","openingBalance":100.00}' | json_field "['id']")"
expect "POST /transactions (income 50.25)" 201 "$(status_of -X POST "$BASE/api/v1/transactions" -H "$JSON" -H "$AUTH" \
  -d "{\"accountId\":\"$ACCOUNT\",\"type\":\"INCOME\",\"amount\":50.25,\"transactionDate\":\"2025-01-15\"}")"
expect "POST /transactions (overdraft is refused)" 422 "$(status_of -X POST "$BASE/api/v1/transactions" -H "$JSON" -H "$AUTH" \
  -d "{\"accountId\":\"$ACCOUNT\",\"type\":\"EXPENSE\",\"amount\":999.00,\"transactionDate\":\"2025-01-15\"}")"
BALANCE="$(curl -fsS "$BASE/api/v1/accounts/$ACCOUNT" -H "$AUTH" | json_field "['currentBalance']")"
expect "account balance is 100.00 + 50.25" 150.25 "$BALANCE"

step "Dashboard aggregates are available"
expect "GET /dashboard/summary" 200 "$(status_of "$BASE/api/v1/dashboard/summary?month=2025-01" -H "$AUTH")"

printf '\nSmoke test passed.\n'
