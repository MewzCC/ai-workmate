'use client';

import { useState } from 'react';
import { Button, Card, Drawer, Radio, Slider, Space, Switch, Typography, Upload, message } from 'antd';
import type { UploadProps } from 'antd';
import { DeleteOutlined, UploadOutlined } from '@ant-design/icons';
import type { OaTheme } from '@/types/oa';

interface AppearanceDrawerProps {
  open: boolean;
  themes: OaTheme[];
  currentTheme: string;
  aiMiniEnabled: boolean;
  wallpaperOpacity: number;
  wallpaperBlur: number;
  onClose: () => void;
  onThemeChange: (themeName: string) => void;
  onAiMiniChange: (enabled: boolean) => void;
  onWallpaperChange: (url: string | null) => void;
  onWallpaperOpacityChange: (value: number) => void;
  onWallpaperBlurChange: (value: number) => void;
}

export default function AppearanceDrawer({
  open,
  themes,
  currentTheme,
  aiMiniEnabled,
  wallpaperOpacity,
  wallpaperBlur,
  onClose,
  onThemeChange,
  onAiMiniChange,
  onWallpaperChange,
  onWallpaperOpacityChange,
  onWallpaperBlurChange,
}: AppearanceDrawerProps) {
  const [saving, setSaving] = useState(false);

  const uploadProps: UploadProps = {
    accept: 'image/*',
    showUploadList: false,
    beforeUpload: (file) => {
      const reader = new FileReader();
      reader.onload = () => {
        onWallpaperChange(String(reader.result));
        message.success('壁纸已本地预览，不会上传到后端');
      };
      reader.readAsDataURL(file);
      return false;
    },
  };

  return (
    <Drawer title="外观设置" width={420} open={open} onClose={onClose}>
      <Space direction="vertical" size={20} className="oa-drawer-stack">
        <section>
          <Typography.Title level={5}>皮肤选择</Typography.Title>
          <Radio.Group value={currentTheme} onChange={(event) => onThemeChange(event.target.value)}>
            <Space direction="vertical" className="oa-theme-list">
              {themes.map((theme) => (
                <Card key={theme.name} size="small" className="oa-theme-option">
                  <Radio value={theme.name}>
                    <Space>
                      <span className="oa-theme-swatch" style={{ background: theme.primary }} />
                      {theme.label}
                    </Space>
                  </Radio>
                </Card>
              ))}
            </Space>
          </Radio.Group>
        </section>

        <section>
          <Typography.Title level={5}>AI 小悬浮窗</Typography.Title>
          <Switch checked={aiMiniEnabled} onChange={onAiMiniChange} checkedChildren="开启" unCheckedChildren="关闭" />
          <Typography.Paragraph type="secondary">默认关闭。开启后会在工作台右下角显示轻量 AI 快捷卡片，主入口仍保留 FloatButton。</Typography.Paragraph>
        </section>

        <section>
          <Typography.Title level={5}>壁纸上传</Typography.Title>
          <Space wrap>
            <Upload {...uploadProps}>
              <Button icon={<UploadOutlined />}>上传壁纸</Button>
            </Upload>
            <Button icon={<DeleteOutlined />} onClick={() => onWallpaperChange(null)}>
              清除壁纸
            </Button>
          </Space>
        </section>

        <section>
          <Typography.Text>壁纸透明度</Typography.Text>
          <Slider min={0.1} max={0.8} step={0.05} value={wallpaperOpacity} onChange={onWallpaperOpacityChange} />
          <Typography.Text>壁纸模糊度</Typography.Text>
          <Slider min={0} max={18} value={wallpaperBlur} onChange={onWallpaperBlurChange} />
        </section>

        <Button
          type="primary"
          loading={saving}
          onClick={() => {
            setSaving(true);
            window.setTimeout(() => {
              setSaving(false);
              message.success('外观配置已保存到 localStorage');
              onClose();
            }, 450);
          }}
        >
          保存配置
        </Button>
      </Space>
    </Drawer>
  );
}
