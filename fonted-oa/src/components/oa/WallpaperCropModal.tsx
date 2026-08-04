'use client';

import { useEffect, useState } from 'react';
import { Button, Modal, Segmented, Slider, Space, Typography } from 'antd';
import { RedoOutlined, RotateLeftOutlined, RotateRightOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import Cropper, { type Area, type Point } from 'react-easy-crop';

const ASPECT_OPTIONS = [
  { label: '16:9', value: 16 / 9 },
  { label: '4:3', value: 4 / 3 },
  { label: '1:1', value: 1 },
];

interface WallpaperCropModalProps {
  open: boolean;
  source: string | null;
  confirming: boolean;
  onCancel: () => void;
  onConfirm: (crop: Area, rotation: number) => void;
}

export default function WallpaperCropModal({
  open,
  source,
  confirming,
  onCancel,
  onConfirm,
}: WallpaperCropModalProps) {
  const { t } = useTranslation();
  const [crop, setCrop] = useState<Point>({ x: 0, y: 0 });
  const [zoom, setZoom] = useState(1);
  const [rotation, setRotation] = useState(0);
  const [aspect, setAspect] = useState(16 / 9);
  const [croppedArea, setCroppedArea] = useState<Area | null>(null);

  const reset = () => {
    setCrop({ x: 0, y: 0 });
    setZoom(1);
    setRotation(0);
    setAspect(16 / 9);
  };

  useEffect(() => {
    if (!open) return;
    setCrop({ x: 0, y: 0 });
    setZoom(1);
    setRotation(0);
    setAspect(16 / 9);
    setCroppedArea(null);
  }, [open, source]);

  return (
    <Modal
      title={t('profile.crop.title')}
      width={880}
      open={open}
      destroyOnHidden
      mask={{ closable: !confirming }}
      closable={!confirming}
      onCancel={onCancel}
      footer={[
        <Button key="cancel" disabled={confirming} onClick={onCancel}>{t('common.cancel')}</Button>,
        <Button
          key="confirm"
          type="primary"
          loading={confirming}
          disabled={!croppedArea}
          onClick={() => croppedArea && onConfirm(croppedArea, rotation)}
        >
          {t('profile.crop.apply')}
        </Button>,
      ]}
    >
      <Typography.Paragraph type="secondary">
        {t('profile.crop.hint')}
      </Typography.Paragraph>
      <div className="oa-wallpaper-crop-stage">
        {source && (
          <Cropper
            image={source}
            crop={crop}
            zoom={zoom}
            rotation={rotation}
            aspect={aspect}
            showGrid
            onCropChange={setCrop}
            onZoomChange={setZoom}
            onCropComplete={(_, pixels) => setCroppedArea(pixels)}
          />
        )}
      </div>
      <div className="oa-wallpaper-crop-controls">
        <div>
          <Typography.Text>{t('profile.crop.ratio')}</Typography.Text>
          <Segmented
            block
            options={ASPECT_OPTIONS}
            value={aspect}
            onChange={(value) => setAspect(Number(value))}
          />
        </div>
        <div>
          <Typography.Text>{t('profile.crop.size')}</Typography.Text>
          <Slider min={1} max={3} step={0.01} value={zoom} onChange={setZoom} />
        </div>
        <div>
          <Typography.Text>{t('profile.crop.rotation')}</Typography.Text>
          <Slider min={-180} max={180} step={1} value={rotation} onChange={setRotation} />
        </div>
        <Space wrap>
          <Button icon={<RotateLeftOutlined />} onClick={() => setRotation((value) => Math.max(-180, value - 90))}>{t('profile.crop.rotateLeft')}</Button>
          <Button icon={<RotateRightOutlined />} onClick={() => setRotation((value) => Math.min(180, value + 90))}>{t('profile.crop.rotateRight')}</Button>
          <Button icon={<RedoOutlined />} onClick={reset}>{t('common.reset')}</Button>
        </Space>
      </div>
    </Modal>
  );
}
