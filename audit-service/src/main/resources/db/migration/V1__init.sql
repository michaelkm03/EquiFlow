-- Audit Service Schema (append-only)

CREATE TABLE audit_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type VARCHAR(50) NOT NULL,
    order_id UUID,
    user_id UUID,
    source_service VARCHAR(50) NOT NULL,
    payload TEXT,
    kafka_topic VARCHAR(100),
    kafka_offset BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_order_id ON audit_events(order_id);
CREATE INDEX idx_audit_user_id ON audit_events(user_id);
CREATE INDEX idx_audit_event_type ON audit_events(event_type);
CREATE INDEX idx_audit_created_at ON audit_events(created_at DESC);
CREATE INDEX idx_audit_source ON audit_events(source_service);

-- Row-level security: INSERT only, deny UPDATE and DELETE
ALTER TABLE audit_events ENABLE ROW LEVEL SECURITY;

-- Policy: allow inserts for the equiflow role
CREATE POLICY audit_insert_only ON audit_events
    FOR INSERT TO equiflow
    WITH CHECK (true);

-- Policy: allow selects for the equiflow role
CREATE POLICY audit_select_only ON audit_events
    FOR SELECT TO equiflow
    USING (true);

-- No UPDATE or DELETE policies = denied by default with RLS enabled
