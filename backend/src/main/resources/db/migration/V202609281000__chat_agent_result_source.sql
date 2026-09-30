ALTER TABLE message ADD COLUMN IF NOT EXISTS source_task_no VARCHAR(64);
CREATE UNIQUE INDEX IF NOT EXISTS uk_message_source_task_no
    ON message(source_task_no) WHERE source_task_no IS NOT NULL;
