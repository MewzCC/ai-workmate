# Internationalization (i18n) Rules

本文件定义 AI WorkMate 全栈国际化规范。任何 AI Agent 或开发者在新增可见文案、错误消息、提示语时，必须先遵循本文件，避免重新引入硬编码。

## 1. 目的与范围

- 消除前端组件、页面与后端响应中的硬编码可见文案。
- 支持多语言切换，默认中文（`zh-CN`），第二语言英文（`en-US`）。
- 覆盖三个应用：`fronted-main`（营销官网）、`fonted-oa`（OA 工作台）、`backend`（Spring Boot 后端）。
- 新增语言时只需新增资源文件，不得修改业务组件代码。

## 2. 支持语言与 locale 标识

| 语言     | locale 标识 | 说明                          |
| -------- | ----------- | ----------------------------- |
| 简体中文 | `zh-CN`     | 默认语言，回退语言            |
| 英文     | `en-US`     | 第二语言                      |

- 统一使用 `zh-CN` / `en-US` 两个标识，禁止混用 `zh`、`zh_CN`、`en`、`en_US`。
- locale 标识同时用于：前端 i18next 资源、Ant Design `ConfigProvider.locale`、后端 `MessageSource` 文件名、`Accept-Language` 请求头。
- 当请求的语言资源缺失时，统一回退到 `zh-CN`。

## 3. 技术选型

### 前端

- `i18next`：核心引擎。
- `react-i18next`：React 绑定，提供 `useTranslation`、`Trans`、`I18nextProvider`。
- `i18next-browser-languagedetector`：自动检测浏览器语言。
- Ant Design `ConfigProvider` 的 `locale` 属性：同步内置组件（DatePicker、Pagination、Table、Modal 等）语言。
- 不得引入 `react-intl`、`vue-i18n` 或自研翻译方案。

### 后端

- Spring `MessageSource`（`ResourceBundleMessageSource`）：加载 `messages_*.properties`。
- `LocaleResolver`：基于 `Accept-Language` 请求头解析 locale。
- `ErrorCode` 枚举只保留 message key，不再承载最终中文文案。

## 4. 目录结构

### fronted-main

```
fronted-main/src/i18n/
├── index.ts                 # i18next 初始化配置
├── I18nProvider.tsx         # 包装 I18nextProvider + 语言切换上下文
├── locales/
│   ├── zh-CN.ts             # 中文资源（默认）
│   └── en-US.ts             # 英文资源
└── useLocale.ts             # 读取/切换语言、持久化、同步 antd locale
```

### fonted-oa

```
fonted-oa/src/i18n/
├── index.ts
├── I18nProvider.tsx         # 包装 I18nextProvider + antd ConfigProvider locale
├── locales/
│   ├── zh-CN.ts
│   └── en-US.ts
└── useLocale.ts
```

### backend

```
backend/src/main/resources/i18n/
├── messages_zh_CN.properties
├── messages_en_US.properties
└── messages.properties      # 回退（等同 zh-CN）
```

## 5. 资源文件命名与 key 规范

### 5.1 前端 namespace 划分

每个前端按业务域划分 namespace，避免单个文件膨胀：

| namespace   | 覆盖范围                                       |
| ----------- | ---------------------------------------------- |
| `common`    | 通用按钮、状态、空态、确认/取消、加载提示      |
| `home`      | 营销官网首页、Hero、特性、CTA（仅 fronted-main）|
| `auth`      | 登录、注册、验证码、找回密码                   |
| `chat`      | 聊天界面、消息、流式提示                       |
| `oa`        | OA 顶部栏、侧栏、标签、外观 Drawer、AI Drawer  |
| `dashboard` | OA 驾驶舱、ECharts 卡片标题                    |
| `access`    | 权限管理页面                                   |
| `knowledge` | 知识库页面                                     |
| `approval`  | 审批、请假流程                                 |
| `profile`   | 用户资料、头像、壁纸                           |
| `errors`    | 前端展示的错误提示文案                         |

- key 使用小写驼峰，按 `namespace.section.key` 组织，例如 `oa.topbar.title`、`dashboard.cards.revenue`。
- 资源文件按 namespace 顶级键聚合，例如：

```ts
// fronted-main/src/i18n/locales/zh-CN.ts
export default {
  common: {
    confirm: '确认',
    cancel: '取消',
    save: '保存',
    loading: '加载中…',
    empty: '暂无数据',
  },
  home: {
    hero: {
      title: '企业级 AI 助手与 OA 工作台',
      cta: '立即尝试',
    },
  },
  // ...
};
```

### 5.2 后端 message key

- 后端错误消息 key 统一前缀 `error.`，对应 `ErrorCode.errorCode` 的小写蛇形。
- 例如 `AUTH_REQUIRED` → `error.auth_required`。
- 参数化消息使用 `{0}`、`{1}` 占位符，由 `MessageSource.format` 注入。

```properties
# messages_zh_CN.properties
error.auth_required=请先登录
error.permission_denied=权限不足
error.resource_not_found=资源不存在
error.version_conflict=数据已被其他操作更新，请刷新后重试
```

```properties
# messages_en_US.properties
error.auth_required=Please sign in first
error.permission_denied=Permission denied
error.resource_not_found=Resource not found
error.version_conflict=Data was updated by another operation, please refresh and retry
```

## 6. 禁止硬编码规则

1. **可见文案禁止硬编码**：任何会渲染到 DOM、弹窗、message、notification、tooltip、placeholder、alt、aria-label 的中英文文本，必须通过 `t('key')` 读取。
2. **错误消息禁止硬编码**：后端 `Result.error(...)`、`BusinessException`、校验注解 `message` 必须使用 message key 或 MessageSource 解析；禁止在 controller/service 直接拼中文。
3. **例外允许硬编码**：
   - 代码注释、变量名、日志（日志仍需低敏，不含敏感信息）。
   - 纯技术标识符：路由 path、localStorage key、API path、CSS 类名、事件名。
   - 数据库种子数据中的业务名称（如默认菜单名）由后端接口返回，前端按返回值渲染。
   - **OA 菜单例外**：侧边栏菜单名虽来自后端 `rbac_route.name`，但为支持语言切换，前端用 `routeKey` 作为 i18n key 翻译（`t(\`oa.menu.${routeKey}\`, { defaultValue: route.name })`）。详见第 13 节。
4. **禁止字符串拼接构造文案**：禁止 `'共 ' + total + ' 条'`，必须使用插值 `t('common.total', { total })`。
5. **禁止内联中文 JSX**：禁止 `<Button>保存</Button>`，必须 `<Button>{t('common.save')}</Button>`。
6. 校验注解的 `message` 必须填 `{javax.validation.constraint}key` 形式或国际化 key，不得直接写中文。

## 7. 前端语言检测与持久化

- 检测优先级（由 `i18next-browser-languagedetector` 配置）：
  1. localStorage `workmeta-locale`
  2. 浏览器 `navigator.language`
  3. 回退 `zh-CN`
- 语言切换通过 `useLocale().changeLanguage(locale)` 完成，需同步：
  1. i18next 当前语言
  2. localStorage `workmeta-locale`
  3. `document.documentElement.lang`
  4. Ant Design `ConfigProvider.locale`（OA）
- localStorage key 统一为 `workmeta-locale`，两个前端共用，跨应用切换时保持一致。
- 不支持的 locale 必须回退到 `zh-CN`，不得报错或白屏。

## 8. Ant Design locale 同步（OA）

- `fonted-oa` 的 `I18nProvider` 必须同时包裹 `I18nextProvider` 与 `ConfigProvider`，根据当前 locale 传入 `zhCN` 或 `enUS`（来自 `antd/locale/zh_CN` 与 `antd/locale/en_US`）。
- 切换语言时，Ant Design 内置组件（DatePicker、Pagination、Table、Modal.confirm、Notification）必须同步切换。
- 通过 `App.useApp()` 创建的静态方法（`modal`、`notification`、`message`）同样继承 `ConfigProvider.locale`，禁止绕过。
- ECharts 图表中的中文（标题、图例、单位）必须走 `t()`，不得随 Ant Design locale 自动切换。

## 9. 后端国际化

### 9.1 MessageSource 配置

- 在 `backend/src/main/java/com/aiworkmate/config/` 新增 `MessageSourceConfig.java`，注册 `ResourceBundleMessageSource`：
  - `basename = i18n/messages`
  - `defaultEncoding = UTF-8`
  - `fallbackToSystemLocale = false`
- 配置 `LocaleResolver`（`AcceptHeaderLocaleResolver`），支持 `zh-CN`、`en-US`，默认 `zh-CN`。

### 9.2 ErrorCode 改造

- `ErrorCode` 枚举保留 `code`、`errorCode`、`status`，移除 `defaultMessage` 字段，改为 `messageKey` 字段。
- `messageKey` 取值如 `error.AUTH_REQUIRED`（与 `ErrorCode.errorCode` 对应），与 `messages_*.properties` 的 key 对齐。
- `ErrorCode` 不再直接返回中文，最终文案由 `MessageUtils.resolve(errorCode.getMessageKey())` 按 locale 解析。
- 新增 `ErrorCode` 时必须同步在 `messages.properties`、`messages_zh_CN.properties`、`messages_en_US.properties` 三个文件中添加对应 key，缺一不可。

### 9.3 异常处理改造

- `I18nConfig` 同时注册 `MessageSource` 与 `LocalValidatorFactoryBean`，后者通过 `setValidationMessageSource(messageSource)` 让 Bean Validation 的 `{validation.xxx}` 占位符走主 MessageSource，而非独立的 `ValidationMessages.properties`。
- `GlobalExceptionHandler` 注入 `MessageSource`，按 `LocaleContextHolder.getLocale()` 解析校验消息。
- `BusinessException.getMessage()` 内部调用 `MessageUtils.resolve(messageKey)` 按 locale 解析；`BusinessException(ErrorCode, String)` 的第二个参数为 message key（符合 `error.` / `validation.` 命名前缀时走 MessageSource，否则视为已解析文案向后兼容）。
- `Result.error(ErrorCode)` 内部调用 `MessageUtils.resolve(errorCode.getMessageKey())`，禁止在 `Result` 中直接读中文常量。
- `@Valid` 校验注解的 `message` 必须使用 `{validation.xxx.key}` 占位语法，由 `LocalValidatorFactoryBean` 解析；key 形如 `validation.password.length`、`validation.kb.name.notBlank`。
- SSE 流中的错误事件（`event: error`）同样通过 `MessageUtils.resolve()` 按当前 locale 输出可读消息，禁止只抛中文。

### 9.4 Accept-Language 约定

- 前端请求统一在请求头携带 `Accept-Language: zh-CN` 或 `en-US`，与当前 i18next 语言一致。
- 后端未收到 `Accept-Language` 时回退 `zh-CN`。
- 后端只接受上述两个 locale，其他值一律回退，不抛异常。

## 10. 翻译质量

- 英文翻译使用地道、简洁的产品英文，不得机器直译。
- 业务术语统一术语表（首次出现时在本文档补充）：
  - 工作台 → Workspace
  - 驾驶舱 → Dashboard
  - 审批 → Approval
  - 知识库 → Knowledge Base
  - 权限 → Permission
  - 外观 → Appearance
- 同一概念在不同 namespace 中使用相同译文，避免“保存/存储”混用。
- 占位符名称使用语义化变量名：`{name}`、`{total}`，禁止 `{0}` 出现在前端资源。

## 11. 验证要求

- 前端：切换到 `en-US` 后，页面无中文残留、无 key 暴露、无白屏；`npm run lint` 与 `npm run build` 通过。
- 后端：携带不同 `Accept-Language` 请求时，错误响应 `message` 字段语言正确；`mvn test` 通过。
- 新增可见文案时，必须同步更新 `zh-CN` 与 `en-US` 两个资源文件，缺一不可。
- 任何新增组件/页面 PR 不得引入新的硬编码可见文案。

## 12. 迁移与回归约束

- 全量迁移期间，已迁移组件不得再写入硬编码中文。
- mock 数据（`fonted-oa/src/mock/*`）中的中文业务名称属于演示数据，不强制迁移；但渲染 mock 数据的组件标签、表头、按钮文案必须迁移。
- 后端 `init.sql` 种子数据中的中文菜单名/角色名属于业务数据，不迁移；前端按接口返回值渲染。
- 迁移不得改变现有业务逻辑、路由、鉴权与主题行为。

## 13. OA 菜单与导航文案翻译

OA 侧边栏菜单名、顶部页面标签、路由切换提示虽来自后端 `GET /api/navigation` 返回的 `name`（数据库 `rbac_route.name`），但为支持语言切换，前端统一用 `routeKey` 作为 i18n key 翻译。

### 13.1 翻译方案

- i18n 资源位置：`fonted-oa/src/i18n/locales/{zh-CN,en-US}/oa.ts` 的 `menu` 命名空间。
- key 对应后端 `rbac_route.routeKey`，例如 `dashboard`、`ai-workspace`、`approval-list`。
- 翻译调用：`t(\`oa.menu.${routeKey}\`, { defaultValue: route.name })`，未配置 key 时回退到后端返回的 `name`。

### 13.2 必须翻译的位置

| 位置 | 文件 | 说明 |
| --- | --- | --- |
| 侧边栏菜单 label | `SidebarMenu.tsx` | `toMenuItems` 内 `label: t(\`oa.menu.${menu.id}\`, { defaultValue: menu.name })` |
| 顶部页面标签 | `PageTabBar.tsx` | tab `title`、`label`、下拉菜单 `label` |
| 浏览器标签页标题 | `AdminLayout.tsx` | `document.title` 拼接的 `selectedMenu.name` |
| 路由切换 message | `AdminLayout.tsx` | `oa.switchedTo`、`oa.switchingTo` 的 `name` 参数 |
| 传给子组件的 pageTitle | `AdminLayout.tsx` | `Topbar`、`Dashboard`、`AIOperationDrawer` 的 `pageTitle` prop |

### 13.3 新增菜单项时的约束

- 后端新增 `rbac_route` 记录时，前端必须同步在 `zh-CN/oa.ts` 和 `en-US/oa.ts` 的 `menu` 命名空间添加对应 `routeKey` 的翻译，缺一不可。
- 若未添加翻译 key，前端会回退到后端 `name`（通常是中文），切换 `en-US` 时该菜单项仍显示中文。
- 侧边栏品牌名与副标题用 `oa.sidebar.brand`、`oa.sidebar.brandSub`，不得在 `SidebarMenu.tsx` 硬编码 `WorkMate OA` 或 `Enterprise Console`。
