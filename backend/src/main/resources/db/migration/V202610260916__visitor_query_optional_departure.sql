-- Match the existing optional departure field in the visitor domain API. Keep permissions/switches unchanged.
UPDATE agent_tool SET
    output_schema=jsonb_set(output_schema,'{properties,items,items,required}',(
        SELECT jsonb_agg(field.value ORDER BY field.ordinality)
        FROM jsonb_array_elements(output_schema #> '{properties,items,items,required}') WITH ORDINALITY AS field(value,ordinality)
        WHERE field.value<>'"expectedLeaveAt"'::jsonb
    )),
    schema_hash='sha256:0ee54fb81f4e29f20108018ac3449c19f747fa481249a87b67d06d8e8479891e',
    updated_at=CURRENT_TIMESTAMP
WHERE code='visitor.query' AND handler_version='1.0.0'
  AND schema_hash='sha256:aa6f923d24734c45f3022026885641deb9b74abbe6e9061394234e5cc739a3aa';
