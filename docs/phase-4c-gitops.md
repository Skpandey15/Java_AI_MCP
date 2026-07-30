# Phase 4C — GitOps deployment

Phase 4C makes Git the deployment control plane for dev, UAT and production.
Argo CD reads the Kustomize overlays from `main`; CI never applies application
manifests directly to a cluster.

## Delivery model

| Environment | Promotion trigger | Argo CD synchronization |
| --- | --- | --- |
| dev | successful `release-images` workflow | automatic, with prune and self-heal |
| UAT | manual `promote-gitops` dispatch after dev is healthy | explicit operator sync |
| production | manual `promote-gitops` dispatch after UAT is healthy | explicit operator sync |

Every promotion resolves the three `sha-<commit>` image tags in GHCR to registry
digests and commits only the digests to the target overlay. UAT and production
never consume a mutable image tag.

## Repository layout

- `platform/gitops/argocd`: the `AppProject` and three declarative `Application` resources
- `platform/gitops/update-image-digests.ps1`: the supported mechanical overlay updater
- `platform/gitops/validate-gitops.ps1`: reconciliation, manual-gate and digest policy checks
- `.github/workflows/promote-gitops.yml`: health-gated promotion pull requests

The Argo CD resources use the in-cluster destination by default. For separate
clusters, register each cluster in Argo CD and replace the matching
`spec.destination.server` while retaining the namespace and AppProject boundary.

## Bootstrap

Install a supported Argo CD release in the `argocd` namespace, register this
repository, and apply the declarative bootstrap:

```powershell
kubectl apply -k platform/gitops/argocd
kubectl get applications -n argocd
```

For a private repository, configure an Argo CD repository credential Secret
outside this repository. Never commit a GitHub token or SSH private key.

The applications track `main`. Development automatically reconciles changes.
UAT and production have `automated.enabled: false`, so an approved digest change
becomes `OutOfSync` until an operator reviews the Argo CD diff and starts sync.

## GitHub configuration

Create these GitHub Environments:

- `dev-promotion`: no required reviewer
- `uat-promotion`: required reviewer and prevention of self-review
- `prod-promotion`: required reviewer, prevention of self-review and no admin bypass

Configure these secrets:

- `GITOPS_BOT_TOKEN`: a fine-grained token or GitHub App token with repository
  contents and pull-request write access. A separate credential is required
  because events created by the default workflow token do not start the normal
  pull-request CI chain.
- `ARGOCD_SERVER`: Argo CD API hostname available to the runner
- `ARGOCD_AUTH_TOKEN`: read-only token permitted to inspect application health

Enable repository auto-merge and require `platform-ci` for `main`. Dev promotion
pull requests request auto-merge and merge only after required checks pass.
UAT and production promotion pull requests remain manual.

## Promotion

Dev promotion starts automatically after the trusted-image workflow succeeds.

For UAT or production, run `promote-gitops` and provide:

- `target_environment`: `uat` or `prod`
- `source_sha`: the full 40-character commit SHA used to build the images

The workflow:

1. waits for the source Argo CD application to be `Synced` and `Healthy`;
2. resolves all three GHCR manifests to immutable digests;
3. updates only the selected Kustomize overlay;
4. renders and schema-validates the promoted manifests;
5. opens a promotion pull request.

After merging a UAT or production promotion, inspect the Argo CD diff and sync:

```powershell
argocd app diff online-interview-uat
argocd app sync online-interview-uat
argocd app wait online-interview-uat --sync --health --timeout 600
```

Use `online-interview-prod` for production.

## Deployment ordering and health gates

Argo CD sync waves enforce this order:

1. namespace;
2. generated configuration, Services, ServiceAccount and ExternalSecret;
3. PostgreSQL;
4. Flyway migration hook;
5. application and identity Deployments.

The migration Job is a `Sync` hook and is recreated for a new synchronization.
Application Deployments retain the Phase 4B migrated-schema init-container gate,
readiness probes and rollout health checks.

## Rollback

Git is authoritative. The preferred rollback is a pull request restoring the
last known-good digest triplet from the target overlay's history:

```powershell
git log -- platform/kubernetes/overlays/prod/kustomization.yaml
git show <known-good-commit>:platform/kubernetes/overlays/prod/kustomization.yaml
```

Restore all three application digests together, merge, review the Argo CD diff,
and synchronize. Database migrations must remain backward-compatible; an image
rollback does not reverse Flyway migrations.

Emergency Argo CD history rollback is allowed only for UAT and production,
where automated sync is disabled:

```powershell
argocd app history online-interview-prod
argocd app rollback online-interview-prod <history-id>
argocd app wait online-interview-prod --sync --health --timeout 600
```

Immediately follow an emergency rollback with a Git pull request restoring the
same digests, otherwise Git and the cluster will disagree.

## Acceptance checks

```powershell
$output = Join-Path $env:TEMP online-interview-phase4c
New-Item -ItemType Directory -Force $output | Out-Null
foreach ($environment in 'local','dev','uat','prod') {
  kubectl kustomize "platform/kubernetes/overlays/$environment" |
    Set-Content "$output/$environment.yaml"
}
kubectl kustomize platform/gitops/argocd | Set-Content "$output/argocd.yaml"
./platform/kubernetes/validate-manifests.ps1 -RenderedDirectory $output
./platform/gitops/validate-gitops.ps1 -RenderedDirectory $output -GitOpsDirectory platform/gitops/argocd
```
