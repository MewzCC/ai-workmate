-- Code-owned L0 contract upgrade only. Preserve all tenant policies, permissions and switches.
-- Old frozen plans keep their old hash and must be replanned; never rewrite task snapshots.
UPDATE agent_tool
SET parameters_schema = '{"type":"object","additionalProperties":false,"required":["taskId"],"properties":{"taskId":{"type":"integer","minimum":1},"keyword":{"type":"string","maxLength":64},"page":{"type":"integer","minimum":1,"maximum":10000},"size":{"type":"integer","minimum":1,"maximum":50},"approversOnly":{"type":"boolean"}}}'::jsonb,
    output_schema = '{"type":"object","additionalProperties":false,"required":["items","total","page","size","approversOnly"],"properties":{"items":{"type":"array","maxItems":50,"items":{"type":"object","additionalProperties":false,"required":["id","name","canApprove"],"properties":{"id":{"type":"integer","minimum":1},"name":{"type":"string","maxLength":120},"canApprove":{"type":"boolean"}}}},"total":{"type":"integer","minimum":0},"page":{"type":"integer","minimum":1,"maximum":10000},"size":{"type":"integer","minimum":1,"maximum":50},"approversOnly":{"type":"boolean"}}}'::jsonb,
    schema_hash = 'sha256:3c3328851fef6a8c491d742bde4da1fd6de15cb055303394f1778b140b5ef5c8',
    description = CASE WHEN description = 'Lists up to 50 eligible collaboration candidates for one pending task assigned to the actor.'
        THEN 'Pages collaboration candidates for your assigned pending task; optionally restricts to eligible approvers before pagination.'
        ELSE description END,
    updated_at = CURRENT_TIMESTAMP
WHERE code = 'approval.task.participants.query'
  AND handler_version = '1.0.0' AND risk_level = 'L0' AND side_effect = 'NONE'
  AND schema_hash = 'sha256:c1f565d9ed17668eb3c885274fcebdafdead56a51e4b560f329f6668eeca2dc3';
