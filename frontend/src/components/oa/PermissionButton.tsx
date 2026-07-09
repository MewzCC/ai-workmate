'use client';

import { Button, message } from 'antd';
import type { ButtonProps } from 'antd';
import type { OaRole, PermissionAction } from '@/types/oa';
import { can } from '@/mock/oaPermissions';

interface PermissionButtonProps extends ButtonProps {
  role: OaRole;
  menuId: string;
  action: PermissionAction;
  deniedText?: string;
}

export default function PermissionButton({
  role,
  menuId,
  action,
  deniedText = '当前角色无权限执行该操作',
  onClick,
  children,
  ...props
}: PermissionButtonProps) {
  const allowed = can(role, menuId, action);

  return (
    <Button
      {...props}
      disabled={props.disabled || !allowed}
      onClick={(event) => {
        if (!allowed) {
          message.warning(deniedText);
          return;
        }
        onClick?.(event);
      }}
    >
      {children}
    </Button>
  );
}
