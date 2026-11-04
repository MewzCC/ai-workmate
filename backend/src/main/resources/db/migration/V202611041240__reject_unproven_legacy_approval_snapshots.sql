-- Reopened legacy drafts retain the old instance id but clear submitted_at. Their edited payload is not history.
-- Resolved plans are always created together with a real submission and must never be removed here.
DELETE FROM approval_execution
WHERE plan_json IS NULL AND (submitted_at IS NULL OR form_schema IS NULL);
