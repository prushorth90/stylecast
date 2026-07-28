-- Manual event creation and upcoming-event listing (Task 2).
CREATE TABLE events (
    id UUID PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    location VARCHAR(300) NOT NULL,
    start_time TIMESTAMPTZ NOT NULL,
    end_time TIMESTAMPTZ NOT NULL,
    setting VARCHAR(10) NOT NULL CHECK (setting IN ('INDOOR', 'OUTDOOR')),
    dress_code VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT events_end_after_start CHECK (end_time > start_time)
);

CREATE INDEX idx_events_start_time ON events (start_time);
