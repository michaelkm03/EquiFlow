-- Expanded compliance seed data matching V5__seed_more_rejected_orders.sql in order-service
--
-- Adds 10 rejected + 2 approved compliance checks.
-- Violation amounts are randomized to simulate realistic data variance.
--
-- trader1 = a1000000-0000-0000-0000-000000000001
-- trader2 = a1000000-0000-0000-0000-000000000004

INSERT INTO compliance_checks (id, order_id, user_id, ticker, result, violations, checked_at) VALUES

    -- trader1 GOOGL: INSUFFICIENT_FUNDS ($357,200 required, $82,500 available)
    ('d4000000-0000-0000-0000-000000000014',
     'b1000000-0000-0000-0000-000000000014',
     'a1000000-0000-0000-0000-000000000001',
     'GOOGL', 'REJECTED',
     '[{"code":"INSUFFICIENT_FUNDS","description":"Insufficient funds: Required $357200.00, Available $82500.00, Shortfall $274700.00","severity":"HARD_BLOCK"}]',
     NOW() - INTERVAL '5 hours'),

    -- trader2 NFLX: INSUFFICIENT_FUNDS ($318,500 required, $43,200 available)
    ('d4000000-0000-0000-0000-000000000015',
     'b1000000-0000-0000-0000-000000000015',
     'a1000000-0000-0000-0000-000000000004',
     'NFLX', 'REJECTED',
     '[{"code":"INSUFFICIENT_FUNDS","description":"Insufficient funds: Required $318500.00, Available $43200.00, Shortfall $275300.00","severity":"HARD_BLOCK"}]',
     NOW() - INTERVAL '6 hours'),

    -- trader1 TSLA: WASH_SALE (sold at loss 2026-04-15)
    ('d4000000-0000-0000-0000-000000000016',
     'b1000000-0000-0000-0000-000000000016',
     'a1000000-0000-0000-0000-000000000001',
     'TSLA', 'REJECTED',
     '[{"code":"WASH_SALE","description":"Wash-sale rule violation: TSLA was sold at a loss on 2026-04-15. Repurchase within 30 days is prohibited.","severity":"HARD_BLOCK"}]',
     NOW() - INTERVAL '7 hours'),

    -- trader2 AAPL: WASH_SALE (sold at loss 2026-04-28)
    ('d4000000-0000-0000-0000-000000000017',
     'b1000000-0000-0000-0000-000000000017',
     'a1000000-0000-0000-0000-000000000004',
     'AAPL', 'REJECTED',
     '[{"code":"WASH_SALE","description":"Wash-sale rule violation: AAPL was sold at a loss on 2026-04-28. Repurchase within 30 days is prohibited.","severity":"HARD_BLOCK"}]',
     NOW() - INTERVAL '8 hours'),

    -- trader1 AMD: INSUFFICIENT_FUNDS ($486,000 required, $91,750 available)
    ('d4000000-0000-0000-0000-000000000018',
     'b1000000-0000-0000-0000-000000000018',
     'a1000000-0000-0000-0000-000000000001',
     'AMD', 'REJECTED',
     '[{"code":"INSUFFICIENT_FUNDS","description":"Insufficient funds: Required $486000.00, Available $91750.00, Shortfall $394250.00","severity":"HARD_BLOCK"}]',
     NOW() - INTERVAL '9 hours'),

    -- trader2 NVDA: INSUFFICIENT_FUNDS ($166,500 required, $58,000 available)
    ('d4000000-0000-0000-0000-000000000019',
     'b1000000-0000-0000-0000-000000000019',
     'a1000000-0000-0000-0000-000000000004',
     'NVDA', 'REJECTED',
     '[{"code":"INSUFFICIENT_FUNDS","description":"Insufficient funds: Required $166500.00, Available $58000.00, Shortfall $108500.00","severity":"HARD_BLOCK"}]',
     NOW() - INTERVAL '10 hours'),

    -- trader1 AMZN: WASH_SALE (sold at loss 2026-04-10)
    ('d4000000-0000-0000-0000-000000000020',
     'b1000000-0000-0000-0000-000000000020',
     'a1000000-0000-0000-0000-000000000001',
     'AMZN', 'REJECTED',
     '[{"code":"WASH_SALE","description":"Wash-sale rule violation: AMZN was sold at a loss on 2026-04-10. Repurchase within 30 days is prohibited.","severity":"HARD_BLOCK"}]',
     NOW() - INTERVAL '11 hours'),

    -- trader2 GOOG: WASH_SALE (sold at loss 2026-05-01)
    ('d4000000-0000-0000-0000-000000000021',
     'b1000000-0000-0000-0000-000000000021',
     'a1000000-0000-0000-0000-000000000004',
     'GOOG', 'REJECTED',
     '[{"code":"WASH_SALE","description":"Wash-sale rule violation: GOOG was sold at a loss on 2026-05-01. Repurchase within 30 days is prohibited.","severity":"HARD_BLOCK"}]',
     NOW() - INTERVAL '12 hours'),

    -- trader1 META: INSUFFICIENT_FUNDS ($521,000 required, $76,300 available)
    ('d4000000-0000-0000-0000-000000000022',
     'b1000000-0000-0000-0000-000000000022',
     'a1000000-0000-0000-0000-000000000001',
     'META', 'REJECTED',
     '[{"code":"INSUFFICIENT_FUNDS","description":"Insufficient funds: Required $521000.00, Available $76300.00, Shortfall $444700.00","severity":"HARD_BLOCK"}]',
     NOW() - INTERVAL '13 hours'),

    -- trader2 META: INSUFFICIENT_FUNDS ($390,750 required, $62,400 available)
    ('d4000000-0000-0000-0000-000000000023',
     'b1000000-0000-0000-0000-000000000023',
     'a1000000-0000-0000-0000-000000000004',
     'META', 'REJECTED',
     '[{"code":"INSUFFICIENT_FUNDS","description":"Insufficient funds: Required $390750.00, Available $62400.00, Shortfall $328350.00","severity":"HARD_BLOCK"}]',
     NOW() - INTERVAL '14 hours'),

    -- trader1 INTC: APPROVED (control)
    ('d4000000-0000-0000-0000-000000000024',
     'b1000000-0000-0000-0000-000000000024',
     'a1000000-0000-0000-0000-000000000001',
     'INTC', 'APPROVED',
     '[]',
     NOW() - INTERVAL '15 hours'),

    -- trader2 MSFT: APPROVED (control)
    ('d4000000-0000-0000-0000-000000000025',
     'b1000000-0000-0000-0000-000000000025',
     'a1000000-0000-0000-0000-000000000004',
     'MSFT', 'APPROVED',
     '[]',
     NOW() - INTERVAL '16 hours');
