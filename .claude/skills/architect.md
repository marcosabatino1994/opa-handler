# /architect — Feasibility, Design & Task Document Creation

The user provides a short description of what they want to do.
/architect does the rest: it checks the workspace, reads the codebase,
fills the full task document, and only asks the user when something is
genuinely ambiguous. Nothing is written to code during this skill.

---

## Step 0 — Workspace check (runs /next logic internally)

Before any design work, verify the workspace is ready.

### 0a — Scan for in-progress tasks

List all `tasks/TASK-*.md` files (exclude `_template.md`).
Read each file's frontmatter and collect files where status is NOT `DONE`
and NOT `REJECTED`.

If in-progress tasks exist:
- Print them in a table (ID, title, status, next skill).
- Ask the user:
  > In-progress tasks exist. Continue one of them, or start a new task?
  > [1] Continue task <ID> — <title>  (next: /build or /review)
  > [2] Start a new task
- If option 1 → hand off to the appropriate skill (/build or /review) and stop.
- If option 2 → continue.

### 0b — Check local workspace state

Run `git status` and `git stash list`.

If uncommitted changes or stash entries exist:
- List the modified files and stash entries.
- Ask:
  > [A] Continue these in-progress changes
  > [B] Stash and park them
  > [C] Discard (WARNING: irreversible)
- Do NOT proceed until the workspace is clean.

### 0c — Sync with remote

```bash
git fetch origin
git log HEAD..origin/develop --oneline
```

If commits exist ahead on `origin/develop`:
- List them (short SHA + message).
- Recommend rebasing. Ask: rebase now, or start from current HEAD?
- Wait for the user's answer.

Once the workspace is clean and synced → continue.

---

## Step 1 — Receive task intent

Ask the user for one thing only:
> What do you want to do on this project?

Accept a short natural-language description. Do NOT ask a multi-question
interview at this stage — gather what you need from the codebase in Step 2,
and only ask follow-up questions in Step 3 if genuinely needed.

---

## Step 2 — Codebase analysis

Read the project to fill in as much of the task document as possible without
asking the user. Actively derive answers rather than asking for them.

**Backend (Quarkus) — read if the task touches backend or is unclear:**
- `backend/src/main/java/` — existing entities, resources, services
- `backend/src/main/resources/application.properties`
- `backend/pom.xml` — current version and existing dependencies

**Frontend (Angular) — read if the task touches frontend or is unclear:**
- `frontend/src/app/` — components, services, routing modules
- `frontend/src/environments/`
- `frontend/package.json` — current version and existing dependencies

From the codebase and the task description, derive:
- Which layer(s) are affected (backend / frontend / both / infra)
- Task type (feature / fix / hotfix / chore)
- Exact files to create (with full paths)
- Exact files to modify (with full paths and what changes in each)
- New dependencies needed (Maven artifact IDs or npm packages with versions)
- API contracts affected (endpoints, DTOs, Angular service methods)
- Database changes (new entities, schema migrations)
- Breaking changes
- Current version numbers from pom.xml and/or package.json
- Version bump type and new version numbers
- Proposed branch name (kebab-case, max 4 words, from task description)

**Branch naming:**
| Type    | Pattern                | Base       |
|---------|------------------------|------------|
| feature | `feature/<short-name>` | develop    |
| fix     | `fix/<short-name>`     | develop    |
| hotfix  | `hotfix/<short-name>`  | main       |
| chore   | `chore/<short-name>`   | develop    |

**Version bump:**
| Impact                                           | Bump          |
|--------------------------------------------------|---------------|
| New endpoint or UI feature visible to users      | Minor (0.X.0) |
| Bug fix, internal refactor, config change        | Patch (0.0.X) |
| Breaking API change or major architectural shift | Major (X.0.0) |
| Docs, chore, no behaviour change                 | None          |

---

## Step 3 — Clarifying questions (conditional — only if genuinely needed)

After reading the codebase, check if there are points you cannot resolve
from the code or the task description alone.

Reasons to ask a question:
- Two valid architectural approaches exist and the choice has real trade-offs
  (e.g. new entity vs. extending an existing one).
- A requirement is missing and cannot be inferred (e.g. which user role
  should access a new endpoint).
- A destructive or irreversible change is implied and you need explicit
  confirmation (e.g. dropping a column).

Reasons NOT to ask a question:
- You can read the answer from the codebase.
- The question is about something already standard in the project.
- You are unsure about implementation detail — make the standard choice and
  note it in the plan.

If there are genuine unknowns, ask ALL of them in one message (never
interrupt the user multiple times). Wait for answers, then continue.

If there are no genuine unknowns → skip this step entirely.

---

## Step 4 — Feasibility check

Flag any blockers before writing the document:
- Missing or incompatible dependency versions
- Conflicts with existing API contracts
- Schema migrations that risk data loss
- Security implications (auth, input validation, CORS)
- Task too large for one branch (suggest splitting)

If a hard blocker is found, state it and ask the user how to proceed before continuing.

---

## Step 5 — Generate task ID

List all `tasks/TASK-*.md` files (exclude `_template.md`). Count them.
New ID = count + 1, zero-padded to 3 digits.

Task document filename: `tasks/<ID>-<branch-slug>.md`
where `<branch-slug>` = branch name with `/` replaced by `-`.

Example: `feature/add-opa-policy` → `tasks/TASK-006-feature-add-opa-policy.md`

---

## Step 6 — Create the task document

Copy the schema from `tasks/_template.md`.

Fill in the YAML frontmatter:
```yaml
id: <TASK-NNN>
title: "<concise one-line task title>"
type: <feature|fix|hotfix|chore>
status: DRAFT
branch: "<branch-name>"
created: "<YYYY-MM-DD>"
base_branch: <develop|main>
```

Fill **Section 1 — Task Description** completely:
- **What:** one or two sentences on what will be built or changed.
- **Why:** the business or technical reason (infer from context if not stated).
- **Layers affected:** list the layers.

Fill **Section 2 — Architect Review** completely:
- **Analysis date:** today's date.
- **Files to create:** complete list with full paths.
- **Files to modify:** complete list with full paths and what changes in each.
- **New dependencies:** with exact versions, or "none".
- **API / schema changes:** exact endpoints or table changes, or "none".
- **Version bump table:** all four cells filled (current and proposed for each layer).
- **Risks and notes:** any flags from Step 4, or "none".
- Leave **Decision** and **Decision rationale** blank.

Write the file to disk. Print:
```
Task document created: tasks/<ID>-<branch-slug>.md
```

---

## Step 7 — Present plan and request approval

```
──────────────────────────────────────────────────────────────
Task document : tasks/<ID>-<branch-slug>.md
Task          : <title>
Type          : <feature|fix|hotfix|chore>
Branch        : <branch-name>  (base: <base_branch>)
Layers        : <backend / frontend / both>
Version bump  : backend <A.B.C → A.B.D> | frontend <X.Y.Z → X.Y+1.0> | none
──────────────────────────────────────────────────────────────
Files to create:
  - <path>
Files to modify:
  - <path> — <what changes>
New dependencies:
  - <dep>
Risks:
  - <risk or "none">
──────────────────────────────────────────────────────────────
Approve this plan to proceed to /build?
Reply: APPROVE / REJECT / adjust <what to change>
```

Wait for explicit user response. Do NOT proceed until a decision is given.

---

## Step 8 — Write the decision

**If APPROVED:**
1. Edit the task document:
   - Change `status: DRAFT` → `status: APPROVED` in frontmatter.
   - Fill `### Decision` → `APPROVED`
   - Fill `**Decision rationale:**` → one sentence confirming the plan.
2. Print:
   ```
   Status updated to APPROVED — tasks/<ID>-<branch-slug>.md
   Run /build to implement. Task: <TASK-NNN>
   ```

**If REJECTED:**
1. Ask the user for the rejection reason.
2. Edit the task document:
   - Change `status: DRAFT` → `status: REJECTED` in frontmatter.
   - Fill `### Decision` → `REJECTED`
   - Fill `**Decision rationale:**` → the user's rejection reason.
3. Print:
   ```
   Status updated to REJECTED — tasks/<ID>-<branch-slug>.md
   Task is closed. Run /next to start fresh or /architect to redesign.
   ```

**If adjust:**
- Update the relevant parts of the analysis.
- Re-write the affected sections of the task document.
- Return to Step 7 with the revised plan.
