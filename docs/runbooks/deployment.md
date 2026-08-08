# Deployment Runbook

**Purpose.** Everything you need to build, ship, and deploy this platform **without help** —
including the exact local commands to roll a code change onto the k3d dev cluster when you
cannot delegate the work (e.g. AI assistant tokens exhausted). It also reviews how the
automated CI/CD pipeline is wired so you know what happens on a `git push`.

Companion docs: [phase-4a-artifact-ci](../phase-4a-artifact-ci.md) (CI), [phase-4b-kubernetes](../phase-4b-kubernetes.md)
(manifests / local cluster), [phase-4c-gitops](../phase-4c-gitops.md) (GitOps promotion),
[production-hardening](../production-hardening.md).

---

## 1. Pipeline at a glance

Delivery runs as a **single GitHub Actions pipeline** (`pipeline.yml`): on every push to `main`
it runs **build/test → publish images → promote to dev** as **one run** with a visual job graph
and live per-step logs. Manual **uat/prod** promotion is a separate workflow (`promote-gitops.yml`).
Delivery is **GitOps**: nothing is `kubectl apply`-ed to shared clusters directly — instead the
desired image **digests** are written into `platform/kubernetes/overlays/<env>/kustomization.yaml`,
and **Argo CD** syncs each cluster to match Git.

```
 git push (main) ─▶ pipeline.yml ── one run, live logs ─────────────────────┐
   ┌───────────────────────────────────────────────────────────────────┐   │
   │ CI:  web-ui · interview-orchestrator · ai-service ·                │   │
   │      docker-images · kubernetes-manifests    (also the only jobs   │   │
   │        │ (all pass)                            that run on a PR)    │   │
   │        ▼                                                           │   │
   │ detect ─▶ publish ──────────────▶ promote-dev                      │   │
   │ (skip     (per image: Trivy gate, (open dev digest PR ─────────────┼───┤ PR
   │  digest-   SBOM, push sha-<sha>    + auto-merge)                    │   │ merges
   │  only)     + main, Cosign, attest)                                 │   ▼
   └───────────────────────────────────────────────────────────────────┘  Argo CD
                                                                            syncs dev
 manual uat/prod: promote-gitops.yml (dispatch) → health-gated PR (review + Env approval)
 security.yml: Trivy fs (vuln+secret) + config scan; on push/PR + weekly cron
```

| Workflow | File | Triggers | What it does |
|---|---|---|---|
| **pipeline** | `.github/workflows/pipeline.yml` | push `main`, **every PR**, manual | **CI** on push+PR: `web-ui` (npm test+build), `interview-orchestrator` (gradle `test jacocoTestCoverageVerification bootJar`, JDK 25), `ai-service` (uv + ruff + pytest ≥95% + AI-quality gate), `docker-images` (build all three + live health smoke), `kubernetes-manifests` (render overlays + kubeconform + policy). Then on `main` only: `detect` (skip digest-only commits) → `publish` (per image: **Trivy HIGH/CRITICAL gate**, SBOM, push `sha-<sha>`+`main`, **Cosign sign**, attest) → `promote-dev` (open dev digest PR + **auto-merge**). One run, live logs. |
| **promote-gitops** | `.github/workflows/promote-gitops.yml` | manual dispatch → **uat/prod** | Resolves immutable digests, waits for the source env to be **healthy in Argo CD** and to already run the requested release, writes digests into the target overlay, renders+validates, opens a **promotion PR** (your review + the `*-promotion` GitHub Environment approval, then merge). |
| **security** | `.github/workflows/security.yml` | push `main`, PR, weekly cron, manual | Trivy filesystem scan (vuln + secret) and infra-config scan; fails on HIGH/CRITICAL. |

**Key properties**
- **Immutable digests** are what get deployed to uat/prod (never the mutable `main` tag).
- **Migrations are gated**: the `database-migration` Job is the *only* workload allowed to run
  Flyway; app pods run with `SPRING_FLYWAY_ENABLED=false` and an init container that blocks
  readiness until the schema is migrated. See §4.
- **Promotion is progressive**: dev (auto) → uat (manual, health-gated on dev) → prod (manual,
  health-gated on uat).

---

## 2. Trigger a **cloud** deployment manually

Requires the [`gh` CLI](https://cli.github.com/) authenticated to `Skpandey15/Java_AI_MCP`,
or use the GitHub UI (**Actions → pick workflow → Run workflow**).

### 2a. Run the full pipeline on `main`
```bash
gh workflow run pipeline.yml --repo Skpandey15/Java_AI_MCP --ref main
```
Runs build/test → publish → `promote-dev` as **one run**; the dev digest PR auto-merges → Argo CD
deploys dev. Watch the whole thing (steps + live logs) in **Actions → pipeline → the run**.

### 2b. From a branch / PR (CI only)
```bash
gh workflow run pipeline.yml --repo Skpandey15/Java_AI_MCP --ref <branch>
```
On a branch or PR only the CI jobs run; `publish`/`promote-dev` run on `main` only — merge to
`main` to release.

### 2c. Promote an existing release to uat or prod
```bash
# source_sha = the 40-char commit whose sha-<sha> images already exist in GHCR
gh workflow run promote-gitops.yml --repo Skpandey15/Java_AI_MCP --ref main \
  -f target_environment=uat \
  -f source_sha=<full-40-char-sha>
```
- uat promotion requires **dev** to be healthy in Argo CD; prod requires **uat** healthy.
- It opens a PR and pauses on the `uat-promotion` / `prod-promotion` Environment approval —
  approve in **GitHub → Settings → Environments** (or the PR checks), then merge. Argo CD syncs.

### 2d. Watch it
```bash
gh run list  --repo Skpandey15/Java_AI_MCP --limit 8
gh run watch --repo Skpandey15/Java_AI_MCP <run-id>
```

---

## 3. Deploy to the **local k3d cluster** (the hands-on path)

Use this to put a local code change onto the running dev cluster yourself. This is the path to
follow when you can't delegate it.

**Facts about this environment**
- k3d cluster: **`dev`** · namespace: **`online-interview`** · app URL: **http://interview.localhost:8081**
- Deployment name == container name for all three (`web-ui`, `interview-orchestrator`, `ai-service`),
  so `kubectl set image deploy/<x> <x>=<image>` works.
- ⚠️ **Local Gradle is broken on this machine** (jacoco/JDK mismatch) — the orchestrator jar
  **must** be built inside a container. All build commands below are container-based and need
  only Docker + `k3d` + `kubectl`.
- Commands use the Git-Bash form (`MSYS_NO_PATHCONV=1`, `pwd -W`). Run them from the repo root
  unless a `cd` is shown. Pick a unique `TAG` per deploy (e.g. `fix-login`, `r7`) so the
  rollout actually restarts.

### 3.0 One-command shortcut

`scripts/deploy-local.ps1` automates all of §3a–§3c (build → k3d import → rollout), including the
§4 migration Job:

```powershell
./scripts/deploy-local.ps1 -Service ai-service            # one service
./scripts/deploy-local.ps1 -Service interview-orchestrator -Migrate   # + run a new migration first
./scripts/deploy-local.ps1 -Service all -Tag r7           # all three at a fixed tag
```

For automated local CI/CD, run the change-aware wrapper. It executes the affected service's
quality gates, delegates deployment to `deploy-local.ps1`, detects Flyway changes, preserves
the original PostgreSQL replica count, and verifies readiness without deleting PVCs:

```powershell
./scripts/local-ci-cd.ps1                    # one run for application changes vs HEAD
./scripts/local-ci-cd.ps1 -Watch             # continuous change detection + CI + deploy
./scripts/local-ci-cd.ps1 -Watch -Service all -RunInitial
```

Generated build, dependency, cache, and virtual-environment directories are excluded from watch
snapshots so a deployment does not trigger itself. Press `Ctrl+C` to stop watch mode.

The manual steps below are the same thing spelled out — useful for one-offs or debugging.

### 3a. ai-service (Python) — no build tricks needed
```bash
cd apps/ai-service
docker build -t online-interview-ai-service:$TAG .
k3d image import online-interview-ai-service:$TAG -c dev
kubectl -n online-interview set image deploy/ai-service ai-service=online-interview-ai-service:$TAG
kubectl -n online-interview rollout status deploy/ai-service --timeout=120s
```

### 3b. web-ui (React) — no build tricks needed
```bash
cd apps/web-ui
docker build -t online-interview-web-ui:$TAG .
k3d image import online-interview-web-ui:$TAG -c dev
kubectl -n online-interview set image deploy/web-ui web-ui=online-interview-web-ui:$TAG
kubectl -n online-interview rollout status deploy/web-ui --timeout=120s
```

### 3c. interview-orchestrator (Java) — build jar in a container, then wrap it
```bash
cd apps/interview-orchestrator
# 1) build the bootJar inside the official Gradle+JDK25 image (local gradle is broken).
#    'gradle_cache' is a reusable Docker volume so deps aren't re-downloaded each time.
MSYS_NO_PATHCONV=1 docker run --rm \
  -v "$(pwd -W):/app" -v gradle_cache:/home/gradle/.gradle -w /app \
  gradle:9.6.1-jdk25 gradle bootJar --no-daemon

# 2) containerize the prebuilt jar (Dockerfile.local = temurin:25-jre + COPY jar)
docker build -f Dockerfile.local -t online-interview-orchestrator:$TAG .
k3d image import online-interview-orchestrator:$TAG -c dev

# 3) *** IF this change added a Flyway migration, run §4 NOW, before the rollout ***

# 4) roll it out
kubectl -n online-interview set image deploy/interview-orchestrator \
  interview-orchestrator=online-interview-orchestrator:$TAG
kubectl -n online-interview rollout status deploy/interview-orchestrator --timeout=150s
```

> A new migration = you added a file under
> `apps/interview-orchestrator/src/main/resources/db/migration/V<NN>__*.sql`.
> If you skip §4 the new pod will hang in **Init** (its init container waits forever for a schema
> that was never migrated). See §6.

---

## 4. Run a database migration (local)

Migrations do **not** run at app startup here — a dedicated `database-migration` Job runs Flyway
with the orchestrator image. When you deploy the orchestrator imperatively with `set image`
(§3c) that Job does **not** re-run, so you must run it yourself **with the new image** before the
app rollout. This one-off Job avoids touching secrets/config.

Save this as `migration.yaml` (edit the image tag to match your `$TAG`):

```yaml
apiVersion: batch/v1
kind: Job
metadata:
  name: database-migration-adhoc
  namespace: online-interview
spec:
  backoffLimit: 1
  activeDeadlineSeconds: 300
  ttlSecondsAfterFinished: 600
  template:
    metadata:
      labels:
        app.kubernetes.io/name: database-migration   # REQUIRED: NetworkPolicy allows only this label to reach Postgres
    spec:
      restartPolicy: Never
      serviceAccountName: platform-runtime
      initContainers:
        - name: wait-for-postgres
          image: pgvector/pgvector:0.8.5-pg17-bookworm
          imagePullPolicy: IfNotPresent
          command: [sh, -c, "until pg_isready -h postgres -U interview; do sleep 2; done"]
      containers:
        - name: migrate
          image: online-interview-orchestrator:REPLACE_WITH_YOUR_TAG   # <-- edit
          imagePullPolicy: Never                                       # local image already imported to k3d
          args: [--spring.main.web-application-type=none, --spring.flyway.enabled=true]
          envFrom:
            - configMapRef: {name: platform-config}
          env:
            - {name: DATABASE_PASSWORD, valueFrom: {secretKeyRef: {name: platform-secrets, key: DATABASE_PASSWORD}}}
            - {name: AI_SERVICE_TOKEN,  valueFrom: {secretKeyRef: {name: platform-secrets, key: AI_SERVICE_TOKEN}}}
            - {name: SPRING_FLYWAY_ENABLED, value: "true"}
            - {name: OBJECT_STORAGE_ENABLED, value: "false"}
            - {name: MESSAGING_ENABLED, value: "false"}
          securityContext:
            allowPrivilegeEscalation: false
            capabilities: {drop: [ALL]}
```

```bash
kubectl apply -f migration.yaml
kubectl -n online-interview wait --for=condition=complete job/database-migration-adhoc --timeout=180s
kubectl -n online-interview logs job/database-migration-adhoc | grep -i "Successfully applied\|now at version"
kubectl -n online-interview delete job database-migration-adhoc   # cleanup so the name is free next time
```
A `restricted:latest` PodSecurity **warning** is printed but the Job still runs — it's only an
audit warning, not an enforced denial.

**Verify the schema moved:**
```bash
kubectl -n online-interview exec postgres-0 -- \
  psql -U interview -d online_interview -tc \
  "SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1;"
```

### Alternative: full kustomize apply (runs the migration Job for you)
The base overlay already contains the migration Job, so `apply -k` recreates and runs it. The
catch: the local overlay's `secretGenerator` sets `OPENAI_API_KEY=replace-before-apply`, so
`apply -k` will **overwrite your real key** — you must restore it afterward. Prefer the one-off
Job above for iterative work; use this for a clean full redeploy:
```bash
# build & import ALL three images at tag 'phase4b-local' first (the tag the local overlay pins)
kubectl -n online-interview delete job database-migration --ignore-not-found
kubectl apply -k platform/kubernetes/overlays/local
# RESTORE the real OpenAI key (apply -k just clobbered it):
kubectl -n online-interview patch secret platform-secrets --type merge \
  -p "{\"stringData\":{\"OPENAI_API_KEY\":\"$OPENAI_API_KEY\"}}"
kubectl -n online-interview wait --for=condition=complete job/database-migration --timeout=5m
kubectl -n online-interview rollout restart deploy/interview-orchestrator deploy/ai-service deploy/web-ui
```

---

## 5. Verify & roll back

```bash
# health
kubectl -n online-interview get pods
kubectl -n online-interview exec deploy/interview-orchestrator -c interview-orchestrator -- \
  wget -qO- http://127.0.0.1:8080/actuator/health; echo
# then open http://interview.localhost:8081 and click through the changed flow

# roll back one revision (per deployment)
kubectl -n online-interview rollout undo deploy/interview-orchestrator
kubectl -n online-interview rollout status deploy/interview-orchestrator
```
For shared envs, roll back by reverting the promotion commit on `main`; Argo CD self-heals to
the previous digests. **Never** roll a database migration backward without a compensating
forward migration.

---

## 6. Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| Orchestrator pod stuck **Init** (`validate-migrated-schema`) | New migration not applied; init container waits for the schema | Run §4 with the deployed image, then the pod becomes Ready |
| `ErrImageNeverPull` / `ImagePullBackOff` on a local deploy | Image not imported into k3d, or tag typo | `k3d image import <image>:<tag> -c dev`; confirm the exact tag in `set image` |
| Rollout "successful" but code unchanged | Reused the same tag — Deployment saw no spec change | Use a **new** unique `$TAG` each deploy (or `kubectl rollout restart deploy/<x>`) |
| Migration Job can't reach Postgres / times out | Pod missing the `app.kubernetes.io/name: database-migration` label | That label is required — the default-deny NetworkPolicy only lets it egress to Postgres |
| `Failed to fetch` in the browser | A backend pod is down/not Ready | Check `kubectl get pods`; the UI now shows a friendly "service unavailable" message for this |
| AI calls fail after `apply -k overlays/local` | `apply -k` reset `OPENAI_API_KEY` to the placeholder | Restore it (see §4 alternative); prefer the one-off migration Job to avoid this |
| `PodSecurity "restricted:latest"` warning | Audit-level warning only | Ignore — the namespace enforces the looser *baseline* level; workloads still run |
| Argo CD shows the app **Degraded** after a machine/cluster restart (pods still Running); `SecretStore platform-secret-store` = `InvalidProviderConfig` — *"unable to log in with Kubernetes auth"* | Dev-mode Vault is **in-memory**; the restart wiped its Kubernetes auth method, policy, role, and KV data, so External Secrets can no longer log in | **Self-heals within ~2 min** via the `vault/vault-autoreseed` CronJob (§7a). To force recovery immediately: `kubectl -n vault create job vault-reseed-now --from=cronjob/vault-autoreseed`. If the recovery seed itself is missing (e.g. the `vault` namespace was recreated), re-run `scripts/setup-local-vault.ps1` |

---

## 7. Argo CD — the deploy UI and push-to-deploy (local)

Argo CD gives you the "Blue Ocean"-style visualization for **delivery** (app health, the live
resource tree, sync/diff), and is what makes *push-to-`main` → cluster updates* real. It is
installed in the local `dev` cluster (namespace `argocd`).

**Access.**
- UI: **http://argocd.localhost:8081** (Traefik ingress; served over plain HTTP because
  `argocd-cmd-params-cm` sets `server.insecure: true`).
- User `admin`; get the initial password:
  ```bash
  kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath='{.data.password}' | base64 -d; echo
  ```
  (Change it in **User Info → Update Password**, then delete that secret.)

**What's registered.** An Application `online-interview-dev` (project `online-interview`) that
syncs `platform/kubernetes/overlays/dev-local` from GitHub `main` into a **separate**
`online-interview-dev` namespace — so it never touches the stack you run in `online-interview`.
It is registered with **manual sync** locally (the repo's `application-dev-local.yaml` enables
auto-sync; manual avoids auto-spinning a full second stack that could exhaust an 8 GB node).

**Three things you must do (they need YOUR credentials — an assistant must not enter tokens):**
1. **Connect the private repo** so Argo CD can read the manifests (until then the app shows
   `sync=Unknown`, *"authentication required"*): UI → **Settings → Repositories → Connect Repo**
   → HTTPS → `https://github.com/Skpandey15/Java_AI_MCP.git` → username + a GitHub PAT with
   `repo` read.
2. **Add the image pull secret** so the private GHCR images can be pulled into the new namespace:
   ```bash
   kubectl create namespace online-interview-dev --dry-run=client -o yaml | kubectl apply -f -
   kubectl -n online-interview-dev create secret docker-registry ghcr-pull \
     --docker-server=ghcr.io --docker-username=Skpandey15 --docker-password="$GHCR_TOKEN"
   ```
3. **Publish images**: merge the CVE fix (and any change) to `main`; once `release-images` is
   green and `promote-gitops` updates the `dev` overlay digests, the manifests Argo CD reads
   will point at pullable images.

**Then deploy:** in the UI click **Sync** (or `kubectl -n argocd patch app online-interview-dev
--type merge -p '{"operation":{"sync":{}}}'`). To make it fully automatic (true push-to-deploy),
re-enable auto-sync once you're happy with the resource load:
```bash
kubectl -n argocd patch app online-interview-dev --type merge \
  -p '{"spec":{"syncPolicy":{"automated":{"prune":true,"selfHeal":true}}}}'
```

> ⚠️ **Resource note.** Syncing spins up a full parallel stack (Keycloak, Kafka, Postgres, MinIO,
> LiteLLM, all three apps) in `online-interview-dev` — roughly 3–4 GB on top of your running
> stack. On an 8 GB node, watch for memory pressure; scale replicas down or stop the local stack
> first if needed.

**Gotcha.** Re-running the upstream `install.yaml` with `--server-side` resets
`argocd-cmd-params-cm`, so re-apply `platform/gitops/local-access/argocd-cmd-params.yaml` and
`kubectl -n argocd rollout restart deploy/argocd-server` afterward, or the UI will bounce to
HTTPS and 404 through the HTTP ingress.

**Teardown** (frees all Argo CD memory): `kubectl delete namespace argocd online-interview-dev`.

---

## 7a. Vault secrets are ephemeral — the self-healing reseed

The local Vault runs in **dev mode** (`server.dev.enabled: true`), which forces **in-memory
storage** and ignores any persistent volume. That keeps local setup simple (auto-unsealed, one
root token) but means **every Vault or cluster/laptop restart wipes Vault completely** — KV data,
the Kubernetes auth method, the policy, and the role. External Secrets can then no longer log in
(`SecretStore … InvalidProviderConfig`), the generated `platform-secrets` Secret goes stale, and
Argo CD marks the app **Degraded**. The pods keep running on the last-generated Secret, so it's a
broken *secret pipeline*, not an outage.

> Leaving dev mode to get persistent storage is **worse** for local dev: a real storage backend
> makes Vault come back **sealed** on every restart, requiring unseal-key management. So we keep
> dev mode and make the *reseed* automatic instead.

**How it self-heals.** `scripts/setup-local-vault.ps1` installs, alongside Vault:
- **Secret `vault/vault-seed`** — the recovery seed: the dev root token + `payload.json` (the full
  application secret). It lives in etcd, which **survives** Vault restarts.
- **ConfigMap `vault/vault-bootstrap`** — the idempotent reconcile script
  (`platform/vault/local/autoreseed/reseed.sh`) + the Vault policy.
- **CronJob `vault/vault-autoreseed`** (every 3 min) — re-enables KV v2 + Kubernetes auth, rewrites
  the policy/role/auth-config, and re-puts the secret **only if Vault is missing it** (so a live
  Vault edit is never clobbered). It talks to Vault over the network and never touches the Vault
  pod spec, so it cannot crash-loop Vault.

After a restart, recovery is **hands-off in ~2 min** (one CronJob tick + External Secrets
re-validation), then Argo CD returns to Healthy on its own. Verified by deleting `vault-0` and
watching the app recover with no manual action.

**Operate it:**
```bash
# force an immediate reconcile instead of waiting for the next 3-min tick
kubectl -n vault create job vault-reseed-now --from=cronjob/vault-autoreseed
kubectl -n vault logs job/vault-reseed-now

# rotate a secret value: update Vault, then let the CronJob leave it alone (it only seeds when absent)
#   the seed in vault-seed is the RECOVERY baseline — refresh it by re-running setup-local-vault.ps1
```

If the `vault` namespace itself was deleted (seed gone), re-run `scripts/setup-local-vault.ps1` —
it rebuilds Vault, the seed, and the CronJob from live cluster state.

---

## 7b. "Deployed successfully" Slack notifications

Argo CD's notifications controller sends a Slack message when `online-interview-dev` finishes
syncing and is Healthy (`:white_check_mark: deployed successfully`) and a warning when it goes
Degraded. The templates, triggers, and the app subscription are committed:
- `platform/gitops/local-access/argocd-notifications-cm.yaml` — Slack service, templates, triggers
- `platform/gitops/local-access/application-dev-local.yaml` — `subscribe.on-deployed.slack` /
  `subscribe.on-health-degraded.slack` annotations (channel: `deployments`)

Everything is wired **except the Slack bot token**, which must be created by a human (an assistant
must not create Slack apps or enter tokens). One-time setup:

1. **Create a Slack app** → <https://api.slack.com/apps> → *Create New App* → *From scratch* →
   pick your workspace.
2. **Add a bot scope** → *OAuth & Permissions* → *Scopes → Bot Token Scopes* → add `chat:write`.
3. **Install** → *Install to Workspace* → copy the *Bot User OAuth Token* (`xoxb-…`).
4. **Create/choose a channel** (e.g. `#deployments`) and invite the bot: in that channel type
   `/invite @YourAppName`. (The channel must match the annotation value `deployments`.)
5. **Store the token** in the notifications secret (do this yourself — never paste tokens into an
   assistant):
   ```bash
   kubectl -n argocd patch secret argocd-notifications-secret \
     --type merge -p '{"stringData":{"slack-token":"xoxb-REPLACE-ME"}}'
   kubectl -n argocd rollout restart deploy/argocd-notifications-controller
   ```

**Test without waiting for a deploy:**
```bash
kubectl -n argocd exec deploy/argocd-notifications-controller -- \
  /app/argocd-notifications trigger run on-deployed online-interview-dev
# or send a template straight to the channel:
kubectl -n argocd exec deploy/argocd-notifications-controller -- \
  /app/argocd-notifications template notify app-deployed online-interview-dev --recipient slack:deployments
```
Until the token is set, the controller logs a send error on each event (harmless). To change the
channel, edit the two `subscribe.*.slack` annotations (repo file + `kubectl annotate`).

---

## 7c. Operating the Adaptive AI Interviewer

The Adaptive AI Interviewer (an agent that conducts a live, adaptive interview) is gated by a
single flag, **`ADAPTIVE_ENABLED`** (default `false`), which is also the kill-switch. Design and
build detail: `docs/design/adaptive-ai-interviewer.md`.

**Rollout state.** Dark-launched in **dev only** — `overlays/dev/kustomization.yaml` sets
`ADAPTIVE_ENABLED=true`. **uat and prod stay off** until the pre-GA checklist below is signed off.

**How it runs.** An interview created in **ADAPTIVE** question mode routes the candidate to
`/candidate/adaptive/:assignmentId`. The orchestrator (`AdaptiveSessionService`) runs a durable
turn loop: broker the blueprint + reuse-checked bank questions → call the ai-service agent
(`/internal/v1/interview:next-turn`) → persist the turn. On conclude it **submits the session**
into the normal interviewer review queue. Everything is bounded (`ADAPTIVE_MAX_TURNS`,
`ADAPTIVE_TOKEN_BUDGET`) and all model traffic goes through the LiteLLM scoped key.

**Kill-switch.** Set `ADAPTIVE_ENABLED=false` (env in the overlay) and let Argo CD re-sync — the
candidate endpoints return 404 and the mode disappears. No redeploy of logic needed.

**Monitor during the pilot.**
- **Cost / tokens**: the agent's usage flows through LiteLLM — watch cost per interview in the
  LiteLLM/Grafana dashboards; a runaway loop would show as high tokens/interview (bounded by the
  turn + token budgets, so this should stay flat).
- **Session outcomes**: adaptive sessions land in the interviewer submissions queue like any other.

**Known gap (pre-GA).** Adaptive answers are stored as `adaptive_turn` rows, not `interview_answer`
rows, so the **standard review page does not yet show the adaptive transcript** — the interviewer
transcript/approval view is a pending follow-up. Until it ships, treat the dev flag as a
candidate-experience pilot only.

**Pre-GA checklist (before enabling in uat/prod):**
1. Interviewer transcript + approval view (reads `adaptive_turn`; approves the proposed evaluation).
2. Fairness review — adaptive scoring is comparable/explainable vs the fixed modes (blueprint +
   audit trail make each path auditable).
3. Per-interview cost + latency within budget on the dev pilot.
4. Then promote with `ADAPTIVE_ENABLED=true` added to `overlays/uat` → `overlays/prod`.

---

## 8. Code scanning — CodeQL & SonarCloud

Two static-analysis layers on top of Trivy (dependencies/config) and ruff (basic Python lint),
both unlocked by the repo being public.

### CodeQL (`.github/workflows/codeql.yml`) — active, zero setup
GitHub-native SAST on our own source (Java, TypeScript, Python) plus the workflow files. Runs on
push/PR to `main` and weekly; findings land in the repo's **Security → Code scanning** tab. No
account, token, or server. To widen it, change `queries: security-extended` to
`security-and-quality` (adds maintainability findings).

### SonarCloud (`.github/workflows/sonarcloud.yml` + `sonar-project.properties`) — needs onboarding
Adds the maintainability / duplication / tech-debt dashboard and a "clean as you code" quality
gate with PR decoration, plus coverage import (JaCoCo + pytest). The workflow is a **green no-op
until `SONAR_TOKEN` is set**, so it cannot break CI. To activate:

1. Sign in at **https://sonarcloud.io** with GitHub and **import** `Skpandey15/Java_AI_MCP`
   (free for public repos). Note the **organization** and **project key** it creates.
2. If they differ from the guesses in `sonar-project.properties`
   (`organization=skpandey15`, `projectKey=Skpandey15_Java_AI_MCP`), update that file.
3. In SonarCloud project settings, **turn off Automatic Analysis** (we drive it from CI so Java
   binaries + coverage are included), generate a token, and add it as the repo secret
   **`SONAR_TOKEN`** (GitHub → Settings → Secrets and variables → Actions).

Next push then runs the real analysis. If the `SonarSource/sonarqube-scan-action` version needs
bumping for your account, that's the one line to adjust — CI stays green regardless because of
the token gate.

---

## 9. Pipeline review — findings (2026-08)

**Strengths.** Coverage + AI-quality gates block merges; images are vulnerability-scanned,
SBOM'd, Cosign-signed and deployed by immutable digest; migrations are strictly gated and
ordered ahead of app readiness; promotion is progressive and health-gated between environments;
manifests are schema- and policy-validated in CI before they can ship. This is a genuinely
production-grade supply chain.

**Gaps / follow-ups to consider.**
1. **No post-deploy smoke against the live environment.** CI smoke-tests images in isolation, but
   nothing verifies the app *after* Argo CD syncs an env. Add an Argo CD `PostSync` health/smoke
   hook (or a probe job) per environment.
2. **No automated rollback trigger.** Rollback is manual (revert the promotion commit). Consider
   Argo Rollouts (canary/analysis) for prod so a failed health check auto-aborts the rollout.
3. **`main` tag is mutable and also pushed.** uat/prod correctly use digests, but the floating
   `main` tag invites accidental use in a manifest — keep it for dev convenience only and lint
   against digest-less image refs in uat/prod overlays (partly covered by validate-gitops).
4. **Local iterative deploys bypass the migration Job.** Solved: `scripts/deploy-local.ps1`
   (§3.0) runs the migration Job automatically when passed `-Migrate`.
5. **Private-repo provenance attestation is skipped** (GitHub limitation, already noted in the
   workflow). Digest + Cosign signature remain authoritative; revisit if the repo goes public.
