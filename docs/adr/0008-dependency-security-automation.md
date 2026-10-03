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
   - **Code scanning:** SpotBugs with the FindSecBugs plugin runs as `spotbugsMain` inside `./gw check`, so locally and in CI.
     It scans production classes only (`spotbugsTest` is disabled), reports only the `SECURITY` category
     (`config/spotbugs/security-include.xml`) and fails the build on any finding; the HTML report is
     `build/reports/spotbugs/main.html`. A finding is fixed, or excluded in a `config/spotbugs/exclude.xml` (created and wired in as `excludeFilter` when first needed) with the narrowest
     match and a `<!-- justified: -->` comment, listed in the PR like a PMD suppression. CodeQL was the first choice but
     can't serve here: this is a personal repository, not in an organisation, so code scanning alerts can't be enabled, and
     CodeQL's terms only allow free use on public repositories. SpotBugs also runs inside the gates rather than as a separate
     report someone must read.
2. **Remediation.** Dependabot security updates (a repo setting) open a PR as soon as an advisory applies. `.github/dependabot.yml`
   adds weekly version updates for `gradle` and `github-actions`, with a 5-day cooldown, Gradle minor and patch updates grouped
   into one PR, and major version updates ignored. Neither the cooldown nor the `update-types` ignore applies to security
   updates, so those may still cross a major version. No auto-merge: every PR goes through CI, which runs without secrets for
   Dependabot PRs, and the owner merges.
3. **Integrity.** `gradle/verification-metadata.xml` holds sha256 checksums (and verifies metadata) for every artifact resolved
   by `check pitest shadowJar jarTest`, including plugins, the PMD and PIT tools and the shadow jar's runtime classpath.
   Signatures are not verified. The only trust exceptions are `-sources.jar` and `-javadoc.jar`, plus the Gradle distribution's own `gradle-<version>-src.zip` (IntelliJ fetches it during sync for build-script navigation), so IDE source downloads work. None of these is compiled or run.
4. **Regeneration is automated for Dependabot PRs, with a guard.** Dependabot doesn't update the metadata
   (dependabot-core#1996), so `.github/workflows/dependabot-verification-metadata.yml` does, on Dependabot PRs that change
   `gradle/libs.versions.toml`. No job both runs dependency or plugin code and holds a write token:
   - **`regenerate`** (`contents: read`, no credentials kept) checks out the PR head, regenerates with
     `--write-verification-metadata sha256 --refresh-dependencies check pitest shadowJar jarTest`, runs the guard and, if the
     file changed, uploads only that file as an artifact.
   - **`commit`** (`contents: write`, `actions: write`) runs no Gradle and no Java. It stops without pushing if the branch head
     is no longer the commit `regenerate` built. It checks the artifact is exactly one XML file with a
     `<verification-metadata>` root, and runs the guard from the **base** branch against the branch's current file, so a PR
     can't bring a weaker guard. Then it commits as `github-actions[bot]`, pushes to the PR branch and starts `ci.yml` with
     `workflow_dispatch`: a push made with `GITHUB_TOKEN` starts no `pull_request` runs, so the workflow can't trigger itself,
     but a dispatch always runs. No auto-merge.

   **The guard**, `scripts/verification-checksum-guard <old> <new>`, is the one rule for the workflow and the local script:
   checksums for new versions and dropped entries are fine (a new artifact under a verified version only warns), but a changed
   or added checksum for an artifact that already had one, any change to the `<configuration>` section, or any line Gradle
   wouldn't write fails it. The last check matters because the comparison reads one element per line.

   `scripts/update-verification-metadata [<branch>]` stays for manual cases: it regenerates with the same tasks, runs the guard,
   restores the file and commits nothing on an alarm, and otherwise commits and pushes. Regenerating with only `help` was
   tried and rejected: it misses detached configurations such as Spotless's google-java-format and the PIT tooling.

## Consequences
- **Dependabot PRs get their checksums without a manual step;** the owner still reviews and merges every PR. The bot's commit
  stops Dependabot rebasing the branch, which is acceptable for a weekly PR. When Dependabot does rebase or recreate it, the
  workflow runs again on the new head.
- **A changed checksum for an existing version is a supply-chain alarm:** a red `regenerate` check (or the local script
  stopping) means stop and tell the owner; never hand-edit checksums.
- **Gradle merges on mismatch:** `--write-verification-metadata` keeps the old checksum and adds the new one as `<also-trust>`,
  so the script's comparison counts `<also-trust>` values as checksums.
- **Dependency submission may fail on Gradle 9.8.0** (gradle/actions#1088, "Builds are not stored in ascending base node id
  order"). It doesn't affect CI; if it fails, alerts cover only what Dependabot resolves itself until the issue is fixed.
- **Owner steps (repo settings):** enable Dependency graph, Dependabot alerts and Dependabot security updates; leave CodeQL
  "default setup" off. Pushing workflow files may need the `Workflows: write` token permission.
