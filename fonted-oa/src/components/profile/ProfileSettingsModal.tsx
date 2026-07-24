'use client';

import { useEffect, useState } from 'react';
import { DeleteOutlined, UploadOutlined, UserOutlined } from '@ant-design/icons';
import { Avatar, Button, Form, Input, Modal, Space, Upload, message } from 'antd';
import { useAuth } from '@/components/auth/AuthProvider';
import { profileApi } from '@/lib/profileApi';

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
      message.error('头像仅支持 JPG、PNG 或 WebP');
      return Upload.LIST_IGNORE;
    }
    if (file.size > AVATAR_MAX_BYTES) {
      message.error('头像大小不能超过 2MB');
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
      message.success('个人资料已更新');
      onClose();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '个人资料更新失败');
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
      message.success('头像已移除');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '头像移除失败');
    } finally {
      setDeleting(false);
    }
  };

  return (
    <Modal
      title="个人设置"
      open={open}
      onCancel={onClose}
      onOk={() => form.submit()}
      okText="保存资料"
      confirmLoading={saving}
      destroyOnClose
    >
      <div className="oa-profile-avatar-editor">
        <Avatar size={80} src={previewUrl || user?.avatarUrl} icon={<UserOutlined />} />
        <Space>
          <Upload
            accept=".jpg,.jpeg,.png,.webp"
            maxCount={1}
            showUploadList={false}
            beforeUpload={selectAvatar}
          >
            <Button icon={<UploadOutlined />}>选择头像</Button>
          </Upload>
          {(user?.avatarUrl || avatarFile) && (
            <Button
              danger
              icon={<DeleteOutlined />}
              loading={deleting}
              onClick={() => void deleteAvatar()}
            >
              移除
            </Button>
          )}
        </Space>
        <span>支持 JPG、PNG、WebP，最大 2MB</span>
      </div>
      <Form form={form} layout="vertical" onFinish={save}>
        <Form.Item
          name="name"
          label="姓名"
          rules={[
            { required: true, message: '请输入姓名' },
            { max: 50, message: '姓名不能超过 50 个字符' },
          ]}
        >
          <Input placeholder="请输入姓名" />
        </Form.Item>
        <Form.Item name="email" label="企业邮箱">
          <Input disabled />
        </Form.Item>
      </Form>
    </Modal>
  );
}
