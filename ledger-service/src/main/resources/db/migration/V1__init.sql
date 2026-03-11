-- Ledger Service Schema

CREATE TABLE accounts (
    user_id UUID PRIMARY KEY,
    cash_balance NUMERIC(18, 4) NOT NULL DEFAULT 0,
    cash_on_hold NUMERIC(18, 4) NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE positions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    ticker VARCHAR(10) NOT NULL,
    quantity NUMERIC(18, 8) NOT NULL DEFAULT 0,
    average_cost NUMERIC(18, 4),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_positions_user_ticker UNIQUE (user_id, ticker)
);

CREATE INDEX idx_positions_user_id ON positions(user_id);

CREATE TABLE ledger_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    order_id UUID,
    type VARCHAR(20) NOT NULL,
    amount NUMERIC(18, 4) NOT NULL,
    description TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_tx_user_id ON ledger_transactions(user_id);
CREATE INDEX idx_tx_order_id ON ledger_transactions(order_id);
CREATE INDEX idx_tx_created_at ON ledger_transactions(created_at DESC);

-- Seed trader1 account with $100,000 cash
-- UUID matches the one seeded in auth-service
INSERT INTO accounts (user_id, cash_balance, cash_on_hold) VALUES
    ('a1000000-0000-0000-0000-000000000001', 100000.0000, 0.0000);

INSERT INTO ledger_transactions (user_id, type, amount, description) VALUES
    ('a1000000-0000-0000-0000-000000000001', 'CREDIT', 100000.0000,
     'Initial funding - $100,000 starting balance');
