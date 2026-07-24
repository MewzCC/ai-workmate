# AI WorkMate 企业认证设计

## 目标与边界

OA 认证入口固定为 `fonted-oa` 的 `/auth`。所有 `/oa/**` 页面先调用 `GET /api/auth/me` 校验服务端签发的 HttpOnly Cookie；未认证用户携带 `redirect` 参数返回登录页。营销官网不再承载登录、注册或找回密码表单。

## 认证流程

### 密码登录

1. 用户提交企业邮箱和密码。
2. 服务端检查 `账号 + IP` 临时锁定状态。
3. 失败达到 3 次后，下一次提交必须先完成一次性图形验证码。
4. 失败达到 `LOGIN_MAX_FAILURES` 后锁定 `LOGIN_LOCK_SECONDS`。
5. 登录成功清理失败计数并写入 `HttpOnly + SameSite=Strict` Cookie。

### 邮箱验证码登录、注册、找回密码

1. 前端先请求 `GET /api/auth/captcha` 获取图形验证码。
2. 调用 `POST /api/auth/email-code/send` 时必须同时提交 `captchaId`、`captchaCode` 和业务 `scene`。
3. 图形验证码经 Redis Lua 原子校验后立即作废。
4. 邮箱验证码只允许用于对应 `register / login / reset_password` 场景，校验成功立即删除，连续输错达到上限后作废。
5. 注册使用 `requestId` 对重复提交进行 Redis 幂等保护。

## Redis Key

| Key | TTL | 用途 |
| --- | ---: | --- |
| `oa:captcha:image:{captchaId}` | `CAPTCHA_TTL` | 图形验证码哈希、创建时间、错误次数 |
| `oa:captcha:email:{scene}:{email}` | `EMAIL_CODE_TTL` | 邮箱验证码哈希与场景绑定 |
| `oa:captcha:cooldown:{scene}:{email}` | `EMAIL_CODE_COOLDOWN` | 同邮箱发送冷却 |
| `oa:captcha:hourly:{scene}:{email}:{yyyyMMddHH}` | 1 小时 | 每小时发送次数 |
| `oa:captcha:daily:{scene}:{email}:{yyyyMMdd}` | 2 天 | 每日发送次数 |
| `oa:captcha:ip:{scene}:{ipHash}` | 60 秒 | IP 每分钟发送次数 |
| `oa:login:fail:{account}:{ip}` | `LOGIN_LOCK_SECONDS` | 登录失败计数 |
| `oa:login:lock:{account}:{ip}` | `LOGIN_LOCK_SECONDS` | 登录临时锁定 |
| `oa:register:idempotent:{requestId}` | 10 分钟 | 注册防重复提交 |

验证码只保存加盐 SHA-256 哈希，不保存可直接读取的明文。Redis 不可用时返回 `AUTH_SERVICE_UNAVAILABLE`，不降级到本机内存。

## API

- `GET /api/auth/captcha`
- `POST /api/auth/email-code/send`
- `POST /api/auth/login/password`
- `POST /api/auth/login/email-code`
- `POST /api/auth/register`
- `POST /api/auth/password/reset`
- `POST /api/auth/logout`
- `GET /api/auth/me`

所有接口使用项目统一 `Result<T>`；认证失败为 401，权限不足为 403，限流为 429，锁定为 423，Redis/SMTP 不可用为 503。

## 数据库变更

`backend/src/main/resources/db/init.sql` 统一维护用户表兼容升级，包括 `display_name`、120 字符用户名和规范化邮箱唯一索引。已有数据库在启动新版本前执行同一文件。
