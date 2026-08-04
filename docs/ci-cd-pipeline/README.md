# CI/CD pipeline architecture

This platform uses GitHub Actions for continuous integration and release automation, GitHub Container Registry (GHCR) for image distribution, Git pull requests for environment promotion, and Argo CD for declarative delivery to Kubernetes. Application images move through the pipeline by immutable digest; environment overlays are the deployment source of truth.

```mermaid
flowchart TB
  developer[Developer] -->|push or pull request| github[(GitHub repository)]

  subgraph ci[Continuous integration and security]
    direction LR
    platformCI[platform-ci]
    appChecks[Application gates<br/>Web: test and build<br/>Java: test, coverage and bootJar<br/>Python: Ruff, tests, 95% coverage and AI quality]
    imageChecks[Container gates<br/>Build all images<br/>Compose validation<br/>Postgres and Redis smoke tests<br/>Service health checks]
    manifestChecks[Manifest gates<br/>Render local, dev, UAT and prod<br/>Kubeconform<br/>Kubernetes and GitOps policies]
    security[Trivy repository security<br/>Dependencies, secrets and config]
    codeql[CodeQL SAST<br/>Java, TypeScript, Python and Actions]
    sonar[SonarQube Cloud<br/>Quality, duplication and coverage<br/>when SONAR_TOKEN is configured]

    platformCI --> appChecks
    platformCI --> imageChecks
    platformCI --> manifestChecks
  end

  github -->|PR, main, or manual| platformCI
  github -->|PR, main, weekly, or manual| security
  github -->|PR, main, weekly, or manual| codeql
  github -->|PR, main, or manual| sonar

  platformCI -->|successful run on main| release[release-images]

  subgraph supply[Build and software supply chain]
    direction LR
    release --> matrix[Three-image matrix<br/>web-ui<br/>interview-orchestrator<br/>ai-service]
    matrix --> build[BuildKit build and cache]
    build --> trivyImage[Trivy HIGH and CRITICAL image gate]
    trivyImage --> sbom[SPDX SBOM artifact]
    sbom --> publish[Publish sha-&lt;commit&gt; and main tags]
    publish --> ghcr[(GitHub Container Registry)]
    ghcr --> digest[Resolve immutable digest]
    digest --> cosign[Cosign keyless signature via GitHub OIDC]
    digest --> provenance[Build provenance attestation<br/>for public repositories]
  end

  release -->|successful release| promote[promote-gitops]

  subgraph promotion[Progressive GitOps promotion]
    direction TB
    promote --> scope{Target environment}
    scope -->|automatic after release| devUpdate[Resolve digests and update dev overlay]
    scope -->|manual dispatch| uatGate[Verify dev is synced and healthy<br/>UAT environment approval]
    scope -->|manual dispatch| prodGate[Verify UAT is synced and healthy<br/>Production environment approval]
    uatGate --> uatUpdate[Verify requested release in dev<br/>Update UAT overlay]
    prodGate --> prodUpdate[Verify requested release in UAT<br/>Update production overlay]
    devUpdate --> validate[Render, schema-check and policy-check overlay]
    uatUpdate --> validate
    prodUpdate --> validate
    validate --> promotionPR[Promotion pull request]
    promotionPR -->|dev: auto-merge after checks<br/>UAT/prod: reviewed merge| desired[(main: desired state by digest)]
  end

  ghcr -->|pull by digest| clusters
  desired -->|watched by Argo CD| argocd

  subgraph delivery[Continuous delivery]
    direction TB
    argocd[Argo CD applications<br/>online-interview-dev, -uat and -prod]
    argocd -->|sync and self-heal| clusters[Kubernetes environments<br/>dev, UAT and production]
    clusters --> migration[Database migration Job<br/>runs Flyway before application readiness]
    migration --> workloads[web-ui, interview-orchestrator and ai-service]
    clusters --> platform[Platform dependencies<br/>PostgreSQL, Redis, Kafka, MinIO, Keycloak and LiteLLM]
    platform --> storage[(Persistent volumes)]
  end

  subgraph local[Local k3d development path]
    direction LR
    localSource[Local source checkout] --> deployScript[scripts/deploy-local.ps1]
    deployScript --> localBuild[Containerized builds]
    localBuild --> import[k3d image import]
    import --> localMigration[One-off Flyway Job when required]
    localMigration --> localRollout[kubectl set image and rollout]
    localRollout --> k3d[(k3d dev cluster)]
    k3d --> localPVC[(Existing PVCs remain intact)]
  end
```

## Pipeline behavior

1. Pull requests run application, container, manifest, repository-security, and static-analysis checks. SonarQube Cloud runs only when `SONAR_TOKEN` is configured.
2. A successful `platform-ci` run on `main` starts `release-images`. Digest-only GitOps commits are detected and skipped to prevent a release loop.
3. Each application image is vulnerability-scanned, accompanied by an SPDX SBOM, published to GHCR, resolved to an immutable digest, and signed with Cosign. Public repositories also receive GitHub build-provenance attestations.
4. A successful release automatically targets dev. UAT and production promotions are manually dispatched and require the preceding environment to be healthy in Argo CD.
5. Promotion changes only the target Kustomize overlay, validates the rendered manifests, and opens a pull request. Dev promotions enable automatic merge after required checks; UAT and production remain review- and environment-approval-gated.
6. Argo CD continuously reconciles merged desired state into each Kubernetes environment. The database migration Job advances the schema before application pods become ready.

## Sources of truth

| Concern | Repository location |
|---|---|
| CI tests and build gates | `.github/workflows/ci.yml` |
| Repository and configuration scanning | `.github/workflows/security.yml` |
| Static application security testing | `.github/workflows/codeql.yml` |
| Code quality analysis | `.github/workflows/sonarcloud.yml` |
| Image publishing, SBOMs, signing and attestations | `.github/workflows/release-images.yml` |
| Progressive environment promotion | `.github/workflows/promote-gitops.yml` |
| Kubernetes desired state | `platform/kubernetes/overlays/{dev,uat,prod}` |
| Argo CD applications and project | `platform/gitops/argocd` |
| Local k3d deployment | `scripts/deploy-local.ps1` |

The operational commands, migration procedure, rollback guidance, and local Argo CD setup are documented in [`docs/runbooks/deployment.md`](../runbooks/deployment.md).
