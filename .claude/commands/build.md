# /build — Gated Implementation & Test

Execute the approved architect plan. Requires the task document to have
`status: APPROVED` — any other status is a hard block.

---

## Step 0 — Locate the task document

Check the conversation for a file path of the form `tasks/TASK-NNN-*.md`
(printed by /architect at the end of Step 9).

If found → read that file.

If not found:
- List all `tasks/TASK-*.md` files (exclude `_template.md`).
- Read each file's frontmatter and collect those with `status: APPROVED`.
- If exactly one → use it. Print its path.
- If multiple → list them and ask the user which to work on. Wait for answer.
- If zero → stop:
  ```
  [HARD BLOCK] No task document with status APPROVED found in tasks/.
  Run /architect first and have the user approve the plan.
  ```

---

## Step 1 — Gate check (HARD BLOCK)

Read the task document. Parse the YAML frontmatter and extract `status:`.

If `status` is NOT exactly `APPROVED`:
```
[HARD BLOCK] Cannot run /build.
Task document : <file path>
Current status: <actual status>
Required status: APPROVED

  DRAFT        → run /architect and approve the plan first
  REJECTED     → task is closed; run /architect to redesign
  BUILD_COMPLETE → /build already ran; run /review
  DONE         → task is complete; run /next for a new task
```
Stop. Do not execute any further step.

---

## Step 2 — Load plan and confirm

Read from the task document:
- `branch:` and `base_branch:` from frontmatter
- `id:` and `title:` from frontmatter
- Files to create and modify (Section 2)
- New dependencies (Section 2)
- Version bump table (Section 2)

Print a one-paragraph summary of what will be implemented.

Ask: "Proceed with implementation on branch `<branch>`? [yes / no]"
Wait for explicit confirmation. If no → stop.

---

## Step 3 — Create the branch

```bash
git checkout <base_branch>
git pull origin <base_branch>
git checkout -b <branch>
```

Verify each command succeeded. If branch creation fails → stop and show the error.

---

## Step 4 — Implement

Follow the architect plan exactly — files to create and files to modify in
the order listed in Section 2.

Rules:
- Do not add features beyond what the plan specifies.
- Do not refactor code outside the task scope.
- Only add comments for non-obvious WHY — never for what the code does.
- Keep changes minimal and targeted.

After each Java file is written or modified, verify compilation before continuing:
```bash
cd backend && ./mvnw compile -q
```

---

## Step 5 — Backend tests (if backend was touched)

```bash
cd backend
./mvnw compile
./mvnw test
```

If compilation fails → stop. Show the error. Fix before continuing.
If any test fails → stop. Show the failing output. Fix before continuing.

Do NOT proceed to Step 6 until backend tests pass.
Record result: PASSED or SKIPPED (not touched).

---

## Step 6 — Frontend tests (if frontend was touched)

```bash
cd frontend
npm install
ng build
ng test --watch=false --browsers=ChromeHeadless
```

If build fails → stop. Show the error. Fix before continuing.
If any test fails → stop. Show the failing output. Fix before continuing.

Do NOT proceed to Step 7 until frontend tests pass.
Record result: PASSED or SKIPPED (not touched).

---

## Step 7 — Commit

Stage only the files listed in the architect plan — never `git add .`:

```bash
git add <file1> <file2> ...
git status   # verify staged files match plan exactly
git commit -m "<type>(<scope>): <short description>"
```

Commit message types: `feat`, `fix`, `hotfix`, `chore`, `docs`
Commit message scopes: `backend`, `frontend`, `api`
Example: `feat(backend): add OPA policy evaluation endpoint`

Capture the commit SHA:
```bash
git rev-parse HEAD
```

---

## Step 8 — Update the task document

Edit the task document. Fill in **Section 3 — Build Result**:

| Field             | Value                                              |
|-------------------|----------------------------------------------------|
| Build date        | Today (YYYY-MM-DD)                                 |
| Branch created    | The branch name from frontmatter                   |
| Files changed     | Exact list of staged files from Step 7             |
| Backend test row  | PASSED / FAILED / SKIPPED                          |
| Frontend test row | PASSED / FAILED / SKIPPED                          |
| Commit SHA        | Full SHA from Step 7                               |
| Build notes       | Any deviation from the architect plan, or "none"   |
| Build status      | `BUILD_COMPLETE`                                   |

Update frontmatter: `status: APPROVED` → `status: BUILD_COMPLETE`.

---

## Step 9 — Output

```
/build complete
──────────────────────────────────────────
Task document  : tasks/<ID>-<branch-slug>.md
Branch         : <branch-name>
Files changed  : <count> files
Backend tests  : PASSED / SKIPPED
Frontend tests : PASSED / SKIPPED
Commit SHA     : <sha>
Status         : BUILD_COMPLETE
──────────────────────────────────────────
Run /review to push, open the PR, and close the task.
```
