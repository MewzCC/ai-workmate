'use client';

import { Button } from 'antd';
import { message } from '@/lib/antdMessage';
import { useTranslation } from 'react-i18next';
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
  deniedText,
  onClick,
  children,
  ...props
}: PermissionButtonProps) {
  const { t } = useTranslation();
  const allowed = can(role, menuId, action);

  return (
    <Button
      {...props}
      disabled={props.disabled || !allowed}
      onClick={(event) => {
        if (!allowed) {
          message.warning(deniedText ?? t('oa.ai.noPermission'));
          return;
        }
        onClick?.(event);
      }}
    >
      {children}
    </Button>
  );
}
