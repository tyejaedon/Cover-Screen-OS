# Agent Protocol

This file is the source of truth for how human contributors and automated agents should work in this repo.

## Core rules
- Use an issue before code changes when the work is planned.
- Attach the issue to the right milestone.
- Never start work on `main`.
- Create a branch first, then make changes.
- Open a pull request when the work is ready.
- Keep messages short, clear, and free of heavy jargon.

## Recommended flow
1. Check for an existing issue.
2. If needed, create or update the issue.
3. Add a milestone and labels.
4. Create a branch from the latest base branch.
5. Make the change.
6. Test it.
7. Open a PR.
8. Link the issue in the PR using closing words like `Closes #123`.

## Branch rules
- Do not work directly on `main`.
- Use a branch name that says what the change is.
  - Good: `docs/readme-blueprint`, `fix/keyboard-toggle`, `feat/overlay-flow`
  - Avoid: `tmp`, `test`, `stuff`

## Pull request rules
- Explain what changed.
- Explain why it changed.
- List the tests you ran.
- Link the issue.
- Use closing words in the body or title when the PR should close an issue.
- Keep the PR description easy to scan.

## Writing style
- Use plain words.
- Keep bullet points short.
- Avoid long blocks of text when a list is enough.
- Prefer direct answers over vague wording.

