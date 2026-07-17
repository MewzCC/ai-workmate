'use client';

import { Fragment, useState } from 'react';
import { Button, Card, Drawer, Empty, Image, Radio, Slider, Space, Spin, Switch, Tag, Typography, Upload, message } from 'antd';
import type { UploadProps } from 'antd';
import { DeleteOutlined, EditOutlined, UploadOutlined } from '@ant-design/icons';
import type { Area } from 'react-easy-crop';
import type { OaTheme } from '@/types/oa';
import { createCroppedWallpaper, createWallpaperSource, releaseWallpaperSource } from '@/lib/wallpaper';
import WallpaperCropModal from './WallpaperCropModal';

const WALLPAPER_STORAGE_KEY = 'workmeta-oa-wallpaper';

interface AppearanceDrawerProps {
  open: boolean;
  themes: OaTheme[];
  currentTheme: string;
  aiMiniEnabled: boolean;
  wallpaper: string | null;
  wallpaperOpacity: number;
  wallpaperBlur: number;
  onClose: () => void;
  onThemeChange: (themeName: string) => void;
  onAiMiniChange: (enabled: boolean) => void;
  onWallpaperChange: (url: string | null) => void;
  onWallpaperOpacityChange: (value: number) => void;
  onWallpaperBlurChange: (value: number) => void;
}

export default function AppearanceDrawer(props: AppearanceDrawerProps) {
  const [saving, setSaving] = useState(false);
  const [processingWallpaper, setProcessingWallpaper] = useState(false);
  const [cropSource, setCropSource] = useState<string | null>(null);

  const closeCropper = () => {
    releaseWallpaperSource(cropSource);
    setCropSource(null);
  };

  const uploadProps: UploadProps = {
    accept: 'image/*',
    showUploadList: false,
    beforeUpload: (file) => {
      try {
        closeCropper();
        setCropSource(createWallpaperSource(file));
      } catch (error) {
        message.error(error instanceof Error ? error.message : '无法读取图片');
      }
      return false;
    },
  };

  const applyCrop = async (crop: Area, rotation: number) => {
    if (!cropSource) return;
    setProcessingWallpaper(true);
    try {
      const result = await createCroppedWallpaper(cropSource, crop, rotation);
      window.localStorage.setItem(WALLPAPER_STORAGE_KEY, result);
      props.onWallpaperChange(result);
      closeCropper();
      message.success('壁纸已裁剪、压缩并保存到本地');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '壁纸处理失败');
    } finally {
      setProcessingWallpaper(false);
    }
  };

  const clearWallpaper = () => {
    window.localStorage.removeItem(WALLPAPER_STORAGE_KEY);
    props.onWallpaperChange(null);
    message.success('壁纸已清除');
  };

  return (
    <Fragment>
      <Drawer title="外观设置" width={420} open={props.open} onClose={props.onClose}>
        <Space direction="vertical" size={20} className="oa-drawer-stack">
          <section>
            <Typography.Title level={5}>皮肤选择</Typography.Title>
            <Radio.Group value={props.currentTheme} onChange={(event) => props.onThemeChange(event.target.value)}>
              <Space direction="vertical" className="oa-theme-list">
                {props.themes.map((theme) => (
                  <Card key={theme.name} size="small" className="oa-theme-option">
                    <Radio value={theme.name}>
                      <Space><span className="oa-theme-swatch" style={{ background: theme.primary }} />{theme.label}</Space>
                    </Radio>
                  </Card>
                ))}
              </Space>
            </Radio.Group>
          </section>

          <section>
            <Typography.Title level={5}>AI 小悬浮窗</Typography.Title>
            <Switch checked={props.aiMiniEnabled} onChange={props.onAiMiniChange} checkedChildren="开启" unCheckedChildren="关闭" />
            <Typography.Paragraph type="secondary">
              默认关闭。开启后会在工作台右下角显示轻量 AI 快捷卡片，主入口仍保留 FloatButton。
            </Typography.Paragraph>
          </section>

          <section>
            <Space className="oa-wallpaper-heading">
              <Typography.Title level={5}>壁纸上传</Typography.Title>
              {props.wallpaper && <Tag color="success">已应用</Tag>}
            </Space>
            <Typography.Paragraph type="secondary">
              图片在浏览器中裁剪、压缩并保存，不会上传到服务器。
            </Typography.Paragraph>
            <div className="oa-wallpaper-preview" aria-live="polite">
              <Spin spinning={processingWallpaper} tip="正在处理壁纸">
                {props.wallpaper ? (
                  <Image src={props.wallpaper} alt="当前壁纸预览" width="100%" preview={{ mask: '查看大图' }} />
                ) : (
                  <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="上传并裁剪后将在这里预览" />
                )}
              </Spin>
            </div>
            <Space wrap>
              <Upload {...uploadProps}>
                <Button icon={<UploadOutlined />} disabled={processingWallpaper}>上传并裁剪</Button>
              </Upload>
              <Button
                icon={<EditOutlined />}
                disabled={!props.wallpaper || processingWallpaper}
                onClick={() => setCropSource(props.wallpaper)}
              >
                重新裁剪
              </Button>
              <Button
                icon={<DeleteOutlined />}
                disabled={!props.wallpaper || processingWallpaper}
                onClick={clearWallpaper}
              >
                清除壁纸
              </Button>
            </Space>
          </section>

          <section>
            <Typography.Text>壁纸透明度</Typography.Text>
            <Slider min={0.1} max={0.8} step={0.05} value={props.wallpaperOpacity} onChange={props.onWallpaperOpacityChange} />
            <Typography.Text>壁纸模糊度</Typography.Text>
            <Slider min={0} max={18} value={props.wallpaperBlur} onChange={props.onWallpaperBlurChange} />
          </section>

          <Button
            type="primary"
            loading={saving}
            onClick={() => {
              setSaving(true);
              window.setTimeout(() => {
                setSaving(false);
                message.success('外观配置已保存到 localStorage');
                props.onClose();
              }, 450);
            }}
          >
            保存配置
          </Button>
        </Space>
      </Drawer>
      <WallpaperCropModal
        open={Boolean(cropSource)}
        source={cropSource}
        confirming={processingWallpaper}
        onCancel={closeCropper}
        onConfirm={applyCrop}
      />
    </Fragment>
  );
}
