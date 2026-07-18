CREATE TABLE user_profile (
    id UUID PRIMARY KEY,
    identity_subject VARCHAR(255) NOT NULL UNIQUE,
    email VARCHAR(320) NOT NULL UNIQUE,
    display_name VARCHAR(200) NOT NULL,
    role VARCHAR(30) NOT NULL CHECK (role IN ('CANDIDATE', 'INTERVIEWER')),
    status VARCHAR(30) NOT NULL CHECK (status IN ('ACTIVE', 'SUSPENDED')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_user_profile_role ON user_profile (role);
