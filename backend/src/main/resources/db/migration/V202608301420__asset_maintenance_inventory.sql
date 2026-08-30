ALTER TABLE asset_operation
    DROP CONSTRAINT IF EXISTS ck_asset_operation_type;

ALTER TABLE asset_operation
    ADD COLUMN IF NOT EXISTS inventory_result VARCHAR(32),
    ADD COLUMN IF NOT EXISTS actual_status VARCHAR(20),
    ADD COLUMN IF NOT EXISTS actual_department_id BIGINT,
    ADD COLUMN IF NOT EXISTS actual_owner_user_id BIGINT;

ALTER TABLE asset_operation
    DROP CONSTRAINT IF EXISTS ck_asset_inventory_result,
    DROP CONSTRAINT IF EXISTS ck_asset_actual_status;

ALTER TABLE asset_operation
    ADD CONSTRAINT ck_asset_operation_type CHECK (
        operation_type IN (
            'CLAIM', 'RETURN', 'TRANSFER',
            'REPAIR_START', 'REPAIR_COMPLETE', 'INVENTORY', 'SCRAP'
        )
    );

ALTER TABLE asset_operation
    ADD CONSTRAINT ck_asset_inventory_result CHECK (
        inventory_result IS NULL OR inventory_result IN (
            'MATCH', 'MISSING', 'DAMAGED', 'LOCATION_MISMATCH', 'CUSTODIAN_MISMATCH'
        )
    );

ALTER TABLE asset_operation
    ADD CONSTRAINT ck_asset_actual_status CHECK (
        actual_status IS NULL OR actual_status IN ('IN_USE', 'IDLE', 'REPAIRING', 'SCRAPPED')
    );

CREATE INDEX IF NOT EXISTS idx_asset_operation_inventory_result
    ON asset_operation (tenant_id, inventory_result, created_at DESC)
    WHERE inventory_result IS NOT NULL;
