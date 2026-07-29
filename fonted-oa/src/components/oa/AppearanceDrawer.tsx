'use client';

import { Fragment, useState } from 'react';
import { Button, Card, Drawer, Empty, Image, Radio, Slider, Space, Spin, Switch, Tag, Typography, Upload } from 'antd';
import { message } from '@/lib/antdMessage';
import type { UploadProps } from 'antd';
import type { Area } from 'react-easy-crop';
import type { OaTheme } from '@/types/oa';
import { createCroppedWallpaper, createWallpaperSource, releaseWallpaperSource } from '@/lib/wallpaper';
import { profileApi } from '@/lib/profileApi';
import WallpaperCropModal from './WallpaperCropModal';
import { OaIcon } from '@/components/OaIcon';

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
      const wallpaperFile = await fetch(result).then((response) => response.blob());
      const uploaded = await profileApi.uploadWallpaper(wallpaperFile);
      props.onWallpaperChange(uploaded.wallpaperUrl);
      window.localStorage.removeItem('workmeta-oa-wallpaper');
      closeCropper();
      message.success('壁纸已裁剪、压缩并保存到 MinIO');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '壁纸处理失败');
    } finally {
      setProcessingWallpaper(false);
    }
  };

  const clearWallpaper = async () => {
    setProcessingWallpaper(true);
    try {
      await profileApi.deleteWallpaper();
      window.localStorage.removeItem('workmeta-oa-wallpaper');
      props.onWallpaperChange(null);
      message.success('壁纸已从 MinIO 清除');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '壁纸清除失败');
    } finally {
      setProcessingWallpaper(false);
    }
  };

  return (
    <Fragment>
      <Drawer title="外观设置" size="default" styles={{ wrapper: { width: 420 } }} open={props.open} onClose={props.onClose}>
        <Space orientation="vertical" size={20} className="oa-drawer-stack">
          <section>
            <Typography.Title level={5}>皮肤选择</Typography.Title>
            <Radio.Group value={props.currentTheme} onChange={(event) => props.onThemeChange(event.target.value)}>
              <Space orientation="vertical" className="oa-theme-list">
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
              图片会先在浏览器中裁剪压缩，再安全上传到 MinIO，并跟随当前账号同步。
            </Typography.Paragraph>
            <div className="oa-wallpaper-preview" aria-live="polite">
              <Spin spinning={processingWallpaper} description="正在处理壁纸">
                {props.wallpaper ? (
                  <Image src={props.wallpaper} alt="当前壁纸预览" width="100%" preview={{ mask: '查看大图' }} />
                ) : (
                  <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="上传并裁剪后将在这里预览" />
                )}
              </Spin>
            </div>
            <Space wrap>
              <Upload {...uploadProps}>
                <Button icon={<OaIcon name="upload" />} disabled={processingWallpaper}>上传并裁剪</Button>
              </Upload>
              <Button
                icon={<OaIcon name="edit" />}
                disabled={!props.wallpaper || processingWallpaper}
                onClick={() => setCropSource(props.wallpaper)}
              >
                重新裁剪
              </Button>
              <Button
                icon={<OaIcon name="delete" />}
                disabled={!props.wallpaper || processingWallpaper}
                onClick={() => void clearWallpaper()}
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
                message.success('外观配置已保存');
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
