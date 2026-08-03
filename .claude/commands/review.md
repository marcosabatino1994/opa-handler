# /review — Gated Quality Gate, Push & PR

Final verification before the branch is pushed and a PR is opened.
Requires the task document to have `status: BUILD_COMPLETE` — any other
status is a hard block.

---

## Step 0 — Locate the task document

Check the conversation for a file path of the form `tasks/TASK-NNN-*.md`.

If found → read that file.

If not found:
- List all `tasks/TASK-*.md` files (exclude `_template.md`).
- Read each file's frontmatter and collect those with `status: BUILD_COMPLETE`.
- If exactly one → use it. Print its path.
- If multiple → list them and ask the user which to review. Wait for answer.
- If zero → stop:
  ```
  [HARD BLOCK] No task document with status BUILD_COMPLETE found in tasks/.
  Run /build first and complete the implementation.
  ```

---

## Step 1 — Gate check (HARD BLOCK)

Read the task document. Parse the YAML frontmatter and extract `status:`.

If `status` is NOT exactly `BUILD_COMPLETE`:
```
[HARD BLOCK] Cannot run /review.
Task document : <file path>
Current status: <actual status>
Required status: BUILD_COMPLETE

  DRAFT          → run /architect first
  APPROVED       → run /build first
  REJECTED       → task is closed
  DONE           → task is already complete
```
Stop. Do not execute any further step.

---

## Step 2 — Branch safety check

```bash
git branch --show-current
```

If the current branch is `main` or `develop` → stop:
```
[BLOCK] You are on <branch>. Checkout the feature branch first:
  git checkout <branch from task document>
```

Verify at least one commit exists ahead of the base branch:
```bash
git log origin/<base_branch>..HEAD --oneline
```

If no commits are ahead → stop with an explanation.

---

## Step 3 — Re-run full test suite

Read which layers were touched from Section 2 of the task document.

**Backend (if touched):**
```bash
cd backend
./mvnw test
```

**Frontend (if touched):**
```bash
cd frontend
ng test --watch=false --browsers=ChromeHeadless
```

If any test fails → stop. Do NOT push.
```
[BLOCK] Tests failed. Fix the failures and re-run /review.
```
Record result: PASSED or SKIPPED (not touched).

---

## Step 4 — Version bump

Read the version bump table from Section 2 of the task document.

**Backend (if planned):**
Edit `backend/pom.xml` — change `<version>` to the proposed version.

**Frontend (if planned):**
Edit `frontend/package.json` — change `"version"` to the proposed version.

Commit the version bump separately (never mixed with feature code):
```bash
git add backend/pom.xml        # or frontend/package.json or both
git commit -m "chore(version): bump <layer> to <new version>"
```

If no version bump was planned (chore/docs task) → skip this step.
Record before and after versions for each layer bumped.

---

## Step 5 — Push policies check

Read `branch:` and `base_branch:` from the task document frontmatter.

| Branch prefix | Allowed base | Version bump required |
|---------------|-------------|-----------------------|
| `feature/*`   | develop     | Minor or Patch        |
| `fix/*`       | develop     | Patch                 |
| `hotfix/*`    | main        | Patch                 |
| `chore/*`     | develop     | None                  |

If branch targets `main` and is NOT a `hotfix/*` → block:
```
[BLOCK] Only hotfix branches may target main directly.
```

If branch name does not match any prefix → warn and ask the user to confirm.

---

## Step 6 — Push branch

```bash
git push -u origin <branch>
```

If push fails → show the error and stop. Do not open a PR.

---

## Step 7 — Open Pull Request

Use the GitHub CLI. Derive PR body from the task document.

**Feature / fix / chore → develop:**
```bash
gh pr create \
  --base develop \
  --title "<type>(<scope>): <title from task document>" \
  --body "$(cat <<'PRBODY'
## Summary
- <2-3 bullets from Section 1 What/Why>

## Task document
tasks/<ID>-<branch-slug>.md

## Type
- [ ] Feature  - [ ] Fix  - [ ] Hotfix  - [ ] Chore

## Layers touched
- [ ] Backend (Quarkus)  - [ ] Frontend (Angular)

## Version
- Backend: <before> → <after> (or unchanged)
- Frontend: <before> → <after> (or unchanged)

## Test coverage
- Backend tests: PASSED / not applicable
- Frontend tests: PASSED / not applicable

## Notes
<migration steps, env var changes, deployment notes from architect risks, or "none">
PRBODY
)"
```

**Hotfix → main:**
Use `--base main`. Then also open a backport PR to develop:
```bash
gh pr create \
  --base develop \
  --title "hotfix(<scope>): backport — <title>" \
  --body "Backport of hotfix <PR URL> to develop."
```

Capture the PR URL(s) from the CLI output.

---

## Step 8 — Update the task document

Edit the task document. Fill in **Section 4 — Review Result**:

| Field              | Value                                              |
|--------------------|----------------------------------------------------|
| Review date        | Today (YYYY-MM-DD)                                 |
| Backend test row   | PASSED / FAILED / SKIPPED                          |
| Frontend test row  | PASSED / FAILED / SKIPPED                          |
| Backend version    | Before → After, or "unchanged"                     |
| Frontend version   | Before → After, or "unchanged"                     |
| PR URL             | URL from Step 7                                    |
| Hotfix backport PR | URL or "not applicable"                            |
| Deployment notes   | From architect risks section, or "none"            |
| Final status       | `DONE`                                             |

Update frontmatter: `status: BUILD_COMPLETE` → `status: DONE`.

---

## Step 9 — Output

```
/review complete
──────────────────────────────────────────
Task document  : tasks/<ID>-<branch-slug>.md
Tests          : all passed
Version bump   : <layer A.B.C → A.B.D> | no bump
Branch pushed  : <branch-name>
PR             : <pr url>
Hotfix backport: <url> | not applicable
Status         : DONE
──────────────────────────────────────────
Task <TASK-NNN> is closed. Run /next to start the next task.
```
