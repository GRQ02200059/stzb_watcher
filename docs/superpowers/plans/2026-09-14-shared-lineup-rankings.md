# Shared Lineup Rankings Implementation Plan

Goal: Build anonymous shared popularity, conservative win-rate, and counter rankings using only battles_v2 records, seed them from the local sample database, expose an authenticated server API, and upgrade the Android research page.

Architecture: A server extractor normalizes battles_v2 payloads into anonymous lineup facts. A repository combines idempotent local seed facts with uploaded payload_json records, deduplicates by battle fingerprint, and a pure ranking engine returns three lists. Android fetches only aggregate results and maps hero IDs locally.

Tech stack: Python 3.9 compatible Flask, SQLite, unittest; Kotlin, Compose, OkHttp, coroutines, MockWebServer and Android instrumentation tests.

Constraints:

- battles_v2 is the only statistical fact source.
- No player, UID, union, coordinate or raw battle fields may leave ranking service responses.
- Keep lineup position order.
- Use Wilson lower bound for ranking and always expose sample count.
- API requires a valid 1.2.0 or newer session.
- Preserve unrelated dirty Android worktree edits and commit only feature files or exact hunks.

## Task 1: Pure server extractor and ranking engine

Create auth_service/lineup_rankings.py and test/test_lineup_rankings.py. Test explicit hero columns, all-hero-info fallback, invalid sample filtering, fingerprint stability, attack/defense outcomes, Wilson values, confidence thresholds, popularity ordering, win-rate ordering and matchup thresholds. Run focused tests red, implement minimal pure code, run green, commit.

## Task 2: Seed and uploaded-fact repository

Extend auth_service/schema.sql with lineup_seed_facts. Create auth_service/lineup_ranking_repository.py and tests. Load seed rows and battle_reports.payload_json, extract facts, prefer uploaded facts on fingerprint collision, and return ranking input. Add scripts/export_lineup_seed.py with temporary SQLite tests. Export only anonymous NDJSON from stzb_45.253.243.238.db. Add CLI lineup import FILE with path and schema validation. Verify idempotent import, commit.

## Task 3: Authenticated ranking API

Create auth_service/lineup_ranking_service.py, wire it in app.py and add POST /v1/lineup-rankings/query in api.py. Add lineup_query rate limiting, exact request validation, 16 KiB limit, limit range, privacy tests, session/version tests and stable response tests. Add runtime files to deployment manifest and a live verifier. Run full server suite and commit.

## Task 4: Deploy and seed

Back up auth.db and verify quick_check. Deploy through the versioned installer. Export anonymous seed NDJSON locally, inspect its keys, upload only that file to a temporary server path, import through the CLI, delete temporary files, verify 80 eligible facts and API output. Confirm service, old auth and battle upload remain healthy.

## Task 5: Android shared ranking client

Create data/research/SharedLineupRankings.kt with models, source interface and OkHttp implementation. Read token from AndroidAuthSessionStore, send version 1.2.0, enforce no-store and response caps, and parse summary/popular/winRate/counters strictly. Add MockWebServer tests red then green. Commit.

## Task 6: Upgrade the existing research screen

Refactor LineupResearchScreen to load shared rankings asynchronously while preserving simulator handoff. Add Popular, Win rate and Counters tabs, search, refresh, loading, empty and error states, confidence labels, sample counts, raw and conservative rates. Use HeroNameResolver. Add repository/state tests and Compose UI tests. Change the tool title to Full-server lineup ranking with an exact staged hunk. Build and commit.

## Task 7: Final verification

Run full server tests, Android JVM tests, Debug/Release builds and focused device instrumentation. Verify online API with a disposable 1.2.0 account, delete the account, verify no raw identity fields, check database integrity, and report APK hash and commits.
