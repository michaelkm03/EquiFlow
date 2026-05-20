-- Seed REJECTED orders for compliance agent testing
--
-- These orders were rejected at the compliance step and never reached matching.
-- Corresponding compliance_checks records live in the compliance-service DB (V2__seed_compliance.sql).
--
-- Fixed UUIDs:
--   b1000000-0000-0000-0000-000000000010  trader1  AAPL BUY  REJECTED  (INSUFFICIENT_FUNDS)
--   b1000000-0000-0000-0000-000000000011  trader2  TSLA BUY  REJECTED  (WASH_SALE)
--   b1000000-0000-0000-0000-000000000012  trader1  NVDA BUY  REJECTED  (INSUFFICIENT_FUNDS — trader1 repeat offender)
--   b1000000-0000-0000-0000-000000000013  trader1  MSFT BUY  FILLED    (APPROVED — control case, should not appear in breach summary)

INSERT INTO orders (id, user_id, ticker, side, type, quantity, limit_price, trigger_price, filled_price, filled_qty, status, created_at, updated_at) VALUES
    ('b1000000-0000-0000-0000-000000000010',
     'a1000000-0000-0000-0000-000000000001',
     'AAPL', 'BUY', 'MARKET', 9999, NULL, NULL, NULL, NULL,
     'REJECTED', NOW() - INTERVAL '3 hours', NOW() - INTERVAL '3 hours'),

    ('b1000000-0000-0000-0000-000000000011',
     'a1000000-0000-0000-0000-000000000004',
     'TSLA', 'BUY', 'MARKET', 10, NULL, NULL, NULL, NULL,
     'REJECTED', NOW() - INTERVAL '2 hours', NOW() - INTERVAL '2 hours'),

    ('b1000000-0000-0000-0000-000000000012',
     'a1000000-0000-0000-0000-000000000001',
     'NVDA', 'BUY', 'MARKET', 5000, NULL, NULL, NULL, NULL,
     'REJECTED', NOW() - INTERVAL '1 hour', NOW() - INTERVAL '1 hour'),

    ('b1000000-0000-0000-0000-000000000013',
     'a1000000-0000-0000-0000-000000000001',
     'MSFT', 'BUY', 'MARKET', 5, NULL, NULL, 415.50, 5,
     'FILLED', NOW() - INTERVAL '4 hours', NOW() - INTERVAL '4 hours');
