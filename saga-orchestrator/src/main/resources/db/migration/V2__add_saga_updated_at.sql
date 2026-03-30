-- EQ-113c: add updated_at for SagaRecoveryJob to detect stuck COMPENSATING sagas
ALTER TABLE sagas ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT NOW();
