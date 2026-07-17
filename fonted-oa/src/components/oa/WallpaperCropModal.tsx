'use client';

import { useEffect, useState } from 'react';
import { Button, Modal, Segmented, Slider, Space, Typography } from 'antd';
import { RedoOutlined, RotateLeftOutlined, RotateRightOutlined } from '@ant-design/icons';
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
      title="裁剪壁纸"
      width={880}
      open={open}
      destroyOnHidden
      maskClosable={!confirming}
      closable={!confirming}
      onCancel={onCancel}
      footer={[
        <Button key="cancel" disabled={confirming} onClick={onCancel}>取消</Button>,
        <Button
          key="confirm"
          type="primary"
          loading={confirming}
          disabled={!croppedArea}
          onClick={() => croppedArea && onConfirm(croppedArea, rotation)}
        >
          应用裁剪
        </Button>,
      ]}
    >
      <Typography.Paragraph type="secondary">
        拖动图片调整位置，通过下方控件调整大小、方向和裁剪比例。
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
          <Typography.Text>裁剪比例</Typography.Text>
          <Segmented
            block
            options={ASPECT_OPTIONS}
            value={aspect}
            onChange={(value) => setAspect(Number(value))}
          />
        </div>
        <div>
          <Typography.Text>图片大小</Typography.Text>
          <Slider min={1} max={3} step={0.01} value={zoom} onChange={setZoom} />
        </div>
        <div>
          <Typography.Text>旋转角度</Typography.Text>
          <Slider min={-180} max={180} step={1} value={rotation} onChange={setRotation} />
        </div>
        <Space wrap>
          <Button icon={<RotateLeftOutlined />} onClick={() => setRotation((value) => Math.max(-180, value - 90))}>向左旋转</Button>
          <Button icon={<RotateRightOutlined />} onClick={() => setRotation((value) => Math.min(180, value + 90))}>向右旋转</Button>
          <Button icon={<RedoOutlined />} onClick={reset}>重置</Button>
        </Space>
      </div>
    </Modal>
  );
}
