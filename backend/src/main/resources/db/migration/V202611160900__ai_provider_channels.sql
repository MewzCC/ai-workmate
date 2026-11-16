ALTER TABLE ai_provider_configuration DROP CONSTRAINT IF EXISTS ai_provider_configuration_id_check;
ALTER TABLE ai_provider_configuration ADD COLUMN IF NOT EXISTS channel_name VARCHAR(80) NOT NULL DEFAULT 'Primary';
