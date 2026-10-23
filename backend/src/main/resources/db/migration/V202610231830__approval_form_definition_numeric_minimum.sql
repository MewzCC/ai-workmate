-- Roll forward the already exercised read contract; never rewrite the earlier migration.
UPDATE agent_tool
SET output_schema=jsonb_set(output_schema,'{properties,fields,items,properties,min}',
        '{"type":"number","minimum":-1000000000000000,"maximum":1000000000000000}'::jsonb,TRUE),
    schema_hash='sha256:b91991992231bd332647b3c0942cba36cce1372e9b5d67692b2090366727e00f'
WHERE tenant_id IS NULL AND code='approval.form.definition.query'
  AND schema_hash='sha256:ca2407af55e1fa1053bed2a3ebf77f781e9f9c0ad5496979785cdbbe938c997e';
