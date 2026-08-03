# /architect — Feasibility, Design & Task Document Creation

Analyze the task, assess the codebase, produce an implementation plan, and
create the task document in `tasks/`. The approved document is the contract
that /build will execute. Nothing is written to code during this skill.

---

## Step 0 — Determine mode

Check whether /next handed off an in-progress DRAFT task in this session.

**Continuing a DRAFT task:**
If a DRAFT task was surfaced by /next and the user chose to continue it:
- Read the existing task document from `tasks/TASK-NNN-<slug>.md`.
- Load the existing Task Description and partial Architect Review content.
- Skip Step 1 and go to Step 2.

**Starting fresh:**
If no in-progress task was handed off → proceed to Step 1.

---

## Step 1 — Receive the task description

Ask the user:
1. What needs to be built or fixed?
2. Which layer is primarily affected: frontend (Angular), backend (Quarkus), both, or infra?
3. Is this a feature, fix, hotfix, or chore?

Do not proceed until all three questions are answered.

---

## Step 2 — Codebase analysis

Read the relevant parts of the project:

**Backend (Quarkus)**
- `backend/src/main/java/` — existing entities, resources, services
- `backend/src/main/resources/application.properties`
- `backend/pom.xml` — current version and dependencies

**Frontend (Angular)**
- `frontend/src/app/` — components, services, routing
- `frontend/src/environments/`
- `frontend/package.json` — current version and dependencies

Identify:
- Files to create (exact paths)
- Files to modify (exact paths and what changes in each)
- New dependencies needed (Maven artifact IDs or npm package names with versions)
- API contracts affected (endpoints, DTOs, Angular service methods)
- Database changes (new entities, schema migrations)
- Breaking changes for other consumers

---

## Step 3 — Feasibility check

Flag any blockers before proceeding:
- Missing or incompatible dependency versions
- Conflicts with existing API contracts
- Schema migrations that risk data loss
- Security implications (auth, input validation, CORS)
- Tasks too large for one branch (suggest splitting)

If a hard blocker is found, state it and ask the user how to proceed before continuing.

---

## Step 4 — Determine version bump

| Impact                                           | Bump          |
|--------------------------------------------------|---------------|
| New endpoint or UI feature visible to users      | Minor (0.X.0) |
| Bug fix, internal refactor, config change        | Patch (0.0.X) |
| Breaking API change or major architectural shift | Major (X.0.0) |
| Docs, chore, no behaviour change                 | None          |

Only bump the layer(s) touched. Read the current version from `pom.xml` and
`package.json` to state exact before/after versions.

---

## Step 5 — Propose branch name

Derive a kebab-case short name (max 4 words) from the task description.

| Task type | Branch pattern         | Base branch |
|-----------|------------------------|-------------|
| feature   | `feature/<short-name>` | develop     |
| fix       | `fix/<short-name>`     | develop     |
| hotfix    | `hotfix/<short-name>`  | main        |
| chore     | `chore/<short-name>`   | develop     |

---

## Step 6 — Generate task ID

List all files in `tasks/` matching `TASK-*.md` (exclude `_template.md`).
Count the matches. New ID = count + 1, zero-padded to 3 digits.

- 0 existing files → `TASK-001`
- 5 existing files → `TASK-006`

Task document filename: `tasks/<ID>-<branch-slug>.md`
where `<branch-slug>` = branch name with `/` replaced by `-`.

Example: `feature/add-opa-policy` → `tasks/TASK-006-feature-add-opa-policy.md`

---

## Step 7 — Create the task document

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

Fill **Section 1 — Task Description**:
- **What:** one or two sentences on what will be built or changed.
- **Why:** the business or technical reason.
- **Layers affected:** list the layers.

Fill **Section 2 — Architect Review**:
- **Analysis date:** today's date.
- **Files to create:** complete list.
- **Files to modify:** complete list with what changes in each.
- **New dependencies:** with exact versions.
- **API / schema changes:** exact endpoints or table changes.
- **Version bump table:** all four cells filled.
- **Risks and notes:** any flags from Step 3, or "none".
- Leave **Decision** and **Decision rationale** blank.

Write the file to disk. Print:
```
Task document created: tasks/<ID>-<branch-slug>.md
```

---

## Step 8 — Present plan and request approval

```
──────────────────────────────────────────────────────────────
Task document : tasks/<ID>-<branch-slug>.md
Task          : <title>
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
  - <risk>
──────────────────────────────────────────────────────────────
Approve this plan and proceed to /build?
Reply: APPROVE / REJECT / adjust <what to change>
```

Wait for explicit user response. Do NOT proceed until a decision is given.

---

## Step 9 — Write the decision

**If APPROVED:**
1. Edit the task document:
   - Change `status: DRAFT` → `status: APPROVED` in the frontmatter.
   - In Section 2 fill: `### Decision` → `APPROVED` and `**Decision rationale:**` → one sentence.
2. Print:
   ```
   Status updated to APPROVED — tasks/<ID>-<branch-slug>.md
   Run /build to implement. Task: <TASK-NNN>
   ```

**If REJECTED:**
1. Ask the user for the rejection reason.
2. Edit the task document:
   - Change `status: DRAFT` → `status: REJECTED` in the frontmatter.
   - Fill `### Decision` → `REJECTED` and `**Decision rationale:**` → user's reason.
3. Print:
   ```
   Status updated to REJECTED — tasks/<ID>-<branch-slug>.md
   Task is closed. Run /next to start fresh or /architect to redesign.
   ```

**If adjust:**
- Update the relevant parts of the analysis.
- Re-write the affected sections of the task document.
- Return to Step 8 with the revised plan.
