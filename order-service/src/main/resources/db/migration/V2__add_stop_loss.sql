-- Add stop-loss support

ALTER TABLE orders ADD COLUMN trigger_price NUMERIC(18, 4);

CREATE INDEX idx_orders_ticker_status_trigger ON orders(ticker, status, trigger_price);
