-- Jackson writes the large DoubleNode maximum using exponent notation. The
-- protocol hash uses Jackson's exact schema serialization, not JSONB formatting.
-- Repair only the reviewed old contract; preserve enabled flags and all policy gates.
UPDATE agent_tool
SET schema_hash = 'sha256:d5e7ac51b2bfd63720ac4e27277a3799d861f2173eddd8330270efbff9e2225e',
    updated_at = CURRENT_TIMESTAMP
WHERE code = 'expense.apply'
  AND handler_version = '1.0.0'
  AND schema_hash = 'sha256:ce17ae535efa0de9beed88a10c326cafc7831f98176523b4bf52d6a0cfd7d371'
  AND parameters_schema = '{"type":"object","additionalProperties":false,"minProperties":1,"properties":{"amount":{"type":"number","minimum":0.01,"maximum":999999999.99,"multipleOf":0.01},"category":{"type":"string","enum":["TRAVEL","MEAL","TRANSPORT","OFFICE","OTHER"]},"expenseDate":{"type":"string","format":"date"},"invoiceNumber":{"type":"string","minLength":1,"maxLength":100},"reason":{"type":"string","minLength":1,"maxLength":1000}},"required":["amount","category","expenseDate","reason"]}'::jsonb
  AND output_schema = '{"type":"object","additionalProperties":false,"required":["applicationId","formKey","status","version"],"properties":{"applicationId":{"type":"integer","minimum":1},"formKey":{"type":"string","const":"expense-application"},"status":{"type":"string","const":"PENDING"},"version":{"type":"integer","minimum":1}}}'::jsonb;
