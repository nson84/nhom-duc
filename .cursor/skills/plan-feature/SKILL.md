---
name: plan-feature
description: Scope and plan new features or meaningful changes. Use for requirements, design choices, dependency mapping and acceptance criteria.
---

# plan-feature

**Trigger:** Scope and plan new features or meaningful changes. Use for requirements, design choices, dependency mapping and acceptance criteria.

**Critical guard:** When a request proposes a technical solution, check the underlying business goal before agreeing.

## Shared operating constraints
- Inspect the real repository and existing conventions before acting; never assume a language, ORM, OS or framework.
- Challenge questionable user solutions with evidence while preserving their underlying goals. Do not flatter, blindly agree or argue for the sake of disagreeing.
- Prefer the smallest correct, readable, maintainable solution; avoid overengineering and unrelated rewrites.
- Separate observed facts, assumptions and recommendations. Never invent test results, links or tool capabilities.
- Protect user changes, secrets and real data. No destructive operations, production writes, git push/merge or deploy without appropriate explicit authorization.
- Scale validation to task risk; document what was actually checked and what remains unverified.

## Procedure
1. Inspect relevant repository paths and existing behavior; list proven facts separately from assumptions.
2. Specify user outcome, constraints, non-goals, invariants, failure modes, affected modules and acceptance criteria.
3. Challenge flawed proposals with evidence; offer one recommended approach and alternatives only when meaningful.
4. Split work into verifiable vertical slices; name interface/schema dependencies and migration implications.
5. For sensitive or complex algorithms, explain a concrete example and trade-offs before implementation.
6. If asked only to plan, do not edit production code.

## Completion / output
Deliver a scoped plan with risks, dependencies, tests and explicit assumptions.

## Stop conditions
- Halt and report if essential inputs are inaccessible, the task would damage real data, or the user requested analysis only.
- If verification tooling is unavailable, provide exact manual verification steps and mark them **not run**.

## Language and platform
Apply the workflow to Java, C#, Python, JavaScript/TypeScript, Go and Rust, and to web, desktop, mobile, CLI and services **only where the repository actually uses them**. Use this repository's Maven wrapper, frontend scripts, Tauri and isolated MySQL test database as applicable. Do not introduce Docker or another ecosystem's conventions.
