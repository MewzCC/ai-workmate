-- Refresh existing sessions after the additive asset candidate permissions.
-- Version changes invalidate cached capabilities; they do not grant business permissions or enable writes.
UPDATE app_user actor
SET permission_version=COALESCE(actor.permission_version,0)+1
WHERE EXISTS (
    SELECT 1 FROM rbac_role_permission business
    WHERE business.tenant_id=actor.tenant_id AND business.permission_code='asset:write'
      AND EXISTS (SELECT 1 FROM rbac_role_permission candidate
          WHERE candidate.tenant_id=business.tenant_id AND candidate.role_code=business.role_code
            AND candidate.permission_code='agent:tool:asset.department.query')
      AND EXISTS (SELECT 1 FROM rbac_role_permission candidate
          WHERE candidate.tenant_id=business.tenant_id AND candidate.role_code=business.role_code
            AND candidate.permission_code='agent:tool:asset.custodian.query')
      AND (EXISTS (SELECT 1 FROM user_role assigned
          WHERE assigned.tenant_id=actor.tenant_id AND assigned.user_id=actor.id
            AND assigned.role_code=business.role_code)
        OR (NOT EXISTS (SELECT 1 FROM user_role assigned
            WHERE assigned.tenant_id=actor.tenant_id AND assigned.user_id=actor.id)
          AND actor.role=business.role_code))
);
