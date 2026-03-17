-- Fund trader2 with $100,000 starting balance
-- UUID must match auth-service V3 migration
INSERT INTO accounts (user_id, cash_balance, cash_on_hold) VALUES
    ('a1000000-0000-0000-0000-000000000004', 100000.0000, 0.0000);

INSERT INTO ledger_transactions (user_id, type, amount, description) VALUES
    ('a1000000-0000-0000-0000-000000000004', 'CREDIT', 100000.0000,
     'Initial funding - $100,000 starting balance');
