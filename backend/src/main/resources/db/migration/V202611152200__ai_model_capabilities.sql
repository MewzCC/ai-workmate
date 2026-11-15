ALTER TABLE ai_provider_configuration ADD COLUMN IF NOT EXISTS models JSONB NOT NULL DEFAULT '[]'::jsonb;
UPDATE ai_provider_configuration SET base_url = RTRIM(base_url, '/') || '/v1'
WHERE base_url NOT LIKE '%/v1';
UPDATE ai_provider_configuration SET models=jsonb_build_array(jsonb_build_object(
    'id',model,'contextWindow',32768,'maxOutputTokens',4096,'supportsImages',false))
WHERE models='[]'::jsonb;
