-- Saga Orchestrator Schema

CREATE TABLE sagas (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL UNIQUE,
    user_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'STARTED',
    current_step INTEGER NOT NULL DEFAULT 0,
    failure_reason TEXT,
    started_at TIMESTAMP NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMP
);

CREATE INDEX idx_saga_order_id ON sagas(order_id);
CREATE INDEX idx_saga_status ON sagas(status);
CREATE INDEX idx_saga_user_id ON sagas(user_id);

CREATE TABLE saga_steps (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    saga_id UUID NOT NULL REFERENCES sagas(id),
    step_number INTEGER NOT NULL,
    step_name VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    response_payload TEXT,
    error_message TEXT,
    executed_at TIMESTAMP
);

CREATE INDEX idx_saga_steps_saga_id ON saga_steps(saga_id);
