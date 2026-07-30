# Phase 6C security and tenant isolation

## Identity boundary

All non-local Keycloak users must have a `tenant_id` user attribute before using
tenant-sensitive APIs. The web client maps that attribute into access, ID, and
userinfo tokens. Local and dev-local realms emit `tenant_id=default` so existing
developer accounts continue to work.

Tenant identifiers must match `[a-z0-9][a-z0-9_-]{0,79}`. Candidate registration
persists the token tenant, candidate discovery is tenant-scoped, and assignment
returns `404` for a candidate outside the interviewer's tenant to avoid disclosing
cross-tenant identities. Existing records migrate to the `default` tenant.

Before promoting a user in dev, UAT, or production:

1. Set the Keycloak `tenant_id` user attribute.
2. Confirm a new access token contains the expected `tenant_id`.
3. Register the application profile.
4. Verify an interviewer only sees candidates from the same tenant.

## Kubernetes boundary

The namespace enforces Pod Security `baseline` and audits/warns against
`restricted`. Network policies default-deny ingress and egress, then permit DNS,
public access to the web/auth frontends, declared application service flows, and
model-provider egress from LiteLLM.

The CNI must enforce Kubernetes NetworkPolicy. Validate connectivity after rollout:

- web UI to orchestrator and Keycloak;
- orchestrator to PostgreSQL, Keycloak, AI service, and configured Kafka;
- AI service to LiteLLM;
- LiteLLM to the configured model provider.

Kafka egress is restricted to TCP port `9092`; narrow its current `ipBlock` to the
broker CIDR in each managed environment when that address range is known.

## Supply-chain gates

`.github/workflows/security.yml` scans dependencies, IaC, and secrets with Trivy on
every pull request, main-branch push, and weekly schedule. Pull requests also run
GitHub dependency review and fail on newly introduced high or critical findings.
