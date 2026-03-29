-- Seed orders for local dev and system-cancel testing (trader1 = a1000000-0000-0000-0000-000000000001)
--
-- Fixed UUIDs allow these rows to be referenced directly in tests and API calls:
--   b1000000-0000-0000-0000-000000000001  PENDING         → system-cancel → CANCELLED (normal path)
--   b1000000-0000-0000-0000-000000000002  OPEN            → system-cancel → CANCELLED (normal path)
--   b1000000-0000-0000-0000-000000000003  PENDING_TRIGGER → system-cancel → CANCELLED (stop-loss not yet triggered)
--   b1000000-0000-0000-0000-000000000004  FILLED          → system-cancel → HTTP 409  (money already moved)
--   b1000000-0000-0000-0000-000000000005  CANCELLED       → system-cancel → no-op 200 (already terminal)

INSERT INTO orders (id, user_id, ticker, side, type, quantity, limit_price, trigger_price, filled_price, filled_qty, status, created_at, updated_at) VALUES
    ('b1000000-0000-0000-0000-000000000001',
     'a1000000-0000-0000-0000-000000000001',
     'AAPL', 'BUY', 'MARKET', 10, NULL, NULL, NULL, NULL,
     'PENDING', NOW(), NOW()),

    ('b1000000-0000-0000-0000-000000000002',
     'a1000000-0000-0000-0000-000000000001',
     'TSLA', 'BUY', 'LIMIT', 5, 250.00, NULL, NULL, NULL,
     'OPEN', NOW(), NOW()),

    ('b1000000-0000-0000-0000-000000000003',
     'a1000000-0000-0000-0000-000000000001',
     'NVDA', 'SELL', 'STOP_LOSS', 8, NULL, 900.00, NULL, NULL,
     'PENDING_TRIGGER', NOW(), NOW()),

    -- filled_price and filled_qty populated: a FILLED order with nulls here would
    -- return broken data to any caller reading fill details (settlement, ledger, toResponse)
    ('b1000000-0000-0000-0000-000000000004',
     'a1000000-0000-0000-0000-000000000001',
     'MSFT', 'BUY', 'MARKET', 3, NULL, NULL, 415.50, 3,
     'FILLED', NOW(), NOW()),

    ('b1000000-0000-0000-0000-000000000005',
     'a1000000-0000-0000-0000-000000000001',
     'GOOG', 'SELL', 'LIMIT', 2, 175.00, NULL, NULL, NULL,
     'CANCELLED', NOW(), NOW());

-- order_book_entries FK: REFERENCES orders(id) — the OPEN TSLA limit order must have
-- a corresponding book entry or any order-book read/cancel path will find a broken state
INSERT INTO order_book_entries (order_id, ticker, side, price, remaining_qty) VALUES
    ('b1000000-0000-0000-0000-000000000002', 'TSLA', 'BUY', 250.00, 5);
