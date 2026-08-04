'use client';

import { useEffect, useState } from 'react';
import { Avatar, Button, Form, Input, Modal, Space, Upload } from 'antd';
import { useTranslation } from 'react-i18next';
import { message } from '@/lib/antdMessage';
import { useAuth } from '@/components/auth/AuthProvider';
import { profileApi } from '@/lib/profileApi';
import { OaIcon } from '@/components/OaIcon';

const AVATAR_TYPES = new Set(['image/jpeg', 'image/png', 'image/webp']);
const AVATAR_MAX_BYTES = 2 * 1024 * 1024;

interface ProfileSettingsModalProps {
  open: boolean;
  onClose: () => void;
}

interface ProfileForm {
  name: string;
  email: string;
}

export default function ProfileSettingsModal({ open, onClose }: ProfileSettingsModalProps) {
  const { t } = useTranslation();
  const { user, setUser } = useAuth();
  const [form] = Form.useForm<ProfileForm>();
  const [avatarFile, setAvatarFile] = useState<File | null>(null);
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [deleting, setDeleting] = useState(false);

  useEffect(() => {
    if (!open || !user) return;
    form.setFieldsValue({ name: user.name, email: user.email });
    setAvatarFile(null);
    setPreviewUrl(null);
  }, [form, open, user]);

  useEffect(() => () => {
    if (previewUrl) URL.revokeObjectURL(previewUrl);
  }, [previewUrl]);

  const selectAvatar = (file: File) => {
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

  const save = async (values: ProfileForm) => {
    setSaving(true);
    try {
      let updated = await profileApi.update(values.name);
      if (avatarFile) updated = await profileApi.uploadAvatar(avatarFile);
      setUser(updated);
      message.success(t('profile.message.profileUpdated'));
      onClose();
    } catch (error) {
      message.error(error instanceof Error ? error.message : t('profile.message.profileUpdateFailed'));
    } finally {
      setSaving(false);
    }
  };

  const deleteAvatar = async () => {
    setDeleting(true);
    try {
      const updated = await profileApi.deleteAvatar();
      setUser(updated);
      setAvatarFile(null);
      setPreviewUrl(null);
      message.success(t('profile.message.avatarRemoved'));
    } catch (error) {
      message.error(error instanceof Error ? error.message : t('profile.message.avatarRemoveFailed'));
    } finally {
      setDeleting(false);
    }
  };

  return (
    <Modal
      title={t('profile.title')}
      open={open}
      onCancel={onClose}
      onOk={() => form.submit()}
      okText={t('profile.okText')}
      confirmLoading={saving}
      destroyOnHidden
    >
      <div className="oa-profile-avatar-editor">
        <Avatar size={80} src={previewUrl || user?.avatarUrl} icon={<OaIcon name="avatar" size={32} />} />
        <Space>
          <Upload
            accept=".jpg,.jpeg,.png,.webp"
            maxCount={1}
            showUploadList={false}
            beforeUpload={selectAvatar}
          >
            <Button icon={<OaIcon name="upload" />}>{t('profile.avatar.select')}</Button>
          </Upload>
          {(user?.avatarUrl || avatarFile) && (
            <Button
              danger
              icon={<OaIcon name="delete" />}
              loading={deleting}
              onClick={() => void deleteAvatar()}
            >
              {t('profile.avatar.remove')}
            </Button>
          )}
        </Space>
        <span>{t('profile.avatar.hint')}</span>
      </div>
      <Form form={form} layout="vertical" onFinish={save}>
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
    </Modal>
  );
}
