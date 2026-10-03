# 8. Dependency security automation

Date: 2026-10-03

## Status
Proposed

## Context
The server is built from about 15 third-party libraries, 3 Gradle plugins and 4 GitHub Actions. Nothing watched them for
vulnerabilities, scanned our own code, or checked that downloaded artifacts hadn't been tampered with. The approach follows the
owner's posts "Automation of Dependency Upgrades" (cooldown, no noisy majors, scanning jobs hold no tokens for other systems) and
"Protection from Software Supply Chain Attacks" (checksums, where a checksum that changes without a version bump is the warning
sign). The owner chose security updates plus weekly minor/patch bumps, and dependency verification now.

## Decision
1. **Detection.**
   - **Dependency graph:** `.github/workflows/dependency-submission.yml` runs `gradle/actions/dependency-submission@v5` on push
     to `main` (and by hand), with `contents: write` only. Dependabot can't resolve Gradle's transitive dependencies itself;
     the submitted graph makes its alerts cover libraries such as ONNX Runtime, DJL and Netty. v5, not v6, for the same Terms
     of Use reason as `ci.yml`.
   - **Code scanning:** `.github/workflows/codeql.yml` runs CodeQL v4 for `java-kotlin` with `build-mode: none`, on PRs, on push
     to `main` and weekly. It doesn't run Gradle, so no dependency or plugin code executes in the scanning job, which has
     `security-events: write`, `contents: read` and `actions: read` only.
2. **Remediation.** Dependabot security updates (a repo setting) open a PR as soon as an advisory applies. `.github/dependabot.yml`
   adds weekly version updates for `gradle` and `github-actions`, with a 5-day cooldown, Gradle minor and patch updates grouped
   into one PR, and major version updates ignored. Neither the cooldown nor the `update-types` ignore applies to security
   updates, so those may still cross a major version. No auto-merge: every PR goes through CI, which runs without secrets for
   Dependabot PRs, and the owner merges.
3. **Integrity.** `gradle/verification-metadata.xml` holds sha256 checksums (and verifies metadata) for every artifact resolved
   by `check pitest shadowJar jarTest`, including plugins, the PMD and PIT tools and the shadow jar's runtime classpath.
   Signatures are not verified. The only trust exceptions are `-sources.jar` and `-javadoc.jar`, so IDE source downloads work.
4. **Regeneration is local, not a workflow.** Dependabot doesn't update the metadata, so its PRs fail CI until someone runs
   `scripts/update-verification-metadata <branch>`. The script regenerates with `--refresh-dependencies`, then compares old and
   new: checksums for new versions and dropped entries are fine, but any changed or added checksum for an artifact that already
   had one stops the script, restores the file and commits nothing. Otherwise it commits and pushes to the branch. A workflow
   with a write token would instead run freshly downloaded plugin code with push rights.

## Consequences
- **Every Dependabot PR needs a human (or Claude on request) step.** That's deliberate: it is the point where someone looks.
  Pushing to a Dependabot branch stops Dependabot rebasing it, which is acceptable for a weekly PR.
- **A changed checksum for an existing version is a supply-chain alarm:** stop and tell the owner; never hand-edit checksums.
- **Gradle merges on mismatch:** `--write-verification-metadata` keeps the old checksum and adds the new one as `<also-trust>`,
  so the script's comparison counts `<also-trust>` values as checksums.
- **Dependency submission may fail on Gradle 9.8.0** (gradle/actions#1088, "Builds are not stored in ascending base node id
  order"). It doesn't affect CI; if it fails, alerts cover only what Dependabot resolves itself until the issue is fixed.
- **Owner steps (repo settings):** enable Dependency graph, Dependabot alerts and Dependabot security updates; leave CodeQL
  "default setup" off. Pushing workflow files may need the `Workflows: write` token permission.
