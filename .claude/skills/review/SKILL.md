---
name: review
description: Run the code-reviewer agent on the current branch (or a named branch) against main and summarise blocking and non-blocking findings.
argument-hint: "[branch]"
---

1. If a branch was given (`$ARGUMENTS`), check that it exists. Review it without switching branches: tell the reviewer to use `git diff main...<branch>`. Otherwise review the current branch against `main`.
2. Run the `code-reviewer` agent.
3. Show the user the verdict, the BLOCKING findings (`file:line` and the fix), then the suggestions. Don't change any code unless the user asks; if they do, hand the fixes to `java-implementer`.
