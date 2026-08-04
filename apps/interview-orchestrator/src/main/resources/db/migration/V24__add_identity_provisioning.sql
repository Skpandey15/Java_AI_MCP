CREATE TABLE identity_provisioning (
    id UUID PRIMARY KEY,
    idempotency_key VARCHAR(100) NOT NULL UNIQUE,
    tenant_id VARCHAR(80) NOT NULL,
    email VARCHAR(320) NOT NULL,
    display_name VARCHAR(200) NOT NULL,
    requested_role VARCHAR(30) NOT NULL CHECK (requested_role IN ('INTERVIEWER')),
    identity_subject VARCHAR(255),
    status VARCHAR(30) NOT NULL CHECK (status IN ('PENDING', 'IDENTITY_READY', 'ACTIVE', 'FAILED')),
    failure_code VARCHAR(80),
    failure_message VARCHAR(500),
    attempt_count INTEGER NOT NULL DEFAULT 0,
    requested_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    UNIQUE (tenant_id, email)
);

CREATE INDEX idx_identity_provisioning_status_updated
    ON identity_provisioning (status, updated_at);

CREATE TABLE identity_provisioning_audit (
    id UUID PRIMARY KEY,
    provisioning_id UUID NOT NULL REFERENCES identity_provisioning(id),
    event_type VARCHAR(50) NOT NULL,
    actor_subject VARCHAR(255) NOT NULL,
    detail VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_identity_provisioning_audit_provisioning
    ON identity_provisioning_audit (provisioning_id, created_at);
