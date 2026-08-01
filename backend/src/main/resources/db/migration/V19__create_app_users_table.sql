-- Authentication (Task 17): registered users who own events.
CREATE TABLE app_users (
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Email is normalized (trimmed + lowercased) before it's ever stored, so a
-- plain unique index (not a lower(email) functional index) is sufficient.
CREATE UNIQUE INDEX idx_app_users_email ON app_users (email);
