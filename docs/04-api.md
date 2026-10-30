# 04 · REST API 契约

> 前端与 Java 的唯一接口口径。字段名、枚举、状态码、错误码以本文为准。
> 领域语义见 [02-domain-model.md](02-domain-model.md)，表结构见 [03-database.md](03-database.md)。

## 1. 通用约定

| 项 | 约定 |
|---|---|
| Base URL | 所有业务接口位于 `/api` 下；健康检查为 `/actuator/health` |
| 传输 | HTTPS（本地 HTTP）；请求/响应 `application/json; charset=UTF-8`；上传为 `multipart/form-data` |
| 鉴权 | `Authorization: Bearer <accessToken>`；模板接口公开 |
| 时间 | ISO-8601 UTC，毫秒精度，如 `2026-10-28T08:30:00.000Z` |
| ID | 用户为 JSON number；任务、素材为字符串 UUID；模板为 slug 字符串 |
| 分页 | `page` 从 1 开始，默认 1；`size` 默认 20，最大 50 |
| 空值 | 无值字段返回 `null`，不省略字段；数组返回 `[]` |
| 兼容性 | 允许新增字段；客户端必须忽略未知字段；破坏性变更升级 `/api/v2` |
| 请求追踪 | 服务端生成 `X-Request-Id`，同时写入错误体 `traceId`；客户端可回传用于排障 |
| 语言 | 请求头 `Accept-Language`；支持 `zh-CN`（默认）与 `en-US`；响应头 `Content-Language` 与 `Vary: Accept-Language` |
| 缓存 | 任务列表与详情 `Cache-Control: no-store`；模板列表/详情 `public, max-age=60` 且 `Vary: Accept-Language`；图片按需 `private` 缓存 |

`NEXT_PUBLIC_BACKEND_URL` 指向 Java 服务（开发默认 `http://localhost:8080`）。API 返回的 `/api/...` 与 `/assets/...` 均为**相对路径**，前端统一拼接该地址。

### 1.1 语言协商

| 项 | 规则 |
|---|---|
| 支持语言 | `zh-CN`、`en-US` |
| 解析 | 读取 `Accept-Language`，支持 `en`、`en-US`、`zh`、`zh-CN` 与 `q` 权重；无法匹配时回退 `zh-CN` |
| 响应头 | 所有 JSON 响应带 `Content-Language` 与 `Vary: Accept-Language` |
| 本地化范围 | 模板标题/描述/分类名、`ProblemDetail.title/detail`、字段错误、任务失败原因、步骤消息、尝试错误消息 |
| 不本地化 | 错误码、枚举、字段名、任务模板快照中的兜底标题、结果图水印 |
| CORS | `Access-Control-Allow-Headers` 必须包含 `Accept-Language`、`Authorization`、`Content-Type`；`Access-Control-Expose-Headers` 暴露 `Content-Language`、`Content-Disposition`、`Idempotent-Replay`、`Location`、`Retry-After`、`X-Request-Id` |

示例：

```http
GET /api/templates HTTP/1.1
Accept-Language: en-US
```

```http
HTTP/1.1 200 OK
Content-Language: en-US
Vary: Accept-Language
```

## 2. 鉴权与会话

### 2.1 令牌模型

| 令牌 | 传输 | 有效期 | 存储 |
|---|---|---|---|
| 访问令牌 | 响应体 + `Authorization` 头 | 15 分钟 | 前端内存（Zustand），不写 localStorage |
| 刷新令牌 | HttpOnly Cookie `framelab_rt` | 7 天 | 服务端存 SHA-256 哈希；浏览器不可读 |

Cookie 属性：

```text
Set-Cookie: framelab_rt=<opaque>; Path=/api/auth; HttpOnly; SameSite=Lax; Max-Age=604800
```

- 生产环境必须追加 `Secure`；本地 HTTP 开发不追加。
- 刷新令牌不轮换；退出登录时撤销并清除 Cookie。
- 前端请求 `/api/auth/*` 时使用 `credentials: "include"`；其他请求不依赖 Cookie。
- 无 CSRF Token：业务接口靠 Bearer 头鉴权，不随 Cookie 自动携带；刷新与退出仅操作会话，不改变业务数据。前端禁止用 GET 执行任何写操作。
- 未登录访问受保护接口返回 401；已登录但角色不足返回 403；访问他人资源返回 404。

### 2.2 POST `/api/auth/login`

请求：

```json
{
  "email": "demo@framelab.local",
  "password": "Demo1234!"
}
```

响应 `200`：

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIs...",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "user": {
    "id": 1,
    "email": "demo@framelab.local",
    "displayName": "林小满",
    "role": "USER",
    "createdAt": "2026-10-01T02:00:00.000Z"
  }
}
```

并下发刷新 Cookie。

错误：

| 状态 | code | 场景 |
|---|---|---|
| 400 | `VALIDATION_FAILED` | 邮箱/密码为空或格式错误 |
| 401 | `INVALID_CREDENTIALS` | 邮箱不存在、密码错误、账号禁用（统一文案） |
| 429 | `RATE_LIMITED` | 同一邮箱+IP 15 分钟内失败超过 5 次；带 `Retry-After` |

### 2.3 POST `/api/auth/refresh`

- 无请求体；读取 `framelab_rt` Cookie。
- 成功 `200`：返回与登录相同的 `accessToken / tokenType / expiresIn / user`；不重新下发 Cookie。
- 失败 `401 REFRESH_TOKEN_INVALID`：Cookie 缺失、过期、已撤销或哈希不存在。
- 前端启动时调用一次；失败即进入匿名态，不弹错误提示。

### 2.4 POST `/api/auth/logout`

- 无请求体；即使访问令牌已过期也可调用。
- 撤销当前刷新令牌、清除 Cookie，返回 `204`。
- 不要求携带访问令牌；没有 Cookie 时同样返回 `204`（幂等）。

### 2.5 GET `/api/auth/me`

- 需要访问令牌。
- `200`：`{ "id": 1, "email": "...", "displayName": "...", "role": "USER", "createdAt": "..." }`
- `401 AUTH_REQUIRED / TOKEN_EXPIRED / TOKEN_INVALID`。

## 3. 错误模型

采用 RFC 9457 Problem Details 扩展，`Content-Type: application/problem+json`：

```json
{
  "type": "https://framelab.local/problems/validation-failed",
  "title": "请求参数不合法",
  "status": 400,
  "detail": "素材数量必须为 2 张",
  "instance": "/api/tasks",
  "code": "VALIDATION_FAILED",
  "traceId": "c1f0d8e2a7b34f18",
  "errors": [
    { "field": "assetIds", "message": "必须为 2 个素材" }
  ]
}
```

规则：

- `code` 是机器可读的稳定值，不随语言变化；`title` / `detail` 按 `Accept-Language` 本地化。
- `errors` 仅字段校验错误出现；无字段错误时省略；`errors[].message` 同样本地化。
- 不返回异常类名、堆栈、SQL、磁盘路径、内部地址。
- 5xx 统一 `INTERNAL_ERROR`，日志保留 `traceId` 与服务端堆栈。
- 响应头写 `Content-Language` 与 `Vary: Accept-Language`。

### 3.1 错误码表

| HTTP | code | 含义 / 典型场景 |
|---|---|---|
| 400 | `VALIDATION_FAILED` | 参数缺失、格式错误、数量不符、模拟配置非法 |
| 400 | `MALFORMED_REQUEST` | JSON 无法解析、类型不匹配 |
| 401 | `AUTH_REQUIRED` | 缺少 Authorization |
| 401 | `TOKEN_EXPIRED` | 访问令牌过期（前端应尝试刷新一次） |
| 401 | `TOKEN_INVALID` | 签名错误、签发者/受众不符、格式错误 |
| 401 | `INVALID_CREDENTIALS` | 登录失败 |
| 401 | `REFRESH_TOKEN_INVALID` | 刷新令牌无效/过期/撤销 |
| 403 | `FORBIDDEN` | 角色不足（如 USER 访问管理端） |
| 404 | `TEMPLATE_NOT_FOUND` | 模板不存在或已下架 |
| 404 | `ASSET_NOT_FOUND` | 素材不存在或不属于当前用户 |
| 404 | `TASK_NOT_FOUND` | 任务不存在或不属于当前用户 |
| 404 | `NOT_FOUND` | 其他路径不存在 |
| 405 | `METHOD_NOT_ALLOWED` | 方法不支持 |
| 409 | `ASSET_IN_USE` | 素材已被任务引用，不能删除 |
| 409 | `TASK_NOT_READY` | 任务未成功，结果不可读取 |
| 409 | `IDEMPOTENCY_KEY_REUSED` | 同一 `clientRequestId` 的载荷与已存在任务不一致 |
| 409 | `IDEMPOTENCY_CONFLICT` | 并发插入冲突后仍无法读取原任务（极罕见，需重试） |
| 413 | `FILE_TOO_LARGE` | 上传超过模板/全局上限 |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | 文件类型不是 JPEG/PNG 或请求 Content-Type 错误 |
| 422 | `INVALID_IMAGE` | 魔数不符、解码失败、像素超限 |
| 429 | `RATE_LIMITED` | 登录尝试过多 |
| 500 | `INTERNAL_ERROR` | 未预期异常 |
| 503 | `SERVICE_UNAVAILABLE` | 数据库/存储不可用、维护中 |

## 4. 数据结构

### 4.1 User

```json
{
  "id": 1,
  "email": "demo@framelab.local",
  "displayName": "林小满",
  "role": "USER",
  "createdAt": "2026-10-01T02:00:00.000Z"
}
```

### 4.2 TemplateSummary / TemplateDetail

```json
{
  "id": "travel-postcard",
  "title": "旅行明信片",
  "category": { "id": "travel", "name": "旅行风景" },
  "coverUrl": "/assets/templates/travel-postcard.webp",
  "description": "把两张旅途照片拼成一张可以寄出的明信片。",
  "requiredImageCount": 2,
  "canvas": { "width": 1600, "height": 1200 }
}
```

详情额外字段：

```json
{
  "acceptedMimeTypes": ["image/jpeg", "image/png"],
  "maxFileSizeBytes": 5242880
}
```

`title`、`description`、`category.name` 按 `Accept-Language` 返回；英文示例：

```json
{
  "id": "travel-postcard",
  "title": "Travel Postcard",
  "category": { "id": "travel", "name": "Travel & Landscape" },
  "coverUrl": "/assets/templates/travel-postcard.webp",
  "description": "Turn two travel photos into a postcard you can send.",
  "requiredImageCount": 2,
  "canvas": { "width": 1600, "height": 1200 }
}
```

缺翻译时回退中文基础列，不返回空字符串。

### 4.3 Asset

```json
{
  "id": "5c9b2f7e-3a41-4f0e-9d5b-2a7c1e8f4b10",
  "mimeType": "image/jpeg",
  "sizeBytes": 1834021,
  "width": 1600,
  "height": 1200,
  "createdAt": "2026-10-28T08:20:11.000Z",
  "contentUrl": "/api/assets/5c9b2f7e-3a41-4f0e-9d5b-2a7c1e8f4b10/content"
}
```

### 4.4 TaskSummary

```json
{
  "id": "0f0c8f5e-7a2d-4b9c-9a61-2d8f3b6c1e44",
  "templateId": "travel-postcard",
  "templateTitle": "旅行明信片",
  "templateCoverUrl": "/assets/templates/travel-postcard.webp",
  "status": "RETRYING",
  "currentStage": "GENERATE",
  "retry": {
    "retryCount": 1,
    "attemptCount": 1,
    "maxAttempts": 3,
    "nextRetryAt": "2026-10-28T08:21:09.000Z",
    "lastError": {
      "code": "GENERATION_FAILED",
      "message": "模拟失败：生成阶段按演示配置终止",
      "simulated": true
    }
  },
  "resumeCount": 0,
  "failure": null,
  "result": null,
  "createdAt": "2026-10-28T08:21:00.000Z",
  "updatedAt": "2026-10-28T08:21:07.000Z",
  "finishedAt": null
}
```

`retry` 字段始终存在：

| 字段 | 语义 |
|---|---|
| `retryCount` | 本任务已安排的自动重试总次数 |
| `attemptCount` | 当前/最近步骤已消耗的尝试次数（不含 `INTERRUPTED`） |
| `maxAttempts` | 每步尝试上限快照 |
| `nextRetryAt` | 仅 `RETRYING` 非空；其他状态为 `null` |
| `lastError` | 仅 `RETRYING` 非空；否则为 `null` |

`resumeCount` 表示服务重启后被恢复的次数；前端对 `QUEUED` 且 `resumeCount>0` 显示“正在恢复”。

失败时（终态）：

```json
{
  "status": "FAILED",
  "failure": {
    "stage": "GENERATE",
    "code": "GENERATION_FAILED",
    "message": "模拟失败：生成阶段按演示配置终止",
    "simulated": true
  }
}
```

> `failure.stage` 取失败步骤的 `stage`，与任务的 `currentStage` 一致；`simulated` 对应数据库 `failure_simulated`。`message` 按 `Accept-Language` 本地化，`code` 不随语言变化；`failure` 仅在 `FAILED` 时非空。`RETRYING` 期间失败原因在 `retry.lastError` 中。

成功时：

```json
{
  "status": "SUCCEEDED",
  "currentStage": "FINALIZE",
  "result": {
    "available": true,
    "url": "/api/tasks/0f0c8f5e-7a2d-4b9c-9a61-2d8f3b6c1e44/result",
    "width": 1600,
    "height": 1200
  }
}
```

> TaskSummary 的结果不含 `sizeBytes`，但包含受保护的 `url`，列表卡片可直接按需取图；TaskDetail 返回完整结果元数据。

### 4.5 TaskDetail

在 TaskSummary 基础上增加步骤、素材、画布与完整结果。`steps` 按 `seq` 升序，`materials` 按 `slotIndex` 升序：

```json
{
  "id": "0f0c8f5e-7a2d-4b9c-9a61-2d8f3b6c1e44",
  "templateId": "travel-postcard",
  "templateTitle": "旅行明信片",
  "templateCoverUrl": "/assets/templates/travel-postcard.webp",
  "canvas": { "width": 1600, "height": 1200 },
  "status": "SUCCEEDED",
  "currentStage": "FINALIZE",
  "retry": {
    "retryCount": 0,
    "attemptCount": 1,
    "maxAttempts": 3,
    "nextRetryAt": null,
    "lastError": null
  },
  "resumeCount": 0,
  "steps": [
    {
      "seq": 1,
      "stage": "MATERIAL_CHECK",
      "status": "SUCCEEDED",
      "attemptCount": 1,
      "message": "素材检查通过（2/2）",
      "errorCode": null,
      "startedAt": "2026-10-28T08:21:00.120Z",
      "finishedAt": "2026-10-28T08:21:00.920Z",
      "attempts": [
        {
          "attemptNo": 1,
          "status": "SUCCEEDED",
          "errorCode": null,
          "errorMessage": null,
          "startedAt": "2026-10-28T08:21:00.120Z",
          "finishedAt": "2026-10-28T08:21:00.920Z"
        }
      ]
    },
    {
      "seq": 2,
      "stage": "GENERATE",
      "status": "SUCCEEDED",
      "attemptCount": 1,
      "message": "版式已确定：1600×1200",
      "errorCode": null,
      "startedAt": "2026-10-28T08:21:00.940Z",
      "finishedAt": "2026-10-28T08:21:03.940Z",
      "attempts": [
        {
          "attemptNo": 1,
          "status": "SUCCEEDED",
          "errorCode": null,
          "errorMessage": null,
          "startedAt": "2026-10-28T08:21:00.940Z",
          "finishedAt": "2026-10-28T08:21:03.940Z"
        }
      ]
    },
    {
      "seq": 3,
      "stage": "FINALIZE",
      "status": "SUCCEEDED",
      "attemptCount": 1,
      "message": "结果已保存",
      "errorCode": null,
      "startedAt": "2026-10-28T08:21:03.960Z",
      "finishedAt": "2026-10-28T08:21:05.160Z",
      "attempts": [
        {
          "attemptNo": 1,
          "status": "SUCCEEDED",
          "errorCode": null,
          "errorMessage": null,
          "startedAt": "2026-10-28T08:21:03.960Z",
          "finishedAt": "2026-10-28T08:21:05.160Z"
        }
      ]
    }
  ],
  "materials": [
    {
      "slotIndex": 1,
      "assetId": "5c9b2f7e-3a41-4f0e-9d5b-2a7c1e8f4b10",
      "contentUrl": "/api/assets/5c9b2f7e-3a41-4f0e-9d5b-2a7c1e8f4b10/content"
    },
    {
      "slotIndex": 2,
      "assetId": "8d1e6a2c-4b7f-4c3a-8e9d-5f2b7c1a0d33",
      "contentUrl": "/api/assets/8d1e6a2c-4b7f-4c3a-8e9d-5f2b7c1a0d33/content"
    }
  ],
  "failure": null,
  "result": {
    "url": "/api/tasks/0f0c8f5e-7a2d-4b9c-9a61-2d8f3b6c1e44/result",
    "width": 1600,
    "height": 1200,
    "sizeBytes": 2417780
  },
  "createdAt": "2026-10-28T08:21:00.000Z",
  "updatedAt": "2026-10-28T08:21:05.200Z",
  "finishedAt": "2026-10-28T08:21:05.160Z"
}
```

步骤字段：

| 字段 | 语义 |
|---|---|
| `attemptCount` | 当前步骤已消耗的尝试次数；`INTERRUPTED` 不计入 |
| `message` | 按 `Accept-Language` 本地化的步骤消息；`PENDING` 时可读为 `null` |
| `errorCode` | 失败步骤的稳定错误码；成功/未开始为 `null` |
| `attempts` | 该步骤全部尝试，按 `attemptNo` 升序；包含 `INTERRUPTED` |

尝试字段：

| 字段 | 语义 |
|---|---|
| `attemptNo` | 步骤内序号，从 1 开始；中断也占号 |
| `status` | `RUNNING` / `SUCCEEDED` / `FAILED` / `INTERRUPTED` |
| `errorCode` / `errorMessage` | 失败时稳定码与本地化消息；其余为 `null` |

`RETRYING` 期间，失败步骤的 `status=FAILED`、`errorCode` 非空，后续步骤为 `PENDING`；任务快照中的 `retry.nextRetryAt` 与 `retry.lastError` 给出等待信息。

### 4.6 Page

```json
{
  "items": [],
  "page": 1,
  "size": 20,
  "total": 0,
  "totalPages": 0,
  "hasNext": false
}
```

## 5. 模板

### 5.1 GET `/api/template-categories`

- 公开。
- `Accept-Language` 决定 `name`；缺翻译回退中文。
- `200`：

```json
{
  "items": [
    { "id": "travel", "name": "旅行风景", "sortOrder": 10 }
  ]
}
```

“全部”不返回，由前端置顶；前端文案从词典取，不依赖该名称。

### 5.2 GET `/api/templates`

- 公开。
- Query：

| 参数 | 必填 | 说明 |
|---|---|---|
| `category` | 否 | 分类 slug；省略或 `all` 表示全部；未知分类返回空列表 |

- `200`：

```json
{
  "items": [
    {
      "id": "travel-postcard",
      "title": "旅行明信片",
      "category": { "id": "travel", "name": "旅行风景" },
      "coverUrl": "/assets/templates/travel-postcard.webp",
      "description": "把两张旅途照片拼成一张可以寄出的明信片。",
      "requiredImageCount": 2,
      "canvas": { "width": 1600, "height": 1200 }
    }
  ],
  "total": 2
}
```

- 只返回 `is_published=1`；按分类排序、模板排序、标题排序。
- `title` / `description` / `category.name` 按 `Accept-Language` 本地化。
- `Cache-Control: public, max-age=60`；`Vary: Accept-Language`；`Content-Language` 为实际语言。

### 5.3 GET `/api/templates/{id}`

- 公开。
- `200`：TemplateDetail（文案按 `Accept-Language` 本地化）。
- `404 TEMPLATE_NOT_FOUND`：不存在或下架。
- `Cache-Control: public, max-age=60`；`Vary: Accept-Language`。

## 6. 素材

### 6.1 POST `/api/assets`

- 需要登录。
- `multipart/form-data`：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `file` | file | 是 | 单张 JPEG/PNG |
| `templateId` | string | 否 | 传入时按该模板的 MIME 与大小上限校验；前端始终传 |

- 服务端按 [02 · 5.2](02-domain-model.md#52-上传校验服务端按顺序) 校验、解码、规范化、落盘、入库。
- `201 Created`，`Location: /api/assets/{id}/content`，响应体为 Asset。
- 错误：`400`（缺文件/模板 ID 格式）、`404 TEMPLATE_NOT_FOUND`、`413 FILE_TOO_LARGE`、`415 UNSUPPORTED_MEDIA_TYPE`、`422 INVALID_IMAGE`。
- 单次请求体上限 12 MB（留出 multipart 开销）；超过由容器/Tomcat 拒绝并映射为 413。

### 6.2 GET `/api/assets/{id}/content`

- 需要登录且为素材所有者；他人资源 `404 ASSET_NOT_FOUND`。
- `200`：图片二进制，`Content-Type` 为 `image/jpeg` 或 `image/png`。
- 响应头：

```text
Cache-Control: private, max-age=300
ETag: W/"<asset-id>"
X-Content-Type-Options: nosniff
Content-Disposition: inline; filename="asset-<id>.jpg"
```

PNG 素材的 `Content-Type` 为 `image/png`，文件名扩展名相应改为 `.png`；其余头一致。

- 支持 `If-None-Match`，命中返回 `304`。
- 前端以 `fetch` + `Authorization` 取回 Blob，再用 `URL.createObjectURL` 展示；卸载时释放。

### 6.3 DELETE `/api/assets/{id}`

- 需要登录且为所有者。
- 未被任何 `task_material` 引用：删除数据库记录与物理文件，返回 `204`。
- 已引用：`409 ASSET_IN_USE`。
- 不存在或他人：`404 ASSET_NOT_FOUND`。
- 物理文件删除失败只记录日志，不回滚已完成的数据库删除（避免留下不可管理的记录）；P1 由清理任务兜底。

## 7. 任务

### 7.1 POST `/api/tasks`

- 需要登录。
- 请求：

```json
{
  "templateId": "travel-postcard",
  "assetIds": [
    "5c9b2f7e-3a41-4f0e-9d5b-2a7c1e8f4b10",
    "8d1e6a2c-4b7f-4c3a-8e9d-5f2b7c1a0d33"
  ],
  "clientRequestId": "b7e2c9a4-1f6d-4a8e-9c3b-5d0f2e7a1b64"
}
```

校验顺序：

1. `clientRequestId` 为 UUID。
2. 按 `(user_id, clientRequestId)` 查重：
   - 已存在且 `templateId`、`assetIds` 顺序一致 → 直接返回原任务（`200`，`Idempotent-Replay: true`），**不再做后续模板/素材校验**；
   - 已存在但载荷不一致 → `409 IDEMPOTENCY_KEY_REUSED`；
   - 并发插入冲突 → 重新读取后按上述规则返回，仍失败才 `409 IDEMPOTENCY_CONFLICT`。
3. 模板存在且上架；否则 `404 TEMPLATE_NOT_FOUND`。
4. `assetIds` 数量精确等于 `requiredImageCount`，且无重复；否则 `400 VALIDATION_FAILED`。
5. 每个素材存在且属于当前用户；否则 `404 ASSET_NOT_FOUND`（不区分“不存在”和“别人的”）。
6. 素材 MIME 命中模板允许列表，大小不超过模板上限；否则 `400 VALIDATION_FAILED`。
7. 创建任务（快照 `maxAttempts`）并返回 `202`。

成功 `202 Accepted`：

```text
Location: /api/tasks/0f0c8f5e-7a2d-4b9c-9a61-2d8f3b6c1e44
```

响应体为 TaskDetail（`status=QUEUED`，三步均 `PENDING`，`currentStage=null`，`retry.maxAttempts` 为创建时配置快照）。

幂等重放 `200 OK`：响应体为已存在任务的 TaskDetail，头 `Idempotent-Replay: true`，不重复创建、不重复执行。重放返回的是**当前**任务快照，可能已经 `RUNNING`、`RETRYING` 或终态。

错误补充：

| 状态 | code | 场景 |
|---|---|---|
| 400 | `VALIDATION_FAILED` | 数量不符、重复素材、类型/大小不符合模板、UUID 格式错误 |
| 404 | `TEMPLATE_NOT_FOUND` | 模板不存在或下架 |
| 404 | `ASSET_NOT_FOUND` | 素材不存在或属于他人 |
| 409 | `IDEMPOTENCY_KEY_REUSED` | 同一 `clientRequestId` 但 `templateId` 或素材顺序不同 |
| 409 | `IDEMPOTENCY_CONFLICT` | 并发冲突后仍无法读取原任务（客户端可重试同一请求） |

> 服务端不信任前端“已校验”的结论；所有规则重跑一遍。素材已入库、文件可读且不可变，因此不需要“上传中”的状态字段。错误 `detail` 按 `Accept-Language` 返回，`code` 不变。

### 7.2 GET `/api/tasks`

- 需要登录，只返回当前用户任务。
- Query：

| 参数 | 默认 | 说明 |
|---|---|---|
| `status` | `all` | `all` / `active` / `QUEUED` / `RUNNING` / `RETRYING` / `SUCCEEDED` / `FAILED`；`active` = QUEUED + RUNNING + RETRYING |
| `templateId` | — | 按模板过滤；用于制作台右栏 |
| `page` | 1 | 从 1 开始 |
| `size` | 20 | 1～50 |

- `200`：Page<TaskSummary>，按 `created_at DESC, id DESC` 稳定排序。
- `templateTitle` 优先取当前语言翻译，缺翻译取模板基础列，模板不存在时用任务快照；`failure.message` / `retry.lastError.message` 按 `Accept-Language` 本地化。
- `400 VALIDATION_FAILED`：`status` 非法、`page < 1`、`size` 不在 1～50。
- `Cache-Control: no-store`；`Vary: Accept-Language`。

### 7.3 GET `/api/tasks/{id}`

- 需要登录且为任务所有者；他人/不存在均 `404 TASK_NOT_FOUND`。
- `200`：TaskDetail。
- `Cache-Control: no-store`；`Vary: Accept-Language`。
- 查询无副作用：不得推进步骤、不得重试、不得修改 `updatedAt`。

### 7.4 GET `/api/tasks/{id}/result`

- 需要登录且为任务所有者；他人/不存在 `404 TASK_NOT_FOUND`。
- 任务未成功：`409 TASK_NOT_READY`。
- `200`：JPEG 二进制。

```text
Content-Type: image/jpeg
Cache-Control: private, max-age=86400
ETag: W/"<task-id>-result"
X-Content-Type-Options: nosniff
Content-Disposition: inline; filename="framelab-<taskId 前 8 位>.jpg"
```

- 支持 `If-None-Match` → `304`。
- 结果不可变；成功后重复读取返回同一文件。

### 7.5 重试与续跑的可见语义

| 场景 | 响应表现 | 前端行为 |
|---|---|---|
| 首次排队 | `status=QUEUED`、`resumeCount=0`、`retry.attemptCount=0` | 显示“排队中”，轮询 |
| 服务重启恢复 | `status=QUEUED`、`resumeCount>0`、`currentStage` 保留、已完成步骤保持 `SUCCEEDED` | 显示“正在恢复”，继续轮询；不提示失败 |
| 可重试失败 | `status=RETRYING`、`retry.nextRetryAt` 非空、`retry.lastError` 非空、失败步骤 `FAILED` | 显示“等待重试”、尝试次数与最近原因；继续轮询 |
| 重试开始 | `status=RUNNING`、`retry.nextRetryAt=null`、新 attempt `RUNNING` | 显示当前阶段；保留失败尝试历史 |
| 重试成功 | 失败步骤 `SUCCEEDED`、`attemptCount` 增加、更早尝试仍可查 | 显示完成与结果 |
| 重试耗尽 | `status=FAILED`、`failure` 非空、后续步骤 `SKIPPED` | 显示失败原因与尝试历史 |
| 中断尝试 | 历史中出现 `INTERRUPTED`，不计入 `attemptCount` | 在尝试历史中标注“服务重启中断” |

轮询规则：

- `RETRYING` 仍是活跃任务，必须继续轮询；建议间隔 2.5 s。
- 前端不得依据 `nextRetryAt` 自行把任务改成 `RUNNING`；状态只认服务端。
- 查询失败时保留最后一次快照，显示“暂时无法更新”，不得显示“重试失败”或“任务失败”。

## 8. 轮询、缓存与请求生命周期

前端只通过以下读接口获取事实：

| 场景 | 接口 | 建议间隔 |
|---|---|---|
| 制作台右栏（当前模板） | `GET /api/tasks?templateId={id}&size=20` | 有活跃任务 2.5 s；有 RUNNING 1.5 s；仅 RETRYING 2.5 s；全终态停止 |
| 历史页 | `GET /api/tasks?status=all&page=1&size=20` | 同上 |
| 任务详情弹层 | `GET /api/tasks/{id}` | 1.5 s；`RETRYING` 2.5 s；终态停止 |
| 结果图片 | `GET /api/tasks/{id}/result` | 成功后取一次，Blob 缓存 |

规则：

1. 轮询请求为纯 GET，不得因查询推进任务。
2. 只有 `QUEUED` / `RUNNING` / `RETRYING` 需要跟踪；`SUCCEEDED` / `FAILED` 立即停止。
3. 页面切换到后台时暂停轮询（TanStack Query `refetchIntervalInBackground: false`）；回到前台立即刷新一次。
4. 请求失败时保留最后一次成功数据；显示“暂时无法更新”提示与手动重试；不得把任务状态改为失败。
5. 离开页面、关闭详情、退出或切换用户时取消请求（`AbortController`），并清理该用户的查询缓存。
6. 所有查询键必须包含当前用户 ID 与语言：`['tasks', userId, locale, filters]`、`['task', userId, locale, taskId]`；切换语言时取消在途请求并重取。
7. 不用查询返回值做“乐观合并”到其他任务；每个任务只接受自身 ID 的响应。
8. 请求头统一携带当前 `Accept-Language`；响应 `Content-Language` 与请求不一致时不写缓存，按新语言重取。

## 9. 模拟控制（管理端）

### 9.1 GET `/api/admin/simulation`

- 需要 `ROLE_ADMIN`；USER 返回 `403 FORBIDDEN`。
- `200`：

```json
{
  "delays": {
    "materialCheckMs": 800,
    "generateMs": 3000,
    "finalizeMs": 1200
  },
  "failure": {
    "stage": null,
    "remaining": 0,
    "retryable": false
  },
  "updatedAt": "2026-10-28T08:00:00.000Z",
  "updatedBy": 3
}
```

### 9.2 PUT `/api/admin/simulation`

- 需要 `ROLE_ADMIN`。
- 全量替换：

```json
{
  "delays": {
    "materialCheckMs": 800,
    "generateMs": 8000,
    "finalizeMs": 1200
  },
  "failure": {
    "stage": "GENERATE",
    "remaining": 2,
    "retryable": true
  }
}
```

校验：

- 每个耗时 0～60000 的整数。
- `stage` ∈ `null` / `MATERIAL_CHECK` / `GENERATE` / `FINALIZE`。
- `remaining` 0～50；`remaining > 0` 时 `stage` 不能为 null；`stage` 为 null 时 `remaining` 必须为 0。
- `retryable` 为布尔值，默认 `false`；`remaining = 0` 时必须为 `false`。
- `retryable=true` 只覆盖本次注入，不改变失败码的默认可重试性。
- 成功后写 `updated_by` 为当前管理员、`updated_at` 为当前时间，返回与 GET 相同结构。
- 失败 `400 VALIDATION_FAILED`。

演示示例：

```bash
# 1) 登录管理员，取 accessToken
curl -s http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@framelab.local","password":"Admin1234!"}'

# 2) 让下两次进入 GENERATE 的任务失败，并允许自动重试
curl -s -X PUT http://localhost:8080/api/admin/simulation \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"delays":{"materialCheckMs":800,"generateMs":3000,"finalizeMs":1200},"failure":{"stage":"GENERATE","remaining":2,"retryable":true}}'

# 3) 重置失败注入并恢复默认耗时
curl -s -X PUT http://localhost:8080/api/admin/simulation \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"delays":{"materialCheckMs":800,"generateMs":3000,"finalizeMs":1200},"failure":{"stage":null,"remaining":0,"retryable":false}}'
```

> 配置为全局单例；并发多任务时失败会落在最先进入该阶段的任务。演示失败时一次只创建一个任务。`remaining=2,retryable=true` 在同一任务上会消耗两次：第 1、2 次尝试失败并重试，第 3 次成功（默认上限 3）。

## 10. 健康检查

### GET `/actuator/health`

- 公开；仅暴露 `status`。
- `200 { "status": "UP" }`；数据库或磁盘检查失败时 `503`。
- 供 Docker healthcheck 与 README 验收使用。
- 不暴露组件详情、环境变量或数据库连接串。

## 11. 契约变更规则

1. 修改路径、字段、枚举、状态码、错误码时，必须同批修改：本文 → 后端 DTO/Controller → 前端 `features/*/api.ts` 与类型 → MSW 夹具 → 接口测试。
2. 新增可选字段不视为破坏性变更；客户端必须容忍未知字段。
3. 删除字段、改语义、改枚举视为破坏性变更；P0 不升级版本号，直接在开发期修正所有引用。
4. 接口文档与数据库字段不一致时，以本文与 [02](02-domain-model.md) 为准，先对齐再编码。
5. P1 可引入 springdoc-openapi 生成 `/v3/api-docs` 作为辅助，但本文仍是人工维护的验收口径。
