-- Surge Simulator Schema

CREATE TABLE chaos_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    mode VARCHAR(50) NOT NULL,
    latency_ms INTEGER,
    failure_rate DOUBLE PRECISION,
    status VARCHAR(10) NOT NULL DEFAULT 'ACTIVE',
    triggered_by VARCHAR(100),
    started_at TIMESTAMP NOT NULL DEFAULT NOW(),
    stopped_at TIMESTAMP
);

CREATE INDEX idx_chaos_status ON chaos_sessions(status);
CREATE INDEX idx_chaos_started_at ON chaos_sessions(started_at DESC);
