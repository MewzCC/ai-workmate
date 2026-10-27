-- Preserve each tenant's actual previous route audience while introducing the canonical permission.
INSERT INTO rbac_permission(code,name,module,description,tenant_id)
SELECT 'route:approval-form','通用申请页面','页面访问','访问通用申请页面',id
FROM tenant WHERE code='DEFAULT'
ON CONFLICT (code) DO NOTHING;

WITH added_grants AS (
    INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
    SELECT DISTINCT rp.tenant_id,rp.role_code,'route:approval-form'
    FROM rbac_route route
    JOIN rbac_role_permission rp ON rp.tenant_id=route.tenant_id AND rp.permission_code=route.permission_code
    WHERE route.route_key='approval-form' AND route.route_type='PAGE'
      AND route.component_key='APPROVAL_FORM' AND route.permission_code<>'route:approval-form'
    ON CONFLICT DO NOTHING
    RETURNING tenant_id,role_code
)
UPDATE app_user actor SET permission_version=COALESCE(actor.permission_version,0)+1
WHERE EXISTS (
    SELECT 1 FROM added_grants grant_row
    WHERE grant_row.tenant_id=actor.tenant_id
      AND (actor.role=grant_row.role_code OR EXISTS (
          SELECT 1 FROM user_role membership
          WHERE membership.tenant_id=actor.tenant_id AND membership.user_id=actor.id
            AND membership.role_code=grant_row.role_code
      ))
);

UPDATE rbac_route SET permission_code='route:approval-form',updated_at=CURRENT_TIMESTAMP
WHERE route_key='approval-form' AND route_type='PAGE' AND component_key='APPROVAL_FORM'
  AND permission_code<>'route:approval-form';
