-- Settlement Service Schema

CREATE TABLE settlements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL UNIQUE,
    user_id UUID NOT NULL,
    ticker VARCHAR(10) NOT NULL,
    side VARCHAR(4) NOT NULL,
    quantity NUMERIC(18, 8) NOT NULL,
    fill_price NUMERIC(18, 4) NOT NULL,
    total_amount NUMERIC(18, 4) NOT NULL,
    trade_date DATE NOT NULL,
    settlement_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING_SETTLEMENT',
    settled_at TIMESTAMP,
    failure_reason TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_settlement_order_id ON settlements(order_id);
CREATE INDEX idx_settlement_status ON settlements(status);
CREATE INDEX idx_settlement_date ON settlements(settlement_date);
CREATE INDEX idx_settlement_user_id ON settlements(user_id);
