ALTER TABLE conversation
    ALTER COLUMN model SET DEFAULT 'deepseek-v4-flash';

UPDATE conversation
SET model = 'deepseek-v4-flash'
WHERE model = 'deepseek-chat';
