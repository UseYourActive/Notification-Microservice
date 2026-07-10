# Deployment hardening (12-factor audit follow-up)

## Problem

The 12-factor audit surfaced four deployment-layer gaps:

1. `k8s-infra.yaml`'s Postgres `Deployment` has no volume at all (no
   `PersistentVolumeClaim`, no `emptyDir` even) — a reschedule, node drain, or
   `kubectl rollout restart` loses every row. Separately, the app `Deployment`
   (`src/main/kubernetes/kubernetes.yml`, merged into the Quarkus-generated
   manifest) sets no `terminationGracePeriodSeconds`, so it inherits Kubernetes'
   default of exactly **30s** — identical to, not greater than,
   `quarkus.shutdown.timeout=${SHUTDOWN_TIMEOUT:30s}`
   (`application.properties:103`). A rolling deploy sends SIGTERM, Quarkus starts
   its 30s drain, and the kubelet's SIGKILL can land at the same instant the
   drain would otherwise have finished cleanly — a race, not a guarantee.
2. `.github/workflows/build.yml` runs `mvnw verify` and `mvnw package
   -DskipTests` but never builds or publishes a container image. There is no
   release artifact — nothing a deploy step could actually pull.
3. Dev runs the JVM jar (`deploy-dev.ps1`); the closest thing to "prod" in this
   repo, `deploy-prod.ps1`, builds and runs the **GraalVM native** image via
   `docker-compose`. Neither CI job builds or runs the native artifact, so
   native-only bugs (reflection config gaps, class-initialization-at-runtime
   issues — see the `Troubleshooting` section of `README.md:545-549`, which
   already documents one) are only ever discovered by whoever happens to run
   `deploy-prod.ps1` locally, not by CI.
4. `quarkus.hibernate-orm.db-generation` (`application.properties:24`) is not a
   real Quarkus property (the actual key is
   `quarkus.hibernate-orm.database.generation`) — it's dead config, flagged as
   ignored at boot. Schema management is already fully handled by
   `quarkus.hibernate-orm.schema-management.strategy=validate` (line 28) plus
   Flyway.

## Goal / Non-goals

Goals:
- Data-loss risk on the Postgres pod is either closed (PVC) or explicitly
  scoped as an accepted risk with a documented reason, so nobody mistakes
  `k8s-infra.yaml` for a production-durable manifest.
- Rolling deploys of the app pod can never be SIGKILLed mid-drain under normal
  operation: pod grace period > Quarkus shutdown timeout, with margin.
- CI produces exactly one container image per master commit, published to
  GHCR, tagged so it can be traced back to both the commit and the app
  version — and that image is the thing that would be promoted/deployed, not
  rebuilt per target.
- PR builds still run the full test suite; only master pushes touch the
  registry.
- Native-mode regressions get a CI signal before they're the first thing a
  human discovers via `deploy-prod.ps1`.
- The dead `db-generation` key is removed with no behavior change (schema
  management already lives elsewhere).

Non-goals (explicitly out of scope for this mission):
- Standing up a real external/cloud Kubernetes cluster or a hosted Postgres —
  `k8s-infra.yaml` remains a local/dev-simulation manifest either way (see
  Design decision 1).
- An automated image-promotion pipeline (staging → prod gates, approvals) —
  this repo has no multi-environment deploy today; we only guarantee the image
  CI builds is promotable, not build the promotion mechanism itself.
- Semantic-version automation (the `pom.xml` version stays a static
  `1.0-SNAPSHOT`, as today) — image tags derived from it inherit that
  limitation; not fixed here.
- Rewriting `deploy-dev.ps1` / `deploy-prod.ps1` / `deploy-k8s.ps1` — they must
  keep working unchanged (explicit constraint).
- Running native builds on every PR — cost/feedback-loop tradeoff, see Design
  decision 3.

## Design

### Decision 1 — Postgres persistence: document as dev/demo-only, do NOT add a PVC/StatefulSet

**Chosen: mark `k8s-infra.yaml` explicitly as a local/dev-simulation manifest
(inline comment + README), leave it as a bare `Deployment`.**

Evidence this is the right scope, not a shortcut:
- This repo's actual "prod" path (`deploy-prod.ps1`) never touches Kubernetes
  at all — it builds the GraalVM native image and runs it via
  `docker-compose up -d --build`. `docker-compose.yaml` already gives Postgres
  a named volume (`postgres-data:/var/lib/postgresql/data`) — durable
  persistence for the one path this repo calls "prod" already exists,
  independently of `k8s-infra.yaml`.
- `k8s-infra.yaml` + `src/main/kubernetes/kubernetes.yml` back
  `deploy-k8s.ps1`, which README.md:305-318 labels **"Local Enterprise Sim"** —
  a demo of running the stack under Kubernetes locally (Docker Desktop),
  paired with `teardown-k8s.ps1`, which routinely runs
  `kubectl delete -f k8s-infra.yaml` as part of normal cleanup. The workflow
  already treats this data as disposable by design.
- A real fix (PVC at minimum, `StatefulSet` + headless `Service` +
  `volumeClaimTemplates` for correctness) is a legitimate amount of new
  surface — and on Docker Desktop's local cluster it would bind to
  local-path/hostpath storage, buying correctness for a target that isn't
  actually serving production traffic. It also raises real risk against the
  explicit constraint that local deploy scripts keep working unchanged:
  `Deployment`→`StatefulSet` changes pod identity/DNS semantics that
  `deploy-k8s.ps1`/`teardown-k8s.ps1` were never written against, and nothing
  in this mission's scope currently exercises that path in CI to catch a
  regression.
- Rejected alternative: add a PVC only (no `StatefulSet`). Technically
  possible on a single-replica `Deployment`, but it's a false sense of safety —
  a `Deployment`'s PVC (`ReadWriteOnce`) still gets deleted by
  `teardown-k8s.ps1`'s blanket `kubectl delete -f k8s-infra.yaml`, so it
  wouldn't actually survive the one operation this repo's own scripts perform
  routinely. Fixing that means also touching the teardown script to special-case
  the PVC — which is exactly the "local scripts must keep working unchanged"
  constraint pushing back.

Action: add a prominent comment block at the top of `k8s-infra.yaml` stating
it is dev/demo-only, no persistence, safe to delete/recreate; add a short
"Kubernetes stack is a local demo, not prod" note to `README.md`'s Kubernetes
section pointing at `docker-compose.yaml` as the actual durable-storage path.

### Decision 2 — terminationGracePeriodSeconds

Set `terminationGracePeriodSeconds: 45` on the app pod template in
`src/main/kubernetes/kubernetes.yml` (the existing Quarkus-merge overlay file —
same mechanism already used there to inject `envFrom`/`env`). 45s gives Quarkus's
30s `quarkus.shutdown.timeout` a 15s margin for pod-level teardown overhead
(container stop signal propagation, kubelet bookkeeping) so the kubelet's
SIGKILL can never race the drain under normal conditions. Both values
(`SHUTDOWN_TIMEOUT` env default and `terminationGracePeriodSeconds`) are
called out in a comment referencing each other so they don't drift apart
silently in the future.

### Decision 3 — GHCR image build/push (release stage)

Add a `release` job to `.github/workflows/build.yml`:
- `needs: test`, `if: github.event_name == 'push' && github.ref ==
  'refs/heads/master'` — verified default branch is `master` (GitHub API:
  `UseYourActive/Notification-Microservice` — repo is a rename of
  `UseYourActive/notification`, `default_branch: master`, `private: false`).
  PRs still run the existing test/package job only; they never touch GHCR.
- Builds the existing root `Dockerfile` (multi-stage JVM image) via
  `docker/build-push-action`, authenticated with `docker/login-action` using
  `secrets.GITHUB_TOKEN` (sufficient for GHCR under the same owner/repo — no
  new secret needed).
- Tags via `docker/metadata-action`: raw `{{sha}}` (short) and the Maven
  `project.version` (read once via `mvnw help:evaluate`, not hand-maintained),
  plus `latest` on master. Image ref: `ghcr.io/<owner>/<repo>` lowercased
  (GHCR requires lowercase; `docker/metadata-action` handles this
  automatically — `github.repository` alone is not guaranteed lowercase).
- Built once: this job builds a single image and pushes both tags against the
  same digest — it does not rebuild per tag or per environment. There is no
  per-environment build step anywhere in this mission; "promotion" of that
  same digest to any future environment is out of scope (see Non-goals).
- No secret values are echoed to logs; `GITHUB_TOKEN` is passed only as the
  `password:` input of `docker/login-action`, never printed.

### Decision 4 — Dev/prod parity (native build signal in CI)

Repo visibility check: **public**
(`UseYourActive/Notification-Microservice`, confirmed via the GitHub REST API)
→ GitHub-hosted Linux runner minutes are unlimited and free for public repos;
there is no minutes budget to protect.

Given that, recommend the stronger of the two options the audit offered: a
dedicated `native-build-test` job that runs `./mvnw verify -Pnative
-Dquarkus.native.container-build=true` (same invocation style as
`deploy-prod.ps1`, container-based so it doesn't need GraalVM installed on the
runner), gated to **master pushes only** (`if: github.event_name == 'push' &&
github.ref == 'refs/heads/master'`), not weekly and not on every PR:
- Weekly would leave up to 7 days of undetected native-only breakage between
  runs — a materially weaker signal than the audit's own framing of the risk.
- Every-PR would add ~10+ minutes to every PR's feedback loop; unnecessary
  given master-gating still catches regressions immediately after merge,
  before any promotion/release step would pick up that commit's image.
- Master-only is the standard middle ground and is free of charge here since
  minutes aren't budgeted on this repo.

Estimated cost: GraalVM native compilation for a Quarkus service this size
under `quarkus.native.container-build=true` typically runs **~8-12 minutes**
of job wall time on a standard 2-core Linux runner (dominated by the native-image
step itself, not by test execution). At roughly one push to master per unit of
work, this is a small, bounded addition — and, on a public repo, $0 against
Actions minutes regardless of frequency.

### Decision 5 — remove dead Hibernate key

Delete `quarkus.hibernate-orm.db-generation=${DB_GENERATION:none}`
(`application.properties:24`) — not a real Quarkus property in this version
(`quarkus.hibernate-orm.database.generation` is), and schema management is
already fully owned by `quarkus.hibernate-orm.schema-management.strategy=validate`
plus Flyway. Also remove the now-orphaned `DB_GENERATION=none` line from
`.env.example` (it would otherwise reference a property that no longer
exists) — same mechanical change, not scope creep.

## Failure modes

- **Rolling deploy under load**: with `terminationGracePeriodSeconds: 45` >
  `quarkus.shutdown.timeout=30s`, Quarkus always has its full drain window;
  the extra 15s absorbs pod-level teardown overhead so SIGKILL never arrives
  first under normal conditions.
- **`teardown-k8s.ps1` / cluster reschedule (Postgres)**: data loss remains
  possible and is now a documented, intentional property of the dev/demo
  manifest, not a silent gap — matches Decision 1.
- **GHCR push failure (e.g. transient registry error)**: `release` job fails
  independently of `test`; master's test/package status is unaffected, and no
  partial/mistagged image is left (build-push-action tags and pushes as one
  step).
- **Native build breakage caught post-merge**: the master-only job reports
  failure like any other CI job; it does not block the merge that already
  happened (out of scope — no branch-protection change is part of this
  mission), but surfaces the regression immediately rather than only at
  `deploy-prod.ps1` time.

## Decisions (resolved during review, before execution)

5. **`release` vs `native-build-test` dependency**: `release` depends only on
   `test`, never on `native-build-test`. Native is an informative signal, not
   a release gate — the artifact GHCR receives is the JVM image (root
   `Dockerfile`); the native binary is a separate artifact
   (`deploy-prod.ps1`'s concern) and gating the JVM image's release on a
   ~10-15 min build of a different artifact would only slow every master
   push's release for no correctness benefit to the thing being released.
   Both `release` and `native-build-test` run in parallel off the same
   `test`-gated master push.
6. **Pre- vs post-merge verification of the push stage**: verified
   post-merge only (see `tasks.md` T9). A live pre-merge dry run is possible
   in principle (temporarily widen the `if:` gate to this feature branch,
   push, observe via the public Actions/GHCR API, then revert the gate before
   merge) but was not attempted — it requires pushing to the shared remote
   mid-task and leaves a commit that exists only to be reverted. T9 is the
   explicit, tracked follow-up.

## Risks & open questions

- `docker/metadata-action`'s exact output tag list should be sanity-checked
  against the real Actions run during implementation (T3/T4) rather than
  assumed from documentation alone — first real GHCR push is the actual test,
  tracked as T9.
- The image tag derived from `project.version` will keep being literally
  `1.0-SNAPSHOT` on every push until this repo adopts real release versioning
  (non-goal here) — `latest` and the short-sha tag are the tags that actually
  differentiate builds today.
