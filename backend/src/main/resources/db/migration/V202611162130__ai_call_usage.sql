CREATE TABLE IF NOT EXISTS ai_call_usage (
 id BIGSERIAL PRIMARY KEY,
 channel_id BIGINT NOT NULL,
 model VARCHAR(128) NOT NULL,
 status VARCHAR(16) NOT NULL CHECK(status IN ('SUCCESS','ERROR','CANCELLED')),
 duration_ms BIGINT NOT NULL CHECK(duration_ms >= 0),
 input_tokens BIGINT,
 output_tokens BIGINT,
 total_tokens BIGINT,
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_ai_call_usage_time ON ai_call_usage(created_at);
