# 02 · 领域模型、状态机与业务规则

> 本文定义“系统里有什么、如何变化、什么永远不允许发生”。数据库字段见 [03-database.md](03-database.md)，接口字段见 [04-api.md](04-api.md)。
> 代码中的枚举名、状态名、失败码必须与本文完全一致。

## 1. 通用约定

| 项 | 约定 |
|---|---|
| 主键 | 用户为自增 `BIGINT`；模板/分类为可读 `slug`；任务、素材、令牌为 UUID 字符串 |
| 时间 | 数据库 `DATETIME(3)` 存 UTC；API 用 ISO-8601（`2026-10-28T08:30:00.000Z`）；前端本地化展示 |
| 枚举 | 数据库存大写字符串；Java 用 `enum`；JSON 用大写字符串；禁止前端自定义同义值 |
| 金额/积分 | 本系统不存在；界面不得出现 credits、余额、价格 |
| 事实源 | MySQL + Java 返回值。浏览器缓存、URL、前端计时器都不是任务状态的事实源 |
| 所有权 | 除模板与模板封面外，所有业务资源都必须通过 `user_id` 归属当前登录用户 |
| 删除 | P0 只允许删除“未绑定任务的素材”和“退出登录时的刷新令牌”；任务与结果不删除 |
| 语言 | 面向用户文案支持 `zh-CN` / `en-US`；模板与分类文案存翻译表；任务消息按请求语言渲染；错误码、字段、日志键英文；用户消息不得包含堆栈、SQL、磁盘路径 |

## 2. 实体关系

```
app_user 1 ──── n refresh_token
    │
    ├──────── n upload_asset
    │                 │
    │                 └──── n task_material ──── 1 task
    ├──────── n task ──────────────────────────────┘
    │              │
    │              ├──── 1 template (FK + 创建时快照)
    │              ├──── n task_step (seq = 1,2,3)
    │              │          └──── n task_step_attempt
    │              └──── n task_step_attempt
    │
    └──────── n simulation_setting (updated_by，可空)

template_category 1 ──── n template 1 ──── n task
template_category 1 ──── n template_category_translation
template 1 ──── n template_translation
```

聚合边界：

- **Task 聚合**：`task` + `task_step` + `task_step_attempt` + `task_material`。四者一起表达一次制作请求；对外的任务详情必须从聚合根读取。
- **Asset 独立聚合**：素材先于任务存在，可被多个任务引用；一旦 `READY` 即不可变（不覆盖、不改归属）。
- **Template 独立只读聚合**：P0 无模板写接口；任务创建时把展示与画布参数快照进任务，模板后续改动不影响历史；翻译表只影响展示文案。

## 3. 用户与身份

### 3.1 用户

| 字段 | 说明 |
|---|---|
| `id` | 自增主键；JWT 的 `sub` |
| `email` | 登录名，统一转小写；唯一；不超过 190 字符 |
| `display_name` | 展示名，1～64 字符 |
| `password_hash` | BCrypt（cost 12）；任何接口、日志、响应都不得返回 |
| `role` | `USER` / `ADMIN` |
| `status` | `ACTIVE` / `DISABLED` |
| `created_at` / `updated_at` | UTC |

规则：

1. 登录失败统一返回“邮箱或密码不正确”，不区分账号不存在、密码错误、账号禁用；禁用账号可单独记录审计日志。
2. 登录成功返回访问令牌，并下发刷新令牌 Cookie；刷新令牌只以 SHA-256 哈希入库。
3. P0 不开放注册；提供 2 个普通演示账号和 1 个管理员账号，见 [03 · 种子数据](03-database.md#6-种子数据)。
4. 访问令牌携带 `ver`（`app_user.token_version`）；过滤器每次鉴权校验用户状态与版本。禁用用户由状态校验立即失效；退出全部设备递增版本，已签发访问令牌同样立即失效。
5. 用户删除、密码找回、邮箱验证不在范围。

### 3.2 会话

| 令牌 | 形态 | 有效期 | 存储 | 用途 |
|---|---|---|---|---|
| 访问令牌 | JWT（HS256） | 15 分钟 | 浏览器内存（Zustand），不落 localStorage | 每次 API 请求 `Authorization: Bearer` |
| 刷新令牌 | 256 位随机串 | 家族绝对 7 天 | HttpOnly + SameSite=Lax Cookie；服务端只存哈希 | 仅 `POST /api/auth/refresh`，轮换为新令牌 |

- 刷新令牌**每次刷新轮换**：旧令牌标记 `ROTATED` 并写入 `replaced_by_id`，新令牌沿用同一 `family_id`；家族绝对过期时间不随轮换延长。
- 旧令牌在轮换后 30 秒宽限内重复使用不会撤销家族，用于兜住多标签页并发刷新；超出宽限的重复使用视为重放，撤销整个家族并要求重新登录。
- 退出登录撤销当前家族并清 Cookie；退出全部设备撤销该用户全部家族并递增 `token_version`。
- 访问令牌声明：`sub`（用户 ID）、`email`、`role`、`ver`（令牌版本）、`jti`、`iss=framelab-api`、`aud=framelab-web`、`iat`、`exp`。
- 访问令牌每次请求校验 `status=ACTIVE` 且 `ver` 等于当前用户版本；退出全部设备或禁用后立即失效。普通退出只撤销当前会话，其他设备的访问令牌最多再存活 15 分钟。

### 3.3 权限

| 动作 | 访客 | USER | ADMIN |
|---|---|---|---|
| 浏览模板列表/详情 | ✓ | ✓ | ✓ |
| 上传素材、提交任务、查看自己的任务/结果 | ✗ | ✓ | ✓ |
| 查看他人任务/素材 | ✗ | ✗（404） | ✗（404） |
| 读取/修改模拟控制 | ✗ | ✗（403） | ✓ |

管理员不因角色获得跨用户数据读取权；模拟控制是全局配置，不包含用户作品数据。

## 4. 模板与分类

### 4.1 分类

- 分类是筛选维度，不是权限或定价维度。
- “全部”是前端虚拟项，不落库。
- 分类：`travel` 旅行风景、`creative` 创意图片、`festival` 节日营销、`portrait` 人像写真、`life` 生活记录、`commerce` 电商海报。
- 分类名称以基础列 `name` 为 `zh-CN` 文案，`template_category_translation` 提供 `en-US`；缺翻译时回退中文。

### 4.2 模板

| 字段 | 语义 | 约束 |
|---|---|---|
| `id` | slug，如 `travel-postcard` | 稳定、不可变；URL 使用它 |
| `category_id` | 所属分类 | 必须存在 |
| `title` | 标题 | 1～80 字符 |
| `description` | 一句话描述 | 1～500 字符 |
| `cover_path` | 封面公开路径 | 形如 `/assets/templates/travel-postcard.webp` |
| `required_image_count` | 需要的素材张数 | 1～5，提交时必须**精确等于** |
| `accepted_mime_types` | 允许的 MIME | 逗号分隔，必须是 `image/jpeg`、`image/png` 的非空子集 |
| `max_file_size_bytes` | 单文件上限 | 1 MB～10 MB；P0 默认 10 MB |
| `canvas_width` / `canvas_height` | 结果画布像素 | 64～4096；默认 1200 × 1600 |
| `sort_order` | 列表排序 | 升序 |
| `is_published` | 是否上架 | 仅上架模板出现在列表、详情与提交校验中 |

规则：

1. 模板是种子数据，P0 不提供 CRUD；`is_published` 仅供运维改库演示。
2. 任务创建时把 `title / cover_path / canvas_width / canvas_height` 快照到任务；之后模板改名、下架、改画布都不影响已创建任务。
3. 模板列表只返回上架模板；详情对下架模板返回 404。
4. 模板封面是公开资源，可按 slug 长缓存；上传素材与结果永远不公开。
5. `title` / `description` 基础列是 `zh-CN` 文案；`template_translation` 按 `locale` 提供其他语言。列表与详情按请求语言取翻译，缺翻译时回退基础列。
6. 任务详情的模板标题：模板仍存在时，取当前语言翻译；缺翻译取基础列；模板不存在时用任务快照。封面、画布、素材始终用快照。

## 5. 素材资产

### 5.1 定义

素材（asset）是用户上传、经服务端校验并规范化后的图片。它不是浏览器临时文件，也不是任务内的副本。

| 字段 | 语义 |
|---|---|
| `id` | UUID |
| `user_id` | 所有者；上传时取自登录态，不接受客户端传入 |
| `storage_key` | 服务端相对路径，如 `materials/42/0f0c....jpg`；不含原始文件名 |
| `mime_type` | 规范化后的实际类型（`image/jpeg` / `image/png`） |
| `size_bytes` | 规范化后字节数 |
| `width` / `height` | 解码后的像素尺寸 |
| `created_at` | UTC |

### 5.2 上传校验（服务端，按顺序）

1. 已登录；单次只接收一个文件。
2. 文件非空、≤ 模板 `max_file_size_bytes`（上传接口通过 `templateId` 读取模板上限；未传时使用全局 10 MB，提交时仍按模板复核）。
3. 文件头魔数与声明的 `Content-Type` 一致；只接受 JPEG/PNG。
4. 用 `ImageIO` 解码成功；像素总数 ≤ 4000 万；宽高均 ≤ 8192。
5. 规范化：纠正方向、去除 EXIF/GPS 等敏感元数据、最长边缩放至 4096、按原格式重编码（JPEG quality 0.9）。
6. 写入临时文件 → `fsync` → 原子移动到最终路径；失败清理临时文件。
7. 插入 `upload_asset`；任一环节失败不留下数据库记录或可访问文件。

### 5.3 生命周期

```
选择本地文件（blob: 预览）
  → POST /api/assets（上传中 / 失败可重试）
  → READY（不可变，可用于提交）
  → 被 task_material 引用（不可删除）
  → 未被引用时可 DELETE（物理删除记录与文件）
```

规则：

1. 素材内容一旦 `READY` 不可覆盖；替换 = 上传新素材并替换任务草稿中的引用。
2. 素材可被同一用户的多个任务引用；不同任务只读同一文件，互不影响。
3. 已绑定任务的素材不允许删除（409 `ASSET_IN_USE`），保证历史任务可重放检查。
4. 未绑定素材可由用户主动删除；P1 定时清理创建超过 7 天且未绑定的素材。
5. 素材内容只允许所有者读取；他人访问返回 404，不泄露存在性。
6. 原始文件名不落库、不进路径、不回显，避免路径注入与隐私问题。

> `READY` 是概念状态：`upload_asset` 记录插入成功且文件可读即视为 `READY`，数据库不单设状态列。上传要么完整成功，要么不产生记录。

## 6. 任务聚合

### 6.1 Task

| 字段 | 语义 |
|---|---|
| `id` | UUID；对外唯一标识 |
| `user_id` | 所有者 |
| `template_id` | 模板引用 |
| `template_title` / `template_cover_path` / `canvas_width` / `canvas_height` | 创建时快照 |
| `client_request_id` | 前端生成的 UUID，用于提交幂等 |
| `status` | `QUEUED` / `RUNNING` / `RETRYING` / `SUCCEEDED` / `FAILED` |
| `current_stage` | 当前或最后阶段；从未开始的新任务为 `NULL`，恢复后的 `QUEUED` 保留最后阶段 |
| `max_step_attempts` | 每步尝试上限快照，默认 3 |
| `retry_count` | 已安排的自动重试总次数（跨步骤累加） |
| `resume_count` | 被启动恢复回收的次数 |
| `next_retry_at` | 仅 `RETRYING` 时非空；到期后可被 worker 认领 |
| `last_error_code` / `last_error_detail` / `last_error_simulated` | `RETRYING` 期间最近一次可重试失败；成功或终态后清空 / 转存 |
| `failure_code` / `failure_args` / `failure_detail` | 仅 `FAILED` 时非空；`code` 稳定，`args` 为本地化参数，`detail` 为技术诊断 |
| `failure_simulated` | 是否由模拟注入导致 |
| `result_storage_key` / `result_width` / `result_height` / `result_size_bytes` | 仅 `SUCCEEDED` 时非空 |
| `created_at` / `started_at` / `finished_at` | UTC；`started_at` 首次认领时写入，`finished_at` 仅在终态写入 |
| `updated_at` | UTC；任一字段变化时由数据库更新，用于前端判断快照新旧 |
| `row_version` | 乐观锁版本，每次状态写入 +1 |

> `failure_detail` 与 `last_error_detail` 是英文技术诊断，供日志与管理员排障，**不直接展示给用户**；用户看到的原因由 `failure_code` / `last_error_code` 与消息参数在读取时本地化生成。

### 6.2 TaskStep

| 字段 | 语义 |
|---|---|
| `id` | 自增 |
| `task_id` | 所属任务 |
| `seq` | 1、2、3，固定顺序 |
| `stage` | `MATERIAL_CHECK` / `GENERATE` / `FINALIZE` |
| `status` | `PENDING` / `RUNNING` / `SUCCEEDED` / `FAILED` / `SKIPPED` |
| `attempt_count` | 已消耗的尝试次数；`INTERRUPTED` 不计入，恢复时回退 |
| `message_args` | 成功/过程消息的 JSON 参数，如 `{"checked":2,"required":2}` |
| `error_code` | 最近一次失败码；成功时清空；`RETRYING` 期间保留失败快照 |
| `started_at` / `finished_at` | UTC；反映最近一次尝试 |
| `updated_at` | UTC；步骤记录更新时间 |

### 6.3 TaskStepAttempt

| 字段 | 语义 |
|---|---|
| `id` | 自增 |
| `task_id` | 所属任务 |
| `step_id` | 所属步骤 |
| `attempt_no` | 步骤内序号，从 1 递增；中断也占号 |
| `status` | `RUNNING` / `SUCCEEDED` / `FAILED` / `INTERRUPTED` |
| `error_code` | 失败时的稳定码；成功与中断为 `NULL` |
| `error_detail` | 英文技术诊断；不直接展示 |
| `started_at` / `finished_at` | UTC；`RUNNING` 时 `finished_at` 为空 |
| `created_at` | UTC |

规则：

- `UNIQUE(step_id, attempt_no)`；同一时刻每个步骤最多一个 `RUNNING` attempt。
- `task_id` 是冗余外键，必须与 `step.task_id` 一致，由服务层写入时保证。
- attempt 只增不改（`RUNNING → 终态` 除外），作为审计记录永久保留。
- 每次执行前先插入 `RUNNING`，执行结束后与 `task_step`、`task` 在**同一事务**内更新。
- `INTERRUPTED` 只由启动恢复写入；不计入 `attempt_count`，也不增加 `retry_count`。

### 6.4 TaskMaterial

- `(task_id, slot_index)` 唯一，`slot_index` 从 1 开始，对应前端上传位。
- 同一任务不允许重复引用同一素材：`UNIQUE(task_id, asset_id)`。
- 素材顺序即结果版式中的槽位顺序，提交后不可调整。

### 6.5 创建事务

```
BEGIN
  1. 按 (user_id, client_request_id) 查重
       a. 存在且 templateId、assetIds 顺序一致 → 返回已有任务（200 + 重放头）
       b. 存在但载荷不一致 → 409 IDEMPOTENCY_KEY_REUSED，不创建新任务
       c. 不存在 → 继续
  2. 校验模板存在且已上架（否则 404）
  3. 校验 assetIds 数量 == required_image_count，且无重复
  4. 校验每个素材属于当前用户（否则 404 ASSET_NOT_FOUND）
  5. INSERT task（QUEUED, current_stage=NULL, max_step_attempts=配置值）
       + 模板快照 + client_request_id
  6. INSERT task_step ×3（PENDING, seq=1..3, attempt_count=0）
  7. INSERT task_material ×N（slot_index=1..N）
COMMIT
  → 返回 202 + 任务快照；不等待 worker
```

关键点：

- 查重优先于模板/素材校验：首次请求成功后即使模板下架或素材状态变化，客户端重试仍应拿回同一任务，而不是 404 或重复创建。
- 同键不同载荷返回 409，避免“以为是同一请求、实际参数已变”导致结果与预期不符。
- 并发提交依赖唯一索引 `(user_id, client_request_id)` 兜底；冲突方转为重放读取。
- 校验与写入在同一事务内；任务落库即代表“已受理”。
- `max_step_attempts` 在创建时快照，之后修改全局重试配置不影响已创建任务。
- 不主动 `enqueue`；工作线程轮询数据库认领 `QUEUED` 或到期 `RETRYING`，进程崩溃不丢失已受理任务。
- 返回后前端可立即请求任务；最迟一个轮询周期内出现 `RUNNING`。

## 7. 业务规则

| 编号 | 规则 |
|---|---|
| BR-01 | 提交素材张数必须**精确等于**模板 `required_image_count`，不是“至少”。 |
| BR-02 | 所有素材必须属于当前用户且已 `READY`；任一不满足，整个提交失败，不创建任务。 |
| BR-03 | 素材类型必须命中模板 `accepted_mime_types`；单文件大小不得超过模板上限。 |
| BR-04 | 同一 `(user_id, client_request_id)` 只创建一个任务；重复请求返回原任务，同键不同载荷返回 409。 |
| BR-05 | 任务创建后，模板、素材、画布参数以快照为准；删除模板不删除任务。 |
| BR-06 | 已绑定任务的素材不可删除；任务创建后不可替换素材。 |
| BR-07 | 三个步骤严格按 `MATERIAL_CHECK → GENERATE → FINALIZE` 执行，不允许跳过或并行。 |
| BR-08 | 不可重试失败或重试预算耗尽后，后续步骤不得开始，统一置为 `SKIPPED`；可重试失败在等待期间后续步骤保持 `PENDING`。 |
| BR-09 | 失败必须同时记录失败阶段、稳定失败码与可本地化的原因参数；不得只返回“失败”，也不得把已翻译句子写入任务。 |
| BR-10 | 成功必须有可读取的结果文件与宽高；仅有状态没有结果不算成功。 |
| BR-11 | 终态不可逆：`SUCCEEDED` / `FAILED` 不得再变回 `RUNNING`、`RETRYING` 或另一终态。 |
| BR-12 | 查询接口只读；不得因为查询而推进阶段、重试任务或修改状态。 |
| BR-13 | 同一任务同一时刻最多一个步骤处于 `RUNNING`；同一时刻最多一个该步骤的 `RUNNING` attempt。 |
| BR-14 | 任务、素材、结果的所有读写都必须带 `user_id` 条件；他人资源一律 404。 |
| BR-15 | 素材与结果不得通过静态目录直接公开；必须经鉴权接口流式返回。 |
| BR-16 | 任务结果不得因后续查询、刷新、登录切换而改变；历史以数据库为准。 |
| BR-17 | 失败原因不得包含异常堆栈、SQL、绝对路径、内部主机名；技术诊断只进日志与 `*_detail` 字段。 |
| BR-18 | 定时器与轮询只能读取事实，不得驱动任务前进；阶段推进只能发生在 worker。 |
| BR-19 | 服务重启后，未开始的 `QUEUED` 任务继续等待认领；`RUNNING` 任务回收为 `QUEUED`，从第一个未成功步骤续跑，不判失败。 |
| BR-20 | 结果图只叠加英文品牌水印与任务短码；不绘制中文，避免容器字体缺失。 |
| BR-21 | 自动重试只针对可重试失败码；每步最多 `max_step_attempts` 次尝试，退避 2s → 4s → … 封顶 30s。 |
| BR-22 | 重试按步骤进行；已成功步骤不重跑，重试成功后继续后续步骤。 |
| BR-23 | 中断（进程重启）不消耗重试预算，`attempt_count` 回退，`resume_count` 增加。 |
| BR-24 | `RETRYING` 与 `QUEUED`、`RUNNING` 一样属于活跃状态，前端必须继续轮询。 |
| BR-25 | 消息与模板文案按请求语言渲染；任务只存稳定码与参数，不存翻译结果。 |
| BR-26 | 同一任务可在不同语言下被读取；语言切换不得改变任务状态、失败码、重试或续跑行为。 |

## 8. 任务状态机

### 8.1 Task 状态

| 当前 | 事件 | 前置条件 | 结果 | 写入 |
|---|---|---|---|---|
| — | 提交成功 | 校验通过 | `QUEUED` | task + 3 steps + materials |
| `QUEUED` | worker 认领 | 行锁认领成功 | `RUNNING` | `started_at`（仅首次）、`current_stage=MATERIAL_CHECK`、step1 `RUNNING`、attempt1 `RUNNING` |
| `RUNNING` | 步骤成功且非最后一步 | 当前步骤 `SUCCEEDED` | `RUNNING` | 下一步 `RUNNING`、`current_stage` 更新、新 attempt |
| `RUNNING` | 最后一步成功 | 结果已落盘并写库 | `SUCCEEDED` | `finished_at`、结果字段、step3 `SUCCEEDED`、attempt `SUCCEEDED` |
| `RUNNING` | 可重试失败且预算未耗尽 | 失败码可重试，`attempt_count < max_step_attempts` | `RETRYING` | attempt `FAILED`、step `FAILED`、`next_retry_at`、`last_error_*`、`retry_count+1` |
| `RUNNING` | 不可重试失败或预算耗尽 | 异常已映射为失败码 | `FAILED` | `finished_at`、失败字段、清空 `last_error_*`、当前步 `FAILED`、后续 `SKIPPED`、attempt `FAILED` |
| `RETRYING` | 到达 `next_retry_at` 并被认领 | 行锁认领成功 | `RUNNING` | `next_retry_at=NULL`、`current_stage`、step `RUNNING`、新 attempt |
| `RUNNING` | 进程重启后被恢复 | 启动扫描发现 | `QUEUED` | attempt `INTERRUPTED`、step `PENDING`、`resume_count+1`；不判失败 |
| `SUCCEEDED` / `FAILED` | 任意事件 | — | 不变 | 拒绝写入 |

状态图：

```
                          ┌──────────────┐
   提交 ────────────────▶ │    QUEUED    │
                          └──────┬───────┘
                                 │ worker 认领
                                 ▼
                          ┌──────────────┐  最后一步成功  ┌────────────┐
                          │   RUNNING    │ ─────────────▶ │ SUCCEEDED  │
                          └──┬───────┬───┘                └────────────┘
       可重试失败且有预算       │       │ 不可重试 / 预算耗尽
                              ▼       ▼
                       ┌──────────┐  ┌─────────┐
                       │ RETRYING │  │ FAILED  │
                       └────┬─────┘  └─────────┘
                            │ 到期认领
                            └────────────▶ RUNNING

   服务重启：RUNNING ──恢复──▶ QUEUED（resume_count+1；不进入 FAILED）
```

### 8.2 Step 状态

```
PENDING ──worker 开始──▶ RUNNING ──成功──▶ SUCCEEDED
   ▲                        │
   │                        ├─可重试失败──▶ FAILED ──任务 RETRYING──┐
   │                        │                                      │
   │                        └─不可重试 / 耗尽──▶ FAILED（终态）     │
   │                                                               │
   └────────────恢复（attempt INTERRUPTED，attempt_count−1）◀──────┘
                                   ▲
                                   │ 到达重试时间，重新认领
                                   └────────────────────────────────
SKIPPED：仅当前序失败且任务进入 FAILED 时，由后续步骤从 PENDING 转入
```

- `PENDING → SKIPPED` 只能由“前序步骤失败且任务已 `FAILED`”触发；`RETRYING` 期间后续步骤保持 `PENDING`。
- `SKIPPED` 步骤没有 `started_at / finished_at`，也没有 attempt。
- `RUNNING` 步骤必须有 `started_at` 与一个 `RUNNING` attempt；终态步骤必须有 `finished_at`。
- `FAILED` 步骤在 `RETRYING` 期间是“最近一次失败的快照”；重新开始时置 `RUNNING`、清空 `finished_at`，但保留历史 attempt。
- 步骤顺序不可打乱；seq 2 在 seq 1 `SUCCEEDED` 之前不得进入 `RUNNING`。
- 恢复时 `RUNNING → PENDING`，清空 `started_at / finished_at / error_code`，并回退 `attempt_count`。

### 8.3 不变量

1. `task.status = RUNNING` ⇒ 有且仅有一个 `RUNNING` 步骤，且其 `stage = current_stage`，该步骤有且仅有一个 `RUNNING` attempt。
2. `task.status = RETRYING` ⇒ `next_retry_at` 非空，`current_stage` 为失败阶段，存在一个 `FAILED` 步骤，后续步骤均为 `PENDING`，`last_error_code` 非空。
3. `task.status = SUCCEEDED` ⇒ 三个步骤全部 `SUCCEEDED`，结果字段非空，`finished_at` 非空。
4. `task.status = FAILED` ⇒ 存在一个 `FAILED` 步骤；其后步骤全部 `SKIPPED`；失败码非空，`finished_at` 非空。
5. 新任务 `current_stage = NULL`；恢复后的 `QUEUED` 可以保留 `current_stage` 与 `started_at`，且 `resume_count > 0`。
6. `task.started_at` 非空 ⇔ 任务至少被认领过一次；`finished_at` 非空 ⇔ 状态是终态。
7. 任何终态任务不得再被 worker 认领（认领条件只接受 `QUEUED` 或到期 `RETRYING`）。
8. `step.seq` 唯一且为 1/2/3；`(task_id, stage)` 唯一。
9. `task_step.attempt_count` 等于该步骤非 `INTERRUPTED` 的 attempt 数；每步最多为 `max_step_attempts`。
10. `task.retry_count` 等于所有步骤“可重试失败后安排重试”的次数之和；中断不计入。
11. `task_step_attempt` 的 `(step_id, attempt_no)` 唯一；同一时刻每个步骤最多一个 `RUNNING` attempt。
12. 素材所有者 = 任务所有者（创建时校验，数据库无法表达，由服务层保证）。
13. 任务结果路径与素材路径不重叠，结果只属于一个任务。
14. 消息与失败原因只以“稳定码 + 参数”存储；已翻译文本不进入数据库。

## 9. 三阶段职责

| 阶段 | 输入 | 工作 | 输出 | 可注入失败 |
|---|---|---|---|---|
| `MATERIAL_CHECK` | 任务快照、素材列表 | 等待配置耗时；复核数量、文件存在、可解码、尺寸；生成素材摘要 | `message_args={checked,required}`；task.current_stage | `MATERIAL_CHECK_FAILED` |
| `GENERATE` | 素材摘要、画布参数 | 等待配置耗时；计算版式（1/2/3 张的槽位）；不调用任何外部服务 | `message_args={width,height}`；task.current_stage | `GENERATION_FAILED` |
| `FINALIZE` | 素材文件、版式、画布 | 等待配置耗时；Java2D 合成、绘制英文水印与任务短码；写临时文件、原子移动、更新任务结果 | 结果文件、宽高、字节数；step3 SUCCEEDED；task SUCCEEDED | `RESULT_RENDER_FAILED` / `STORAGE_UNAVAILABLE` |

规则：

1. 每个阶段开始执行前，attempt 与步骤 `RUNNING` 必须已由**认领事务**或**前一步成功事务**提交；随后执行耗时工作，结束时再开事务写结果。**禁止在事务中睡眠或渲染**。
2. 模拟耗时在每个阶段开始时读取，修改配置只影响尚未开始的阶段。
3. 结果合成规则固定、可重复：
   - 1 张素材：铺满画布，底部渐变遮罩 + 英文水印。
   - 2 张素材：左右各 50%，中间 12px 留白。
   - 3 张素材：上方 62% 一张，下方 38% 两张并排。
   - 统一在右下角绘制 `FRAMELAB · {taskId 前 8 位}`，字体为打包的 OFL 英文字体。
4. 结果只写一次；若任务已是终态，任何迟到的写入必须拒绝（`WHERE status='RUNNING'`）。
5. 步骤每次执行都从该步骤开头重来；已成功的前序步骤不重跑，步骤内部结果不跨尝试复用。
6. 成功消息不保存句子，只保存 `message_args`；展示时按 `task.step.<STAGE>.succeeded` 与请求语言渲染。

## 10. 失败模型

### 10.1 运行期失败码

| 失败码 | 可能阶段 | 触发 | 默认可重试 | 可模拟 |
|---|---|---|---|---|
| `MATERIAL_CHECK_FAILED` | MATERIAL_CHECK | 数量不符、文件缺失、无法解码、模拟注入 | 否 | ✓ |
| `GENERATION_FAILED` | GENERATE | 模拟注入、版式计算异常 | 否（模拟可标记为可重试） | ✓ |
| `RESULT_RENDER_FAILED` | FINALIZE | 合成异常、字体加载失败 | 是 | ✓ |
| `STORAGE_UNAVAILABLE` | 任意 | 磁盘写满、权限错误、原子移动失败 | 是 | ✗ |
| `INTERNAL_ERROR` | 任意 | 未捕获异常 | 是 | ✗ |

约束：

- `task.failure_code` 与失败步骤的 `error_code`、最后一次 attempt 的 `error_code` 必须一致。
- `task.failure_args` 保存本地化参数（如 `{"slotIndex":2}`）；`failure_detail` 保存英文技术诊断，不直接展示。
- 模拟注入导致的最终失败，`failure_simulated=1`；用户文案由 `task.failure.<CODE>` 与 `simulated` 参数渲染，自动带“模拟”说明；非注入失败必须为 0。
- 失败是否重试由**失败码 + 模拟标记**决定：可重试且 `attempt_count < max_step_attempts` → `RETRYING`；否则 → `FAILED`。
- `RETRYING` 期间任务不写 `failure_code`，只写 `last_error_*`；最终失败时才把最后错误转存为 `failure_*`。
- 失败后不自动无上限重试；达到上限或不可重试时用户可重新创建任务（新任务、新 ID）。
- 进程重启不再产生 `TASK_INTERRUPTED` 失败；中断由 attempt 的 `INTERRUPTED` 与任务的 `resume_count` 表达。
- 读取时按请求语言渲染消息，见 [10 · 任务消息本地化](10-reliability-i18n.md#56-任务消息本地化)。

### 10.2 提交期错误（非任务失败）

数量不符、素材不存在、模板下架、类型不支持等属于**请求错误**，返回 4xx，不创建任务，不产生失败记录。完整错误码见 [04 · 错误模型](04-api.md#3-错误模型)。

## 11. 模拟控制

### 11.1 配置

| 字段 | 范围 | 默认 | 语义 |
|---|---|---|---|
| `material_check_delay_ms` | 0～60000 | 800 | 素材检查耗时 |
| `generate_delay_ms` | 0～60000 | 3000 | 模拟生成耗时 |
| `finalize_delay_ms` | 0～60000 | 1200 | 结果整理耗时 |
| `fail_stage` | NULL / 三阶段 | NULL | 下一次命中的失败阶段 |
| `fail_times` | 0～50 | 0 | 还需注入失败的次数 |
| `fail_retryable` | 0 / 1 | 0 | 注入的失败是否按可重试失败处理 |
| `updated_by` | 管理员 ID | NULL | 最近修改人 |
| `updated_at` | UTC | — | 最近修改时间 |

### 11.2 注入语义

1. 每个步骤开始时读取当前配置；若 `fail_stage == 当前阶段` 且 `fail_times > 0`，在**同一事务**中执行：
   ```sql
   UPDATE simulation_setting
      SET fail_times = fail_times - 1
    WHERE id = 1 AND fail_stage = ? AND fail_times > 0;
   ```
   影响行数为 1 才注入失败；为 0 则正常执行。
2. 注入失败抛出带失败码与 `retryable` 标记的异常，由流水线统一收口：
   - `retryable=0` 或预算耗尽：当前步 `FAILED`、后续 `SKIPPED`、任务 `FAILED`、`failure_simulated=1`；
   - `retryable=1` 且预算未耗尽：attempt/step `FAILED`、任务 `RETRYING`、`last_error_*`（含 `last_error_simulated=1`）；最终成功则清空 `last_error_*`，最终失败才转存为 `failure_*`。
3. 注入失败只发生对应次数；同一任务的重试会再次进入阶段并再次消耗 `fail_times`，因此 `remaining=2` 可演示“前两次失败、第三次成功”。
4. 注入的 `retryable` 只影响本次注入，不改变失败码的默认可重试性；管理员再次设置可改变下一次行为。
5. 配置是**全局单例**，所有用户共享；并发创建多个任务时，失败会落在最先进入该阶段的任务上。演示失败时建议一次只创建一个任务；P1 可扩展按用户定向。
6. 耗时配置在步骤开始时读取；修改不会中断正在睡眠的步骤。
7. 不实现随机失败；模拟必须可重复、可解释。

## 12. 幂等、并发与终态

| 场景 | 处理 |
|---|---|
| 用户双击提交 | 前端禁用按钮；同一 `clientRequestId` 的服务端请求只创建一个任务 |
| 前端网络重试 | 复用同一 `clientRequestId`；返回原任务并带 `Idempotent-Replay: true` |
| 同键不同载荷 | 返回 409 `IDEMPOTENCY_KEY_REUSED`，不创建第二个任务 |
| 两个请求并发插入 | 唯一索引只允许一个成功；失败方转为重放读取 |
| 两个 worker 同时认领 | `SELECT ... FOR UPDATE SKIP LOCKED`，只有一个成功 |
| 两个 worker 处理同一任务（异常情况） | 所有状态写入带 `WHERE status IN ('RUNNING','RETRYING')` 与 `row_version`；影响行数为 0 则放弃，不覆盖结果 |
| 查询与写入并发 | 查询只读已提交数据；前端以任务 ID 为键更新，不合并不同任务 |
| 任务已终态又收到查询 | 返回终态快照；永不回退 |
| 迟到步骤写入 | 拒绝；记录警告日志 |
| 进程在步骤中途崩溃 | 重启恢复把 `RUNNING` 任务置 `QUEUED`，中断 attempt 标 `INTERRUPTED`，从第一个未成功步骤续跑 |
| 重试等待期间再次查询 | 只读返回 `RETRYING` 与 `nextRetryAt`；不提前执行、不修改状态 |

`clientRequestId` 规则：

- 前端在每次“点击提交”时生成 UUID；同一次提交的重试必须复用；用户再次主动创建则生成新的 UUID。
- 服务端不信任格式之外的含义；格式必须是 UUID，长度 36。
- 唯一索引 `(user_id, client_request_id)`；冲突时读取已有任务返回，不抛 500。
- 同一键的载荷比较 `templateId` 与 `assetIds` 顺序；不同则 409。
- 重放优先于模板/素材校验，保证模板下架后仍能取回已受理任务。

## 13. 所有权与可见性

| 资源 | 读 | 写 | 他人访问 |
|---|---|---|---|
| 模板封面 | 公开 | 仅种子/运维 | 可见 |
| 上传素材 | 所有者 | 所有者上传/删除 | 404 |
| 任务与步骤 | 所有者 | 仅 worker 与提交服务 | 404 |
| 任务结果 | 所有者 | 仅 worker | 404 |
| 模拟配置 | ADMIN | ADMIN | 403 |

实现要求：

- SQL 层必须写 `WHERE user_id = ?`，不能只靠控制器判断。
- 用“查询不到”统一返回 404，禁止先查存在再返回 403，避免枚举他人任务 ID。
- 结果 URL 不包含可猜测的公共路径；即使拿到 URL，也需要有效访问令牌与所有者身份。

## 14. 生命周期与清理

| 对象 | 保留 | 清理 |
|---|---|---|
| 访问令牌 | 15 分钟 | 自然过期；服务端不存储 |
| 刷新令牌 | 家族绝对 7 天 | 保留用于审计；物理删除由 X2a 定时任务执行（见 [12](12-extension-plan.md)）；退出即撤销家族 |
| 上传素材 | 直至用户删除 | 未绑定且创建超过 7 天的素材由 P1 定时任务清理 |
| 任务 / 步骤 | 演示期永久 | P0 不删除 |
| 步骤尝试 | 与任务同寿命 | 不单独删除；用于审计与重试历史 |
| 模板 / 分类翻译 | 与模板同寿命 | 随种子维护；缺失时回退中文 |
| 结果文件 | 与任务同寿命 | 不单独删除 |
| 模拟配置 | 永久单行 | 不删除，可重置为默认值 |

## 15. 枚举汇总

```text
Role:         USER | ADMIN
UserStatus:   ACTIVE | DISABLED

TaskStatus:   QUEUED | RUNNING | RETRYING | SUCCEEDED | FAILED
TaskStage:    MATERIAL_CHECK | GENERATE | FINALIZE
StepStatus:   PENDING | RUNNING | SUCCEEDED | FAILED | SKIPPED
AttemptStatus: RUNNING | SUCCEEDED | FAILED | INTERRUPTED

FailureCode:  MATERIAL_CHECK_FAILED | GENERATION_FAILED | RESULT_RENDER_FAILED
              | STORAGE_UNAVAILABLE | INTERNAL_ERROR

Locale:       zh-CN | en-US
```

任何新增枚举值都必须同时更新：本文、[03 DDL](03-database.md)、[04 契约](04-api.md)、[06 状态矩阵](06-frontend-design.md#11-状态反馈与文案)、[10 可靠性](10-reliability-i18n.md) 与前后端测试。
