-- Change role column from PostgreSQL ENUM to VARCHAR for standard JPA compatibility
ALTER TABLE users ALTER COLUMN role TYPE VARCHAR(50) USING role::VARCHAR;

-- Fix password hashes: update to correct BCrypt of 'password123'
UPDATE users SET password_hash = '$2b$10$rLsD8zrCcZX1/WVAp4gOUugoCIqwbFfdNokEpkz4CplaSvOqc4nfa'
WHERE username IN ('trader1', 'regulator1', 'bot-operator1');
