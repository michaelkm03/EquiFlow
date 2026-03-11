-- EquiFlow: Initialize all service databases
-- This runs on first PostgreSQL container startup

CREATE DATABASE equiflow_auth;
CREATE DATABASE equiflow_orders;
CREATE DATABASE equiflow_market;
CREATE DATABASE equiflow_compliance;
CREATE DATABASE equiflow_ledger;
CREATE DATABASE equiflow_settlement;
CREATE DATABASE equiflow_audit;
CREATE DATABASE equiflow_saga;
CREATE DATABASE equiflow_chaos;

-- Grant all privileges to equiflow user on each database
GRANT ALL PRIVILEGES ON DATABASE equiflow_auth TO equiflow;
GRANT ALL PRIVILEGES ON DATABASE equiflow_orders TO equiflow;
GRANT ALL PRIVILEGES ON DATABASE equiflow_market TO equiflow;
GRANT ALL PRIVILEGES ON DATABASE equiflow_compliance TO equiflow;
GRANT ALL PRIVILEGES ON DATABASE equiflow_ledger TO equiflow;
GRANT ALL PRIVILEGES ON DATABASE equiflow_settlement TO equiflow;
GRANT ALL PRIVILEGES ON DATABASE equiflow_audit TO equiflow;
GRANT ALL PRIVILEGES ON DATABASE equiflow_saga TO equiflow;
GRANT ALL PRIVILEGES ON DATABASE equiflow_chaos TO equiflow;
