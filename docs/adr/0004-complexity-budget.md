# 4. Complexity budget: design for low NPath

Date: 2026-10-01

## Status
Accepted (2026-10-01, by the owner)

## Context
The number of tests a method needs grows with its number of execution paths (NPath). Unchecked conditionals multiply. A method with five independent `if`s has 32 paths, and edge cases get re-tested at every layer that repeats the branching. We want each type to need only a handful of focused tests, with acceptance-criteria tests as the behavioural safety net.

## Decision
1. **Budget per production method:**
   - NPath ≤ 16
   - cyclomatic complexity ≤ 5
   - cognitive complexity ≤ 7
   
   It applies to `src/main` only; test code is exempt.
2. **Enforcement:** PMD 7.28.0 runs only these three rules (`config/pmd/complexity.xml`) as the `pmdComplexity` Gradle task, which `check` depends on. So `./gw check` and CI fail on any violation. PMD reports when a value *reaches* its level, so the configured levels are each limit plus one.
3. **PMD runs through its CLI in a `JavaExec`,** not through Gradle's `pmd` plugin. The plugin's worker daemon connects back to Gradle over localhost, which the Claude Code sandbox refuses (`SocketException: Operation not permitted`). The CLI needs no IPC and behaves the same in CI.
4. **Design principles** in `CLAUDE.md` ("Design for low NPath") say where branching goes instead:
   - value objects (parse, don't validate)
   - behaviour on sealed variants and enums
   - strategies chosen once
   - rule tables
   - no flag or nullable parameters
   - sealed result types
   - pipelines
   - thin orchestrators
5. **Suppressions:** only `@SuppressWarnings("PMD.<Rule>")` on the narrowest element, with a `// justified:` comment. The reviewer must approve each one, and the PR lists them. Target: none.

## Consequences
- More, smaller types. Each decision is tested once on the type that owns it, and callers compose them without branching.
- Acceptance-criteria tests stay. Edge-case permutations live in focused unit tests rather than being multiplied through application and adapter tests.
- Existing catalog code is refactored to the budget before PR #2 merges. Search and MCP are built under it from the start.
- PMD and its CLI are build-time only. They add nothing to the runtime classpath.
