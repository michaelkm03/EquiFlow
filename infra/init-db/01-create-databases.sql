-- EquiFlow: Initialize all service databases
-- Uses conditional creation to be idempotent (equiflow_auth is created by POSTGRES_DB env var)

SELECT 'CREATE DATABASE equiflow_orders' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'equiflow_orders')\gexec
SELECT 'CREATE DATABASE equiflow_market' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'equiflow_market')\gexec
SELECT 'CREATE DATABASE equiflow_compliance' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'equiflow_compliance')\gexec
SELECT 'CREATE DATABASE equiflow_ledger' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'equiflow_ledger')\gexec
SELECT 'CREATE DATABASE equiflow_settlement' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'equiflow_settlement')\gexec
SELECT 'CREATE DATABASE equiflow_audit' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'equiflow_audit')\gexec
SELECT 'CREATE DATABASE equiflow_saga' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'equiflow_saga')\gexec
SELECT 'CREATE DATABASE equiflow_chaos' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'equiflow_chaos')\gexec

GRANT ALL PRIVILEGES ON DATABASE equiflow_auth TO equiflow;
GRANT ALL PRIVILEGES ON DATABASE equiflow_orders TO equiflow;
GRANT ALL PRIVILEGES ON DATABASE equiflow_market TO equiflow;
GRANT ALL PRIVILEGES ON DATABASE equiflow_compliance TO equiflow;
GRANT ALL PRIVILEGES ON DATABASE equiflow_ledger TO equiflow;
GRANT ALL PRIVILEGES ON DATABASE equiflow_settlement TO equiflow;
GRANT ALL PRIVILEGES ON DATABASE equiflow_audit TO equiflow;
GRANT ALL PRIVILEGES ON DATABASE equiflow_saga TO equiflow;
GRANT ALL PRIVILEGES ON DATABASE equiflow_chaos TO equiflow;
