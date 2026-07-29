-- Event weather snapshots: one record per event (Task 5).
--
-- Weather measurement columns are nullable and left NULL (never a fabricated
-- zero) when status = 'FORECAST_UNAVAILABLE', since no real reading exists
-- for an event outside the provider's supported forecast horizon.
CREATE TABLE event_weather_snapshots (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL UNIQUE REFERENCES events(id) ON DELETE CASCADE,
    status VARCHAR(30) NOT NULL
        CHECK (status IN ('AVAILABLE', 'FORECAST_UNAVAILABLE')),
    resolved_location VARCHAR(300),
    latitude NUMERIC(9, 6),
    longitude NUMERIC(9, 6),
    temperature_at_start NUMERIC(5, 2),
    temperature_at_end NUMERIC(5, 2),
    precipitation_probability INTEGER,
    wind_speed NUMERIC(6, 2),
    condition VARCHAR(50),
    forecast_start TIMESTAMPTZ,
    forecast_end TIMESTAMPTZ,
    retrieved_at TIMESTAMPTZ NOT NULL,
    provider_name VARCHAR(50),
    message VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
