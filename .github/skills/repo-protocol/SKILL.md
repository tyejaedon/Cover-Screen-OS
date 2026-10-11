---
name: repo-protocol
description: Follow the repo issue, branch, PR, and plain-language rules.
---

# Repo protocol skill

Use this skill when working in this repo.

## Follow the main flow
1. Find or create an issue.
2. Attach the issue to a milestone.
3. Add the right labels.
4. Create a branch before editing files.
5. Do not work on `main`.
6. Make the change.
7. Test it.
8. Open a PR.
9. Link the issue in the PR.
10. Use closing words like `Closes #123` when needed.

## Branch rules
- Keep the branch name short and clear.
- Use a name that says what changed.
- Do not use `main` for active work.

## PR rules
A good PR should include:
- a short summary
- the issue link
- the branch name
- what changed
- why it changed
- tests run
- closing words if the PR should close an issue

## Writing style
- Use plain words.
- Keep sentences short.
- Avoid extra jargon.
- Be direct and helpful.

## Notes for agents
- If the request is missing an issue, create one or ask for one only if the workflow truly needs it.
- If the request is missing a milestone, add one before the change is treated as ready.
- Keep explanations simple for humans to review.

