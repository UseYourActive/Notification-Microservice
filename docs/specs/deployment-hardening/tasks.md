# Tasks: deployment-hardening

- [ ] T1: Mark `k8s-infra.yaml` as dev/demo-only — files: `k8s-infra.yaml` —
      done when: a top-of-file comment block states this manifest has no
      persistence, is safe to delete/recreate (`teardown-k8s.ps1` already does
      so routinely), and that durable "prod" storage lives in
      `docker-compose.yaml`'s `postgres-data` volume instead.
- [ ] T2: Document the Kubernetes stack's scope in the README — files:
      `README.md` — done when: the "Kubernetes Cluster (Local Enterprise Sim)"
      section states explicitly that this is a local demo path with no data
      durability guarantee, and points at `deploy-prod.ps1`/`docker-compose`
      as the path that persists Postgres data.
- [ ] T3: Fix the app pod's termination grace period — files:
      `src/main/kubernetes/kubernetes.yml` — done when:
      `terminationGracePeriodSeconds: 45` is set on the pod template, with a
      comment cross-referencing `quarkus.shutdown.timeout=30s`
      (`application.properties:103`) so the two values aren't silently
      allowed to drift back into a race.
- [ ] T4: Split `build.yml`'s single job into `test` (unchanged behavior,
      runs on PRs and pushes) and gate everything else on it — files:
      `.github/workflows/build.yml` — done when: `mvnw verify` /
      `mvnw package -DskipTests` still run on every PR and every push to
      master, unchanged, under a job named `test`.
- [ ] T5: Add the GHCR `release` job — files: `.github/workflows/build.yml` —
      done when: a job gated on `github.event_name == 'push' && github.ref ==
      'refs/heads/master'` (`needs: test` — **only** `test`, deliberately not
      `native-build-test`, see dependency decision below) logs in to
      `ghcr.io` with `secrets.GITHUB_TOKEN`, builds the root `Dockerfile`, and
      pushes tags for short-sha, the Maven project version (read via `mvnw
      help:evaluate -Dexpression=project.version -q -DforceStdout`), and
      `latest` — with `permissions: packages: write` set at job level and no
      secret value appearing in any `run:` step's echoed output.
- [ ] T6: Add the master-only native build/test job — files:
      `.github/workflows/build.yml` — done when: a job gated the same way as
      T5, `needs: test` (JVM tests must pass before spending ~10 min on a
      native build), runs `./mvnw verify -Pnative
      -Dquarkus.native.container-build=true` and fails the workflow run on
      native-specific breakage, independent of and in parallel with
      `release` — **not** a dependency of `release` (see below).

      **Native/release dependency, decided:** `release` does not wait on
      `native-build-test`. JVM `test` is the sole release gate; native is an
      informative signal only. Reasoning: this repo's actual runtime artifact
      published to GHCR is the JVM image (root `Dockerfile`); gating the
      release on a ~10-15 min native build that doesn't produce the image
      being released would slow every master push's release by that much for
      a check that verifies a different artifact entirely (the native binary
      `deploy-prod.ps1` builds separately, not the GHCR image). Both jobs run
      off the same `test`-gated master push, in parallel, so master pushes get
      both signals with the release itself blocked only by what actually
      guards the released artifact's correctness.
- [ ] T7: Remove the dead Hibernate key — files:
      `src/main/resources/application.properties`, `.env.example` — done
      when: `quarkus.hibernate-orm.db-generation=...` is deleted from
      `application.properties` and the now-orphaned `DB_GENERATION=none` line
      is deleted from `.env.example`; `quarkus.hibernate-orm.schema-management.strategy=validate`
      (already present) remains the sole schema-management config; app still
      boots cleanly against the existing Flyway migrations.
- [ ] T8: Full local verification pass — files: none (validation only) —
      done when: `./mvnw verify` passes locally, the edited `build.yml` is
      valid YAML with `test`/`release`/`native-build-test` jobs correctly
      gated (spot-check via manual read-through — a real push to master isn't
      available from a feature branch, see T9), and the local deploy scripts
      (`deploy-dev.ps1` at minimum) still run unchanged.
- [ ] T9 (post-merge, cannot complete before merge): Confirm the release
      pipeline on real `master` — done when: after this branch merges, the
      Actions run triggered on `master` shows both `release` and
      `native-build-test` executed (not skipped), and the image is visible in
      GHCR (`ghcr.io/<owner>/<repo lowercased>`) tagged with the commit
      short-sha, the Maven project version, and `latest`.

      **Pre-merge confidence, decided:** not attempted in this execution.
      It's technically possible to get a live pre-merge signal — temporarily
      widen the `release`/`native-build-test` `if:` to also match this
      feature branch's ref, push, let Actions run once, confirm via the
      (public, unauthenticated-readable) GHCR/Actions API, then remove the
      temporary gate in a follow-up commit before merge. Not doing that here
      because it requires pushing this branch to the shared remote mid-task —
      a visible action outside anything approved so far — and it would leave
      a commit in this branch's history that exists solely to be reverted,
      working against the one-task-one-commit rule. The gating logic itself
      (`github.event_name == 'push' && github.ref == 'refs/heads/master'`) is
      standard, unambiguous Actions syntax verified by read-through, not a
      novel construct — the residual risk this defers to T9 is narrow (e.g. a
      typo'd secret name or Dockerfile path), not the gating logic itself.
