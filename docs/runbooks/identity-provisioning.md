# Identity provisioning and authorization operations

## Security boundaries

Keycloak owns credentials, sessions, immutable identity subjects and token roles. PostgreSQL
owns tenant-scoped application profiles and provisioning state. Candidate self-registration
requires the `candidate` role, rejects identities that also carry `interviewer`, and requires
an `email_verified=true` token outside the explicitly relaxed local developer overlay.

The resource server validates signature, issuer, expiry and the `online-interview-web`
audience. Realm imports add that audience to access tokens. Access tokens expire after five
minutes and refresh-token reuse is disabled.

## Interviewer provisioning

Only a token with `platform_admin` may call `POST /api/v1/admin/interviewers`. Supply an
`Idempotency-Key`, tenant, email and display name. The orchestrator records a durable operation,
uses the restricted `interview-provisioner` Keycloak service account, validates tenant
ownership, assigns the interviewer role, atomically activates the PostgreSQL profile, and
writes audit records. Retrying with the same key resumes a failed operation; reusing it for a
different request returns `409`.

Configure the confidential Keycloak client with service accounts enabled. Grant only the
minimum realm-management client roles needed to query/create/update users and map realm roles.
Inject its rotated secret as `KEYCLOAK_ADMIN_CLIENT_SECRET`. Never configure the application
with bootstrap-administrator credentials.

## Reconciliation and monitoring

Production enables a five-minute reconciliation sweep. It verifies active identity subject,
email, tenant attribute and interviewer role, repairs safe drift, and refuses subject changes.
Monitor:

- `identity_provisioning_success_total`
- `identity_provisioning_failure_total`
- `identity_reconciliation_repaired_total`
- `identity_reconciliation_failed_total`

Alert on any sustained provisioning failures and on every reconciliation failure. Review
`identity_provisioning_audit` during identity incidents. Cross-tenant and subject conflicts
require manual investigation and must never be repaired automatically.

## Production checklist

1. Configure SMTP and verify candidate email delivery.
2. Require MFA for `platform_admin` and interviewer accounts in Keycloak.
3. Restrict administrator login by network/device policy where available.
4. Rotate the provisioner secret and revoke old sessions on role removal.
5. Retain Keycloak login/admin events and provisioning audits according to policy.
6. Exercise failed-Keycloak, failed-database and duplicate-request recovery in UAT.

## Gmail development SMTP

The `dev-local` overlay sends through `smtp.gmail.com:587` with STARTTLS as
`sunil4.java.ai.expert@gmail.com`. Run `scripts/configure-gmail-smtp-secret.ps1` to store the
Google App Password as `MAIL_PASSWORD` and `KEYCLOAK_SMTP_PASSWORD` over kubectl stdin. The
password is never committed or printed. Keycloak realm import substitutes the latter environment
variable during a clean rebuild. Rotate an exposed or revoked App Password in Google and rerun
the script, then restart Keycloak and the orchestrator.
