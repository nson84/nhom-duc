---
name: test-engineering
description: Choose and implement unit, integration, contract and E2E tests by risk. Use for changing behavior, fixing regressions and test design.
---

# test-engineering

**Trigger:** Choose and implement unit, integration, contract and E2E tests by risk. Use for changing behavior, fixing regressions and test design.

**Critical guard:** Do not fabricate test results or mock the very logic under test.

## Shared operating constraints
- Inspect the real repository and existing conventions before acting; never assume a language, ORM, OS or framework.
- Challenge questionable user solutions with evidence while preserving their underlying goals. Do not flatter, blindly agree or argue for the sake of disagreeing.
- Prefer the smallest correct, readable, maintainable solution; avoid overengineering and unrelated rewrites.
- Separate observed facts, assumptions and recommendations. Never invent test results, links or tool capabilities.
- Protect user changes, secrets and real data. No destructive operations, production writes, git push/merge or deploy without appropriate explicit authorization.
- Scale validation to task risk; document what was actually checked and what remains unverified.

## Procedure
1. Create a test matrix for happy paths, boundaries, invalid input, dependencies and failure recovery.
2. For bug fixes, prefer a failing regression test before the fix where practical.
3. Choose unit for deterministic logic; integration for persistence/transactions; contract for interfaces; E2E for critical user journeys.
4. Use stack-native test runners and ephemeral fixtures; never target production databases or live payment endpoints.
5. Exercise concurrency, idempotency and data corruption scenarios for critical write paths.
6. Run the relevant suites, record commands and failures; do not weaken assertions simply to produce green results.

## Completion / output
Test matrix, added/updated tests, executed commands, pass/fail and remaining gaps.

## Stop conditions
- Halt and report if essential inputs are inaccessible, the task would damage real data, or the user requested analysis only.
- If verification tooling is unavailable, provide exact manual verification steps and mark them **not run**.

## Language and platform
Apply the workflow to Java, C#, Python, JavaScript/TypeScript, Go and Rust, and to web, desktop, mobile, CLI and services **only where the repository actually uses them**. Use this repository's Maven wrapper, frontend scripts, Tauri and isolated MySQL test database as applicable. Do not introduce Docker or another ecosystem's conventions.
