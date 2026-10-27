-- Existing typed read projections include optimistic-lock versions in settings and reissues.
UPDATE agent_tool SET
 output_schema=jsonb_set(jsonb_set(output_schema,'{properties,reissues,items,properties,version}','{"type":"integer","minimum":0}'::jsonb),
     '{properties,settings,properties,version}','{"type":"integer","minimum":0}'::jsonb),
 schema_hash='sha256:bbf04a579ddf4826587b5a21e662e25259ef69e41a93cb55e753d275195ce1b4',
 updated_at=CURRENT_TIMESTAMP
WHERE code='attendance.query' AND handler_version='1.0.0'
 AND schema_hash='sha256:4ff357d3836da753f0c32d3774ea04ba3cf83d1c6b8708de548e8ff711348d60';
