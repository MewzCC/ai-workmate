'use client';

import { Button } from 'antd';
import { message } from '@/lib/antdMessage';
import { useTranslation } from 'react-i18next';
import type { ButtonProps } from 'antd';
import { usePermission, type PermissionMatchMode } from '@/hooks/usePermission';

interface PermissionButtonProps extends ButtonProps {
  permission: string | readonly string[];
  mode?: PermissionMatchMode;
  deniedText?: string;
}

export default function PermissionButton({
  permission,
  mode = 'ALL',
  deniedText,
  onClick,
  children,
  ...props
}: PermissionButtonProps) {
  const { t } = useTranslation();
  const { allowed } = usePermission(permission, mode);

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
