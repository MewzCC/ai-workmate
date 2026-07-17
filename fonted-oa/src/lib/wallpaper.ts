import type { Area } from 'react-easy-crop';

const MAX_STORAGE_LENGTH = 3_800_000;
const PRIMARY_WIDTH = 2560;
const PRIMARY_HEIGHT = 1440;

export function createWallpaperSource(file: File): string {
  if (!file.type.startsWith('image/')) {
    throw new Error('请选择图片文件');
  }
  return URL.createObjectURL(file);
}

export function releaseWallpaperSource(source: string | null): void {
  if (source?.startsWith('blob:')) URL.revokeObjectURL(source);
}

export async function createCroppedWallpaper(
  source: string,
  crop: Area,
  rotation: number,
): Promise<string> {
  const image = await loadImage(source);
  const rotatedCanvas = renderRotatedImage(image, rotation);
  const primary = renderCrop(rotatedCanvas, crop, PRIMARY_WIDTH, PRIMARY_HEIGHT, 0.84);
  if (primary.length <= MAX_STORAGE_LENGTH) return primary;

  const compact = renderCrop(rotatedCanvas, crop, 1920, 1080, 0.72);
  if (compact.length > MAX_STORAGE_LENGTH) {
    throw new Error('裁剪后的图片仍然过大，请缩小裁剪范围或选择尺寸更小的图片');
  }
  return compact;
}

function loadImage(source: string): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const image = new Image();
    image.onload = () => resolve(image);
    image.onerror = () => reject(new Error('无法读取该图片'));
    image.src = source;
  });
}

function renderRotatedImage(image: HTMLImageElement, rotation: number): HTMLCanvasElement {
  const radians = rotation * Math.PI / 180;
  const width = Math.abs(Math.cos(radians) * image.naturalWidth)
    + Math.abs(Math.sin(radians) * image.naturalHeight);
  const height = Math.abs(Math.sin(radians) * image.naturalWidth)
    + Math.abs(Math.cos(radians) * image.naturalHeight);
  const canvas = document.createElement('canvas');
  canvas.width = Math.max(1, Math.round(width));
  canvas.height = Math.max(1, Math.round(height));

  const context = getCanvasContext(canvas);
  context.translate(canvas.width / 2, canvas.height / 2);
  context.rotate(radians);
  context.drawImage(image, -image.naturalWidth / 2, -image.naturalHeight / 2);
  return canvas;
}

function renderCrop(
  source: HTMLCanvasElement,
  crop: Area,
  maxWidth: number,
  maxHeight: number,
  quality: number,
): string {
  const scale = Math.min(1, maxWidth / crop.width, maxHeight / crop.height);
  const canvas = document.createElement('canvas');
  canvas.width = Math.max(1, Math.round(crop.width * scale));
  canvas.height = Math.max(1, Math.round(crop.height * scale));
  getCanvasContext(canvas).drawImage(
    source,
    crop.x,
    crop.y,
    crop.width,
    crop.height,
    0,
    0,
    canvas.width,
    canvas.height,
  );
  return canvas.toDataURL('image/webp', quality);
}

function getCanvasContext(canvas: HTMLCanvasElement): CanvasRenderingContext2D {
  const context = canvas.getContext('2d');
  if (!context) throw new Error('当前浏览器不支持壁纸裁剪');
  return context;
}
