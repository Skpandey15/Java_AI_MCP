ALTER TABLE user_profile
    ADD COLUMN tenant_id VARCHAR(80) NOT NULL DEFAULT 'default';

ALTER TABLE user_profile
    ALTER COLUMN tenant_id DROP DEFAULT;

DROP INDEX IF EXISTS idx_user_profile_role;
CREATE INDEX idx_user_profile_tenant_role
    ON user_profile (tenant_id, role, status, display_name);

CREATE INDEX idx_user_profile_tenant_subject
    ON user_profile (tenant_id, identity_subject);
