'use client';

import { useEffect, useLayoutEffect, useRef, useState } from 'react';
import { Avatar, Button, Form, Input, Modal, Space, Tabs, Upload } from 'antd';
import { useTranslation } from 'react-i18next';
import { message } from '@/lib/antdMessage';
import { useAuth } from '@/components/auth/AuthProvider';
import { profileApi } from '@/lib/profileApi';
import { OaIcon } from '@/components/OaIcon';
import { isFormValidationError, useAsyncFormAction } from '@/hooks/useAsyncFormAction';

const AVATAR_TYPES = new Set(['image/jpeg', 'image/png', 'image/webp']);
const AVATAR_MAX_BYTES = 2 * 1024 * 1024;
// 与后端 PasswordPolicy.PASSWORD_REGEX 保持一致：8-32 位，含字母和数字，无空格
const PASSWORD_PATTERN = /^(?=.*[A-Za-z])(?=.*\d)\S{8,32}$/;

interface ProfileSettingsModalProps {
  open: boolean;
  onClose: () => void;
}

interface ProfileForm {
  name: string;
  email: string;
}

interface PasswordForm {
  oldPassword: string;
  newPassword: string;
  confirmPassword: string;
}

export default function ProfileSettingsModal({ open, onClose }: ProfileSettingsModalProps) {
  const { t } = useTranslation();
  const { user, setUser } = useAuth();
  const identity = `${user?.tenantId}:${user?.id}`;
  const latestUser = useRef(user); latestUser.current = user;
  const action = useAsyncFormAction(`${identity}:${user?.permissionVersion}:${open}`);
  type Submission = NonNullable<ReturnType<typeof action.begin>>;
  const [form] = Form.useForm<ProfileForm>();
  const [passwordForm] = Form.useForm<PasswordForm>();
  const [avatarFile, setAvatarFile] = useState<File | null>(null);
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const [operation, setOperation] = useState<'save' | 'delete' | 'password'>();
  const saving = action.busy && operation === 'save', deleting = action.busy && operation === 'delete';
  const [activeTab, setActiveTab] = useState<'profile' | 'password'>('profile');

  useLayoutEffect(() => {
    const current = latestUser.current;
    form.resetFields();
    if (open && current) form.setFieldsValue({ name: current.name, email: current.email });
    setAvatarFile(null);
    setPreviewUrl(null);
    setActiveTab('profile');
    passwordForm.resetFields();
  }, [form, passwordForm, open, identity]);

  useEffect(() => () => {
    if (previewUrl) URL.revokeObjectURL(previewUrl);
  }, [previewUrl]);

  const selectAvatar = (file: File) => {
    if (!open || !user || action.busy) return Upload.LIST_IGNORE;
    if (!AVATAR_TYPES.has(file.type)) {
      message.error(t('profile.avatar.invalidType'));
      return Upload.LIST_IGNORE;
    }
    if (file.size > AVATAR_MAX_BYTES) {
      message.error(t('profile.avatar.tooLarge', { max: '2MB' }));
      return Upload.LIST_IGNORE;
    }
    if (previewUrl) URL.revokeObjectURL(previewUrl);
    setAvatarFile(file);
    setPreviewUrl(URL.createObjectURL(file));
    return false;
  };

  const save = async (values: ProfileForm, attempt: Submission) => {
    if (!attempt.current() || !user) return;
    try {
      let updated = await profileApi.update(values.name);
      if (!attempt.current()) return;
      if (avatarFile) updated = await profileApi.uploadAvatar(avatarFile);
      if (!attempt.current()) return;
      if (updated.id !== user.id || updated.tenantId !== user.tenantId) throw new Error(t('profile.message.profileUpdateFailed'));
      setUser(updated);
      message.success(t('profile.message.profileUpdated'));
      onClose();
    } catch (error) {
      if (!attempt.current()) return;
      message.error(error instanceof Error ? error.message : t('profile.message.profileUpdateFailed'));
    }
  };

  const deleteAvatar = async () => {
    if (!open || !user) return;
    const attempt = action.begin();
    if (!attempt) return;
    setOperation('delete');
    try {
      const updated = await profileApi.deleteAvatar();
      if (!attempt.current()) return;
      if (updated.id !== user.id || updated.tenantId !== user.tenantId) throw new Error(t('profile.message.avatarRemoveFailed'));
      setUser(updated);
      setAvatarFile(null);
      setPreviewUrl(null);
      message.success(t('profile.message.avatarRemoved'));
    } catch (error) {
      if (!attempt.current()) return;
      message.error(error instanceof Error ? error.message : t('profile.message.avatarRemoveFailed'));
    } finally {
      attempt.finish();
    }
  };

  const changePassword = async (values: PasswordForm, attempt: Submission) => {
    if (!attempt.current() || !user) return;
    try {
      await profileApi.changePassword(values.oldPassword, values.newPassword);
      if (!attempt.current()) return;
      message.success(t('profile.message.passwordChanged'));
      passwordForm.resetFields();
      onClose();
    } catch (error) {
      if (!attempt.current()) return;
      message.error(error instanceof Error ? error.message : t('profile.message.passwordChangeFailed'));
    }
  };

  const handleOk = async () => {
    if (!open || !user || action.busy) return;
    const attempt = action.begin();
    if (!attempt) return;
    setOperation(activeTab === 'password' ? 'password' : 'save');
    try {
      if (activeTab === 'password') {
        const values = await passwordForm.validateFields();
        if (attempt.current()) await changePassword(values, attempt);
      } else {
        const values = await form.validateFields();
        if (attempt.current()) await save(values, attempt);
      }
    } catch (error) {
      if (attempt.current() && !isFormValidationError(error)) message.error(t('profile.message.profileUpdateFailed'));
    } finally { attempt.finish(); }
  };

  const okText = activeTab === 'password'
    ? t('profile.password.submit')
    : t('profile.okText');

  return (
    <Modal
      title={t('profile.title')}
      open={open && Boolean(user)}
      onCancel={() => { action.reset(); passwordForm.resetFields(); onClose(); }}
      onOk={handleOk}
      okText={okText}
      okButtonProps={{ loading: saving || (action.busy && operation === 'password'), disabled: deleting }}
      cancelText={t(action.busy ? 'common.close' : 'common.cancel')}
      destroyOnHidden
    >
      <Tabs
        activeKey={activeTab}
        onChange={(key) => { if (!action.busy) setActiveTab(key as 'profile' | 'password'); }}
        items={[
          {
            key: 'profile',
            disabled: action.busy,
            label: t('profile.tabs.profile'),
            children: (
              <>
                <div className="oa-profile-avatar-editor">
                  <Avatar size={80} src={previewUrl || user?.avatarUrl} icon={<OaIcon name="avatar" size={32} />} />
                  <Space>
                    <Upload
                      accept=".jpg,.jpeg,.png,.webp"
                      maxCount={1}
                      showUploadList={false}
                      beforeUpload={selectAvatar}
                      disabled={action.busy}
                    >
                      <Button disabled={action.busy} icon={<OaIcon name="upload" />}>{t('profile.avatar.select')}</Button>
                    </Upload>
                    {(user?.avatarUrl || avatarFile) && (
                      <Button
                        danger
                        icon={<OaIcon name="delete" />}
                        loading={deleting}
                        disabled={action.busy}
                        onClick={() => void deleteAvatar()}
                      >
                        {t('profile.avatar.remove')}
                      </Button>
                    )}
                  </Space>
                  <span>{t('profile.avatar.hint')}</span>
                </div>
                <Form form={form} disabled={action.busy} layout="vertical" onSubmitCapture={(event) => { event.preventDefault(); event.stopPropagation(); void handleOk(); }}>
                  <Form.Item
                    name="name"
                    label={t('profile.field.name')}
                    rules={[
                      { required: true, message: t('profile.validation.nameRequired') },
                      { max: 50, message: t('profile.validation.nameTooLong', { max: 50 }) },
                    ]}
                  >
                    <Input placeholder={t('profile.field.namePlaceholder')} />
                  </Form.Item>
                  <Form.Item name="email" label={t('profile.field.email')}>
                    <Input disabled />
                  </Form.Item>
                </Form>
              </>
            ),
          },
          {
            key: 'password',
            disabled: action.busy,
            label: t('profile.tabs.password'),
            children: (
              <Form form={passwordForm} disabled={action.busy} layout="vertical" onSubmitCapture={(event) => { event.preventDefault(); event.stopPropagation(); void handleOk(); }}>
                <Form.Item
                  name="oldPassword"
                  label={t('profile.password.oldPassword')}
                  rules={[{ required: true, message: t('profile.password.oldRequired') }]}
                >
                  <Input.Password placeholder={t('profile.password.oldPlaceholder')} autoComplete="current-password" />
                </Form.Item>
                <Form.Item
                  name="newPassword"
                  label={t('profile.password.newPassword')}
                  rules={[
                    { required: true, message: t('profile.validation.newPasswordRequired') },
                    { pattern: PASSWORD_PATTERN, message: t('profile.password.policyHint') },
                  ]}
                >
                  <Input.Password placeholder={t('profile.password.newPlaceholder')} autoComplete="new-password" />
                </Form.Item>
                <Form.Item
                  name="confirmPassword"
                  label={t('profile.password.confirmPassword')}
                  dependencies={['newPassword']}
                  rules={[
                    { required: true, message: t('profile.password.confirmRequired') },
                    ({ getFieldValue }) => ({
                      validator(_, value) {
                        if (!value || getFieldValue('newPassword') === value) {
                          return Promise.resolve();
                        }
                        return Promise.reject(new Error(t('profile.password.mismatch')));
                      },
                    }),
                  ]}
                >
                  <Input.Password placeholder={t('profile.password.confirmPlaceholder')} autoComplete="new-password" />
                </Form.Item>
                <span className="oa-profile-password-hint">{t('profile.password.policyHint')}</span>
              </Form>
            ),
          },
        ]}
      />
    </Modal>
  );
}
