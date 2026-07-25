# Iconfont Symbol 使用与更新教程

本项目使用阿里 Iconfont 的 **Symbol** 模式。营销官网 `fronted-main` 和 OA 工作台 `fonted-oa` 是两个独立的 Next.js 应用，因此两端需要分别保存一份 `iconfont.js`。

## 目录位置

```text
fronted-main/public/iconfont/iconfont.js
fonted-oa/public/iconfont/iconfont.js

fronted-main/src/components/IconFont.tsx
fonted-oa/src/components/IconFont.tsx
```

两个应用的根布局已经加载 `/iconfont/iconfont.js`，后续更新图标库时不需要再次修改 `layout.tsx`。

## 在组件中使用

```tsx
import { IconFont } from '@/components/IconFont';

export function Example() {
  return (
    <>
      <IconFont name="main_icon" size={24} />
      <IconFont name="yonghuguanli" size={20} />
      <IconFont name="PDF" size={32} />
    </>
  );
}
```

单色图标默认继承当前文字颜色：

```tsx
<IconFont
  name="daochu"
  size={18}
  style={{ color: '#1677ff' }}
/>
```

CSV、PDF、PNG、JPG 等多色图标会保留 Iconfont 中配置的原始颜色。

用于 Ant Design 按钮：

```tsx
import { Button } from 'antd';
import { IconFont } from '@/components/IconFont';

<Button icon={<IconFont name="daochu" size={16} />}>
  导出
</Button>
```

图标仅用于装饰时不需要设置标题。图标自身承载业务含义时，应提供 `title`：

```tsx
<IconFont name="add_oa" size={20} title="新增" />
```

## 更新 Iconfont 图标库

### 1. 下载新的 Symbol 包

在 Iconfont 项目中完成图标添加或修改后：

1. 切换到 `Symbol`。
2. 点击“下载至本地”。
3. 解压下载包。
4. 确认其中包含 `iconfont.js` 和 `iconfont.json`。

尽量不要修改已经在代码中使用的 `font_class`，否则旧调用会失效。

### 2. 替换两个应用的脚本

在仓库根目录执行：

```powershell
$source = "C:\你的下载目录\iconfont.js"

Copy-Item $source "fronted-main\public\iconfont\iconfont.js" -Force
Copy-Item $source "fonted-oa\public\iconfont\iconfont.js" -Force
```

Symbol 模式不依赖 `iconfont.css`、`.ttf`、`.woff` 或 `.woff2`。

### 3. 同步图标名称

打开新下载包中的 `iconfont.json`，复制每个图标的 `font_class`：

```json
{
  "font_class": "shezhi"
}
```

把名称同步加入两端 `IconFont.tsx` 的 `iconFontNames`：

```tsx
export const iconFontNames = [
  // 其他图标
  'shezhi',
] as const;
```

必须同步修改：

```text
fronted-main/src/components/IconFont.tsx
fonted-oa/src/components/IconFont.tsx
```

### 4. 处理删除或改名

删除或修改 `font_class` 前，先从仓库根目录搜索旧名称：

```powershell
rg "旧图标名称" fronted-main fonted-oa
```

把所有调用替换为新名称，然后再从两个 `iconFontNames` 清单中删除旧名称。

### 5. 验证

```powershell
cd fronted-main
npm run lint
npm run build

cd ../fonted-oa
npm run lint
npm run build
```

两端命令都通过后，更新才算完成。

## 常见问题

### 图标不显示

检查以下内容：

- `iconfont.js` 是否同时复制到了两个应用。
- `name` 是否等于 `iconfont.json` 中的 `font_class`。
- `IconFont.tsx` 的 `iconFontNames` 是否包含该名称。
- 当前访问的应用是否已经重新启动。

### 更新后仍显示旧图标

重启对应的 Next.js 开发服务，并在浏览器中执行强制刷新。

### TypeScript 提示名称不可用

说明新名称还没有加入当前应用 `IconFont.tsx` 的 `iconFontNames`，或者名称大小写与 `font_class` 不一致。

### OA 可以显示，但官网不显示

两个应用运行在不同端口，不能依赖另一个应用的静态资源。检查 `fronted-main/public/iconfont/iconfont.js` 是否已经更新；反向情况同理。
