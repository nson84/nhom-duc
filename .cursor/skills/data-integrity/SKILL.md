---
name: data-integrity
description: Safeguard data changes, migrations, transactions, concurrency and backups. Use for financial/inventory state, schema evolution and restoration.
---

# data-integrity

**Trigger:** Safeguard data changes, migrations, transactions, concurrency and backups. Use for financial/inventory state, schema evolution and restoration.

**Critical guard:** Never mutate or restore production data to investigate a problem without explicit authorization and a recovery plan.

## Shared operating constraints
- Inspect the real repository and existing conventions before acting; never assume a language, ORM, OS or framework.
- Challenge questionable user solutions with evidence while preserving their underlying goals. Do not flatter, blindly agree or argue for the sake of disagreeing.
- Prefer the smallest correct, readable, maintainable solution; avoid overengineering and unrelated rewrites.
- Separate observed facts, assumptions and recommendations. Never invent test results, links or tool capabilities.
- Protect user changes, secrets and real data. No destructive operations, production writes, git push/merge or deploy without appropriate explicit authorization.
- Scale validation to task risk; document what was actually checked and what remains unverified.

## Procedure
1. Identify invariants, ownership of writes, transaction boundaries and failure scenarios.
2. Assess lost updates, duplicate requests, deadlocks, isolation, locking, retries and idempotency.
3. Choose an appropriate concurrency control strategy and explain with interleaved request examples.
4. Design versioned forward/backward-compatible migrations with validation and rollback/roll-forward strategy.
5. For backups, verify integrity and restore using an isolated environment before promoting recovered data.
6. Test partial failures and duplicate/replayed operations without exposing real data.

## Completion / output
Invariants, chosen consistency mechanism, migration/restore safety plan and integration tests.

## Stop conditions
- Halt and report if essential inputs are inaccessible, the task would damage real data, or the user requested analysis only.
- If verification tooling is unavailable, provide exact manual verification steps and mark them **not run**.

## Language and platform
Apply the workflow to Java, C#, Python, JavaScript/TypeScript, Go and Rust, and to web, desktop, mobile, CLI and services **only where the repository actually uses them**. Use this repository's Maven wrapper, frontend scripts, Tauri and isolated MySQL test database as applicable. Do not introduce Docker or another ecosystem's conventions.
