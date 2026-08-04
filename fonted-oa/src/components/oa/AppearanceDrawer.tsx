'use client';

import { Fragment, useState } from 'react';
import { useTranslation } from 'react-i18next';
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
  const { t } = useTranslation();
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
        message.error(error instanceof Error ? error.message : t('oa.appearance.readImageFailed'));
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
      message.success(t('oa.appearance.wallpaperProcessed'));
    } catch (error) {
      message.error(error instanceof Error ? error.message : t('oa.appearance.wallpaperProcessFailed'));
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
      message.success(t('oa.appearance.wallpaperCleared'));
    } catch (error) {
      message.error(error instanceof Error ? error.message : t('oa.appearance.wallpaperClearFailed'));
    } finally {
      setProcessingWallpaper(false);
    }
  };

  return (
    <Fragment>
      <Drawer title={t('oa.appearance.title')} size="default" styles={{ wrapper: { width: 420 } }} open={props.open} onClose={props.onClose}>
        <Space orientation="vertical" size={20} className="oa-drawer-stack">
          <section>
            <Typography.Title level={5}>{t('oa.appearance.themeSection')}</Typography.Title>
            <Radio.Group value={props.currentTheme} onChange={(event) => props.onThemeChange(event.target.value)}>
              <Space orientation="vertical" className="oa-theme-list">
                {props.themes.map((theme) => (
                  <Card key={theme.name} size="small" className="oa-theme-option">
                    <Radio value={theme.name}>
                      <Space><span className="oa-theme-swatch" style={{ background: theme.primary }} />{t(`oa.theme.${theme.name}`)}</Space>
                    </Radio>
                  </Card>
                ))}
              </Space>
            </Radio.Group>
          </section>

          <section>
            <Typography.Title level={5}>{t('oa.appearance.aiMiniSection')}</Typography.Title>
            <Switch checked={props.aiMiniEnabled} onChange={props.onAiMiniChange} checkedChildren={t('oa.appearance.aiMiniOn')} unCheckedChildren={t('oa.appearance.aiMiniOff')} />
            <Typography.Paragraph type="secondary">
              {t('oa.appearance.aiMiniDesc')}
            </Typography.Paragraph>
          </section>

          <section>
            <Space className="oa-wallpaper-heading">
              <Typography.Title level={5}>{t('oa.appearance.wallpaperSection')}</Typography.Title>
              {props.wallpaper && <Tag color="success">{t('oa.appearance.applied')}</Tag>}
            </Space>
            <Typography.Paragraph type="secondary">
              {t('oa.appearance.wallpaperDesc')}
            </Typography.Paragraph>
            <div className="oa-wallpaper-preview" aria-live="polite">
              <Spin spinning={processingWallpaper} description={t('oa.appearance.processingWallpaper')}>
                {props.wallpaper ? (
                  <Image src={props.wallpaper} alt={t('oa.appearance.wallpaperPreviewAlt')} width="100%" preview={{ mask: t('oa.appearance.viewLarge') }} />
                ) : (
                  <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('oa.appearance.emptyPreview')} />
                )}
              </Spin>
            </div>
            <Space wrap>
              <Upload {...uploadProps}>
                <Button icon={<OaIcon name="upload" />} disabled={processingWallpaper}>{t('oa.appearance.uploadCrop')}</Button>
              </Upload>
              <Button
                icon={<OaIcon name="edit" />}
                disabled={!props.wallpaper || processingWallpaper}
                onClick={() => setCropSource(props.wallpaper)}
              >
                {t('oa.appearance.recrop')}
              </Button>
              <Button
                icon={<OaIcon name="delete" />}
                disabled={!props.wallpaper || processingWallpaper}
                onClick={() => void clearWallpaper()}
              >
                {t('oa.appearance.clear')}
              </Button>
            </Space>
          </section>

          <section>
            <Typography.Text>{t('oa.appearance.wallpaperOpacity')}</Typography.Text>
            <Slider min={0.1} max={0.8} step={0.05} value={props.wallpaperOpacity} onChange={props.onWallpaperOpacityChange} />
            <Typography.Text>{t('oa.appearance.wallpaperBlur')}</Typography.Text>
            <Slider min={0} max={18} value={props.wallpaperBlur} onChange={props.onWallpaperBlurChange} />
          </section>

          <Button
            type="primary"
            loading={saving}
            onClick={() => {
              setSaving(true);
              window.setTimeout(() => {
                setSaving(false);
                message.success(t('oa.appearance.saveSuccess'));
                props.onClose();
              }, 450);
            }}
          >
            {t('oa.appearance.save')}
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
