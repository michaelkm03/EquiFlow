-- Order Service Schema

CREATE TYPE order_side AS ENUM ('BUY', 'SELL');
CREATE TYPE order_type AS ENUM ('MARKET', 'LIMIT');
CREATE TYPE order_status AS ENUM (
    'PENDING', 'COMPLIANCE_CHECK', 'OPEN', 'FILLED',
    'PARTIALLY_FILLED', 'CANCELLED', 'REJECTED', 'FAILED'
);

CREATE TABLE orders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    ticker VARCHAR(10) NOT NULL,
    side VARCHAR(4) NOT NULL,
    type VARCHAR(10) NOT NULL,
    quantity NUMERIC(18, 8) NOT NULL,
    limit_price NUMERIC(18, 4),
    filled_price NUMERIC(18, 4),
    filled_qty NUMERIC(18, 8),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    saga_id UUID,
    rejection_reason TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_orders_user_id ON orders(user_id);
CREATE INDEX idx_orders_ticker ON orders(ticker);
CREATE INDEX idx_orders_status ON orders(status);
CREATE INDEX idx_orders_saga_id ON orders(saga_id);
CREATE INDEX idx_orders_created_at ON orders(created_at DESC);

CREATE TABLE order_book_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL REFERENCES orders(id),
    ticker VARCHAR(10) NOT NULL,
    side VARCHAR(4) NOT NULL,
    price NUMERIC(18, 4) NOT NULL,
    remaining_qty NUMERIC(18, 8) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_book_ticker_side ON order_book_entries(ticker, side);
CREATE INDEX idx_book_order_id ON order_book_entries(order_id);
CREATE INDEX idx_book_price ON order_book_entries(price);
