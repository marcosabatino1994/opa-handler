---
id: TASK-000
title: ""
type: feature
status: DRAFT
branch: ""
created: ""
base_branch: develop
---

# TASK-000 — <Title>

> This document is the single source of truth for one task.
> Created by /architect, gated through /build, and closed by /review.
> Do not edit the `status` field manually — skills manage it.

---

## 1. Task Description

> Written by /architect when this document is created.

**What:**

**Why:**

**Layers affected:** <!-- backend / frontend / both / infra -->

---

## 2. Architect Review

> Written by /architect. The `status` field must be APPROVED before /build can run.

**Analysis date:**

### Files to create

<!-- list each file path, one per line -->

### Files to modify

<!-- list each file path and what changes, one per line -->

### New dependencies

<!-- Maven or npm, or "none" -->

### API / schema changes

<!-- endpoints, DTOs, DB migrations affected, or "none" -->

### Version bump

| Layer    | Current | Proposed | Reason |
|----------|---------|----------|--------|
| Backend  |         |          |        |
| Frontend |         |          |        |

### Risks and notes

<!-- blockers, migration steps, security considerations, or "none" -->

### Decision

<!-- APPROVED | REJECTED — written by /architect after user confirms -->

**Decision rationale:**

---

## 3. Build Result

> Written by /build after implementation is complete.
> LOCKED — /build will not fill this section unless status == APPROVED.

**Build date:**

**Branch created:**

### Files changed

<!-- list exact paths changed -->

### Test results

| Suite         | Result                              |
|---------------|-------------------------------------|
| Backend (mvn) | <!-- PASSED / FAILED / SKIPPED -->  |
| Frontend (ng) | <!-- PASSED / FAILED / SKIPPED -->  |

**Commit SHA:**

**Build notes:**

<!-- any deviation from the architect plan, or "none" -->

### Build status

<!-- BUILD_COMPLETE | BUILD_FAILED — written by /build -->

---

## 4. Review Result

> Written by /review after the PR is opened and all checks pass.
> LOCKED — /review will not fill this section unless status == BUILD_COMPLETE.

**Review date:**

### Re-run test results

| Suite         | Result                              |
|---------------|-------------------------------------|
| Backend (mvn) | <!-- PASSED / FAILED / SKIPPED -->  |
| Frontend (ng) | <!-- PASSED / FAILED / SKIPPED -->  |

### Version bump applied

| Layer    | Before | After |
|----------|--------|-------|
| Backend  |        |       |
| Frontend |        |       |

**PR URL:**

**Hotfix backport PR:** <!-- URL or "not applicable" -->

**Deployment notes:**

<!-- env vars, migration commands, or "none" -->

### Final status

<!-- DONE | REVIEW_FAILED — written by /review -->
