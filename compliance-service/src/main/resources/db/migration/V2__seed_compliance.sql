-- Seed compliance check records for local dev and agent testing
--
-- Covers every case the compliance breach agent needs to handle:
--   REJECTED + INSUFFICIENT_FUNDS  (trader1, order 010)
--   REJECTED + WASH_SALE           (trader2, order 011)
--   REJECTED + INSUFFICIENT_FUNDS  (trader1, order 012) — trader1 is a repeat offender
--   APPROVED + no violations       (trader1, order 013) — control case, agent should ignore
--
-- Order UUIDs match V4__seed_rejected_orders.sql in order-service.
-- User UUIDs match auth-service seed data:
--   trader1 = a1000000-0000-0000-0000-000000000001
--   trader2 = a1000000-0000-0000-0000-000000000004

INSERT INTO compliance_checks (id, order_id, user_id, ticker, result, violations, checked_at) VALUES

    -- trader1 AAPL BUY rejected: insufficient funds
    ('d4000000-0000-0000-0000-000000000010',
     'b1000000-0000-0000-0000-000000000010',
     'a1000000-0000-0000-0000-000000000001',
     'AAPL', 'REJECTED',
     '[{"code":"INSUFFICIENT_FUNDS","description":"Insufficient funds: Required $1723500.00, Available $100000.00, Shortfall $1623500.00","severity":"HARD_BLOCK"}]',
     NOW() - INTERVAL '3 hours'),

    -- trader2 TSLA BUY rejected: wash-sale
    ('d4000000-0000-0000-0000-000000000011',
     'b1000000-0000-0000-0000-000000000011',
     'a1000000-0000-0000-0000-000000000004',
     'TSLA', 'REJECTED',
     '[{"code":"WASH_SALE","description":"Wash-sale rule violation: TSLA was sold at a loss on 2026-04-22. Repurchase within 30 days is prohibited.","severity":"HARD_BLOCK"}]',
     NOW() - INTERVAL '2 hours'),

    -- trader1 NVDA BUY rejected: insufficient funds (repeat offender)
    ('d4000000-0000-0000-0000-000000000012',
     'b1000000-0000-0000-0000-000000000012',
     'a1000000-0000-0000-0000-000000000001',
     'NVDA', 'REJECTED',
     '[{"code":"INSUFFICIENT_FUNDS","description":"Insufficient funds: Required $500000.00, Available $100000.00, Shortfall $400000.00","severity":"HARD_BLOCK"}]',
     NOW() - INTERVAL '1 hour'),

    -- trader1 MSFT BUY approved: control case
    ('d4000000-0000-0000-0000-000000000013',
     'b1000000-0000-0000-0000-000000000013',
     'a1000000-0000-0000-0000-000000000001',
     'MSFT', 'APPROVED',
     '[]',
     NOW() - INTERVAL '4 hours');
