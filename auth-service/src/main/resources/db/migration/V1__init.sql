-- Auth Service Schema

CREATE TYPE user_role AS ENUM ('TRADER', 'REGULATOR', 'BOT_OPERATOR');

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(50) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role user_role NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    last_login_at TIMESTAMP
);

CREATE INDEX idx_users_username ON users(username);

-- Seed users (BCrypt of 'password123')
-- trader1 has a known UUID to be referenced by ledger-service
INSERT INTO users (id, username, password_hash, role) VALUES
    ('a1000000-0000-0000-0000-000000000001',
     'trader1',
     '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
     'TRADER'),
    ('a1000000-0000-0000-0000-000000000002',
     'regulator1',
     '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
     'REGULATOR'),
    ('a1000000-0000-0000-0000-000000000003',
     'bot-operator1',
     '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
     'BOT_OPERATOR');
