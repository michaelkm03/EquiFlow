-- Seed trader2 for multi-user flow testing
-- UUID a1000000-0000-0000-0000-000000000004 is referenced by ledger-service V3 migration
INSERT INTO users (id, username, password_hash, role) VALUES
    ('a1000000-0000-0000-0000-000000000004',
     'trader2',
     '$2b$10$rLsD8zrCcZX1/WVAp4gOUugoCIqwbFfdNokEpkz4CplaSvOqc4nfa',
     'TRADER');
