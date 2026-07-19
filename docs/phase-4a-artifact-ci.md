# Phase 4A — Trusted container artifacts

Phase 4A turns a successful `main` build into deployable, verifiable container artifacts. Deployment to Kubernetes is intentionally Phase 4B.

## Published images

| Component | GitHub Container Registry image |
|---|---|
| Web UI | `ghcr.io/skpandey15/java-ai-mcp-web-ui` |
| Interview orchestrator | `ghcr.io/skpandey15/java-ai-mcp-interview-orchestrator` |
| AI service | `ghcr.io/skpandey15/java-ai-mcp-ai-service` |

Every successful main-branch CI run publishes:

- `sha-<full-git-commit>`: immutable deployment tag
- `main`: convenience tag for inspection only

Kubernetes manifests must use the immutable tag or, preferably, the resolved image digest. Do not deploy the mutable `main` tag to UAT or production.

## Release gates

The `release-images` workflow runs only after `platform-ci` succeeds on `main`, or when manually dispatched by an authorized repository user. For each component it:

1. Checks out the exact CI-tested commit.
2. Builds the container image.
3. Fails on fixed high or critical vulnerabilities reported by Trivy.
4. Generates an SPDX JSON SBOM and retains it for 30 days.
5. Publishes the immutable and `main` tags to GHCR.
6. Signs the image with Cosign using GitHub's short-lived OIDC identity.
7. Publishes a GitHub build-provenance attestation for the image digest.

The workflow uses the repository-scoped `GITHUB_TOKEN`. It does not require a personal access token, OpenAI key, LiteLLM key, database password or Keycloak credential.

## Repository configuration

GitHub Actions must be enabled with workflow permissions sufficient for packages, OIDC and attestations. These permissions are declared narrowly in the workflow:

- `contents: read`
- `packages: write`
- `id-token: write`
- `attestations: write`

GHCR packages may initially be private. Phase 4B will either configure a Kubernetes `imagePullSecret` or document a deliberate public-package decision. Do not make packages public merely to avoid configuring cluster authentication.

## Verification

After the first successful main-branch release, open the repository Actions page and select `release-images`. Confirm all three matrix jobs pass and their SBOM artifacts exist.

Example local verification after installing Cosign:

```bash
cosign verify \
  --certificate-identity-regexp='https://github.com/Skpandey15/Java_AI_MCP/.github/workflows/release-images.yml@refs/heads/main' \
  --certificate-oidc-issuer='https://token.actions.githubusercontent.com' \
  ghcr.io/skpandey15/java-ai-mcp-interview-orchestrator@sha256:<digest>
```

GitHub's artifact attestation can also be verified with GitHub CLI:

```bash
gh attestation verify \
  oci://ghcr.io/skpandey15/java-ai-mcp-interview-orchestrator@sha256:<digest> \
  --repo Skpandey15/Java_AI_MCP
```

## Phase 4B handoff

Phase 4B consumes these images in Kubernetes. It will add environment overlays, secrets/configuration injection, probes, resource policies and local Rancher Desktop verification. No cluster deployment is performed by Phase 4A.
