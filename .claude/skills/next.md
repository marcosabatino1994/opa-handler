# /next — Session Start, In-Progress Task Detection & Workspace Sync

Detect any in-flight tasks from the `tasks/` folder, resolve local workspace
state, and sync with the remote before any new work begins.
Does NOT create branches — that happens in /build after a plan is approved.

---

## Step 1 — Scan for in-progress tasks

List all files in `tasks/` matching `TASK-*.md`. Exclude `_template.md`.

For each file found, read the YAML frontmatter (the block between the first
`---` and the second `---`) and extract the `status:` field.

Collect every file where status is NOT `DONE` and NOT `REJECTED`.
Call this the **in-progress list**.

If the in-progress list is empty → skip to Step 3.

If the in-progress list is not empty, print a table:

```
In-progress tasks found:
┌──────────┬──────────────────────────────────┬────────────────┬───────────────┐
│ ID       │ Title                            │ Status         │ Next skill    │
├──────────┼──────────────────────────────────┼────────────────┼───────────────┤
│ TASK-003 │ Add OPA policy evaluation        │ APPROVED       │ /build        │
│ TASK-005 │ Fix token refresh race condition │ DRAFT          │ /architect    │
└──────────┴──────────────────────────────────┴────────────────┴───────────────┘
```

Status → next skill mapping:
- `DRAFT` → `/architect` (design not yet approved)
- `APPROVED` → `/build` (ready to implement)
- `BUILD_COMPLETE` → `/review` (ready to push)

Ask the user:
> One or more tasks are in progress. Would you like to:
> [1] Continue an in-progress task — specify which task ID
> [2] Start a completely new task
>
> Reply with 1 or 2 (and the task ID if option 1).

Wait for the user's answer before proceeding.

If option 1: state the task document path and the skill to run next. Stop here.
If option 2: continue to Step 2.

---

## Step 2 — Check local workspace state

Run `git status` and `git stash list`.

If uncommitted changes or stash entries exist:
- List every modified/untracked file and every stash entry.
- Ask the user:
  > [A] Continue working on these in-progress changes
  > [B] Stash and park them (git stash push -m "parked before fresh task")
  > [C] Discard everything — WARNING: irreversible
  >
  > Reply with A, B, or C.

Do NOT proceed until the workspace is clean.

If working tree is clean and stash is empty → continue.

---

## Step 3 — Sync with remote

```
git fetch origin
git log HEAD..origin/develop --oneline
```

If commits exist on `origin/develop` ahead of HEAD:
- Print each commit (short SHA + message).
- Recommend: `git rebase origin/develop`
- Ask: rebase now, or start from current HEAD?

Wait for user answer before rebasing.

If already up to date → continue.

---

## Step 4 — Output summary

```
Session Start Summary
──────────────────────────────────────────────
In-progress tasks : <N found / none>
Local workspace   : clean / in-progress (<files>)
Remote            : up to date / N commits behind develop
Current branch    : <branch name>
──────────────────────────────────────────────
Ready for: /architect (new task) | /build (TASK-NNN) | /review (TASK-NNN)
```
