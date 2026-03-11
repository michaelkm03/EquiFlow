-- Compliance Service Schema

CREATE TABLE compliance_checks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL,
    user_id UUID NOT NULL,
    ticker VARCHAR(10) NOT NULL,
    result VARCHAR(10) NOT NULL,
    violations TEXT,
    checked_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_compliance_order_id ON compliance_checks(order_id);
CREATE INDEX idx_compliance_user_id ON compliance_checks(user_id);
CREATE INDEX idx_compliance_result ON compliance_checks(result);

CREATE TABLE wash_sale_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    ticker VARCHAR(10) NOT NULL,
    order_id UUID NOT NULL,
    sale_date DATE NOT NULL,
    sale_price NUMERIC(18, 4) NOT NULL,
    cost_basis NUMERIC(18, 4),
    loss_amount NUMERIC(18, 4),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_wash_sale_user_ticker ON wash_sale_history(user_id, ticker);
CREATE INDEX idx_wash_sale_date ON wash_sale_history(sale_date);
