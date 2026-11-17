'use client';

import { Fragment, useLayoutEffect, useRef, useState } from 'react';
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
import { useAuth } from '@/components/auth/AuthProvider';
import { useAsyncFormAction } from '@/hooks/useAsyncFormAction';
import { useAsyncActionGuard } from '@/hooks/useAsyncActionGuard';

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
  const { user } = useAuth();
  const scope = `${user?.tenantId}:${user?.id}:${props.open}`;
  const action = useAsyncFormAction(scope);
  const { capture } = useAsyncActionGuard(scope);
  const processingWallpaper = action.busy;
  const [cropSource, setCropSource] = useState<string | null>(null);
  const source = useRef(cropSource); source.current = cropSource;
  useLayoutEffect(() => {
    const ownedSource = source;
    setCropSource(null);
    return () => { releaseWallpaperSource(ownedSource.current); };
  }, [scope]);

  const closeCropper = () => {
    action.reset();
    releaseWallpaperSource(cropSource);
    setCropSource(null);
  };
  const closeDrawer = () => { closeCropper(); props.onClose(); };

  const uploadProps: UploadProps = {
    accept: 'image/*',
    showUploadList: false,
    beforeUpload: (file) => {
      if (!capture()() || !props.open || !user || action.busy) return Upload.LIST_IGNORE;
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
    if (!cropSource || !props.open || !user) return;
    const attempt = action.begin();
    if (!attempt) return;
    try {
      const result = await createCroppedWallpaper(cropSource, crop, rotation);
      if (!attempt.current()) return;
      const wallpaperFile = await fetch(result).then((response) => response.blob());
      if (!attempt.current()) return;
      const uploaded = await profileApi.uploadWallpaper(wallpaperFile);
      if (!attempt.current()) return;
      props.onWallpaperChange(uploaded.wallpaperUrl);
      window.localStorage.removeItem('workmeta-oa-wallpaper');
      closeCropper();
      message.success(t('oa.appearance.wallpaperProcessed'));
    } catch (error) {
      if (!attempt.current()) return;
      message.error(error instanceof Error ? error.message : t('oa.appearance.wallpaperProcessFailed'));
    } finally {
      attempt.finish();
    }
  };

  const clearWallpaper = async () => {
    if (!props.open || !user) return;
    const attempt = action.begin();
    if (!attempt) return;
    try {
      await profileApi.deleteWallpaper();
      if (!attempt.current()) return;
      window.localStorage.removeItem('workmeta-oa-wallpaper');
      props.onWallpaperChange(null);
      message.success(t('oa.appearance.wallpaperCleared'));
    } catch (error) {
      if (!attempt.current()) return;
      message.error(error instanceof Error ? error.message : t('oa.appearance.wallpaperClearFailed'));
    } finally {
      attempt.finish();
    }
  };

  return (
    <Fragment>
      <Drawer title={t('oa.appearance.title')} size="default" styles={{ wrapper: { width: 420 } }} open={props.open && Boolean(user)} onClose={closeDrawer}>
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
              <Upload {...uploadProps} disabled={processingWallpaper}>
                <Button icon={<OaIcon name="upload" />} disabled={processingWallpaper}>{t('oa.appearance.uploadCrop')}</Button>
              </Upload>
              <Button
                icon={<OaIcon name="edit" />}
                disabled={!props.wallpaper || processingWallpaper}
                onClick={() => { if (capture()()) { action.reset(); setCropSource(props.wallpaper); } }}
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
            onClick={closeDrawer}
          >
            {t('common.close')}
          </Button>
        </Space>
      </Drawer>
      <WallpaperCropModal
        open={Boolean(cropSource) && props.open && Boolean(user)}
        source={cropSource}
        confirming={processingWallpaper}
        onCancel={closeCropper}
        onConfirm={applyCrop}
      />
    </Fragment>
  );
}
