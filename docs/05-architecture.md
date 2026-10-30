# 05 · 架构、模块边界与异步流水线

> 本文回答“代码怎么分层、请求怎么走、任务怎么执行、安全边界在哪里”。
> 接口字段见 [04-api.md](04-api.md)，视觉与页面结构见 [06-frontend-design.md](06-frontend-design.md)。

## 1. 架构原则

1. **模块化单体**：一个 Java 进程、一个 MySQL、一个本地存储卷；代码按业务能力垂直切分，不按 `controller/service/mapper` 技术类型平铺整个仓库。首版不拆微服务，避免为演示项目引入分布式事务、注册中心与网络故障。
2. **依赖单向**：`controller → service → mapper`；`worker → task service → mapper`。模块之间只通过 service 的公开方法协作，禁止跨模块直接调用 Mapper 或写别人的 SQL。
3. **服务端是事实源**：任务状态、阶段、结果、失败原因只在 MySQL 中变化；前端轮询只读。异步执行只发生在 worker，HTTP 查询永远无副作用。
4. **阶段可观察**：三个步骤各自有开始/结束时间与独立事务；耗时期间不持有数据库事务；失败后立即停止并留下阶段、失败码、原因。
5. **用户隔离**：控制器、服务、SQL 三层都带 `user_id` 条件；他人资源统一 404。
6. **无状态 API + 有状态任务**：HTTP 层不存 Session；任务状态持久化在数据库，进程重启不丢已受理任务；`RUNNING` 任务在重启后按步骤续跑。
7. **可解释优先**：不引入 MQ、Redis、SSE、对象存储；每个技术选择在 §11 写明理由与代价。可靠性扩展只使用 MySQL 的幂等键、状态字段、尝试记录与 `SKIP LOCKED`。
8. **文案与代码分离**：后端只存稳定错误码与消息参数，读取时按 `Accept-Language` 渲染；前端词典与模板翻译表各司其职，不在数据库或组件里写死某一种语言。

## 2. 系统上下文

```
┌──────────────────────────────────────────────────────────────────┐
│ Browser                                                          │
│  Next.js App Router（React 19，客户端组件为主）                    │
│  TanStack Query：模板/任务/历史缓存与轮询                          │
│  Zustand：访问令牌、当前用户、启动状态                             │
│  Locale：NEXT_LOCALE Cookie + TS 词典 + Intl                      │
└───────────────┬──────────────────────────────────────────────────┘
                │ HTTPS JSON + Bearer / multipart
                │ HttpOnly refresh cookie（仅 /api/auth）
┌───────────────▼──────────────────────────────────────────────────┐
│ Java 21 · Spring Boot 3.4 · Spring Security 6                    │
│                                                                  │
│  /api/auth        AuthController → AuthService → UserMapper       │
│  /api/templates   TemplateController → TemplateService（翻译）     │
│  /api/assets      AssetController → AssetService → Storage        │
│  /api/tasks       TaskController → Submission/QueryService        │
│  /api/admin/...   SimulationController（ROLE_ADMIN）              │
│                                                                  │
│  StartupRecovery ──▶ RUNNING → QUEUED（续跑）                     │
│  TaskWorker ×2 ──认领──▶ TaskPipeline ──▶ 3 StepRunner            │
│       │                    │                    │                 │
│       └──────────── TaskMapper / StepMapper / AttemptMapper ◀────┘ │
│                                                                  │
│  MessageSource（zh-CN / en-US）+ 模板翻译查询                      │
│  LocalObjectStorage：storage/materials、storage/results           │
└───────────────┬──────────────────────────────────────────────────┘
                │ MyBatis XML（参数化 SQL）
┌───────────────▼──────────────────────────────────────────────────┐
│ MySQL 8.4：app_user / template / task / task_step / ...           │
└──────────────────────────────────────────────────────────────────┘
```

信任边界：

- 浏览器不可信：所有数量、类型、大小、归属、状态全部服务端重验。
- Next.js 不是业务后端：不持有数据库连接，不执行任务，不保存个人记录；只做页面与 API 调用。
- 文件系统不是公开目录：素材与结果只能通过鉴权接口读取；仅模板封面作为公开静态资源。
- 管理端配置接口不返回任何用户作品数据。

## 3. 仓库目录结构

```
SC-26-10-30/
├─ docs/                          # 本规范套件 + assets/reference
├─ backend/                       # Spring Boot 3
│  ├─ pom.xml
│  ├─ mvnw / mvnw.cmd
│  ├─ Dockerfile
│  └─ src/
├─ frontend/                      # Next.js App Router
│  ├─ package.json
│  ├─ pnpm-lock.yaml
│  ├─ next.config.ts
│  ├─ Dockerfile
│  └─ src/
├─ docker-compose.yml
├─ .env.example
├─ .github/workflows/ci.yml
├─ .gitignore
└─ README.md
```

根目录不放大段业务代码；前后端各自可独立构建。`storage/`、`.env`、`node_modules/`、`target/`、`.next/` 不入库。

## 4. 后端结构

### 4.1 包结构

```
backend/src/main/java/com/framelab/
├─ FrameLabApplication.java
├─ common/
│  ├─ api/            PageResponse、ApiPaths
│  ├─ error/          ErrorCode、ApiException、GlobalExceptionHandler、ProblemFactory
│  ├─ security/       AuthUser、CurrentUser、SecurityUtils
│  ├─ i18n/           LocaleSupport、MessageResolver、AcceptLanguageFilter
│  ├─ time/           ClockConfig、TimeFormat
│  └─ util/           Uuids
├─ config/
│  ├─ SecurityConfig.java
│  ├─ CorsConfig.java
│  ├─ MyBatisConfig.java
│  ├─ WorkerConfig.java
│  ├─ RetryProperties.java
│  ├─ StorageConfig.java
│  ├─ JacksonConfig.java
│  └─ WebConfig.java
├─ auth/
│  ├─ AuthController.java
│  ├─ AuthService.java
│  ├─ JwtTokenService.java
│  ├─ RefreshTokenService.java
│  ├─ LoginRateLimiter.java
│  ├─ JwtAuthenticationFilter.java
│  └─ dto/
├─ user/
│  ├─ User.java
│  ├─ UserMapper.java
│  └─ UserService.java
├─ template/
│  ├─ TemplateController.java
│  ├─ TemplateService.java
│  ├─ TemplateMapper.java
│  └─ dto/
├─ asset/
│  ├─ AssetController.java
│  ├─ AssetService.java
│  ├─ AssetMapper.java
│  ├─ ImageNormalizer.java
│  ├─ AssetContentService.java
│  └─ dto/
├─ task/
│  ├─ TaskController.java
│  ├─ TaskSubmissionService.java
│  ├─ TaskQueryService.java
│  ├─ TaskMapper.java
│  ├─ TaskStepMapper.java
│  ├─ TaskStepAttemptMapper.java
│  ├─ TaskStepService.java
│  ├─ domain/         TaskStatus、TaskStage、StepStatus、AttemptStatus、
│  │                  FailureCode、RetryPolicy、TaskFailureException
│  ├─ worker/         TaskWorker、TaskClaimService、TaskPipeline、
│  │                  RetryScheduler、StartupRecovery
│  ├─ step/           StepRunner、MaterialCheckStep、GenerateStep、FinalizeStep
│  ├─ render/         LayoutPlanner、ResultRenderer
│  └─ dto/
├─ simulation/
│  ├─ SimulationController.java
│  ├─ SimulationService.java
│  ├─ SimulationMapper.java
│  └─ dto/
└─ infrastructure/
   └─ storage/        ObjectStorage、LocalObjectStorage、StorageException
```

资源目录：

```
backend/src/main/resources/
├─ application.yml
├─ application-dev.yml
├─ application-docker.yml
├─ db/migration/V1__schema.sql
├─ db/migration/V2__seed.sql
├─ i18n/messages.properties
├─ i18n/messages_zh_CN.properties
├─ i18n/messages_en_US.properties
├─ mapper/            TemplateMapper.xml、TaskMapper.xml、TaskStepMapper.xml、
│                     TaskStepAttemptMapper.xml ...
├─ static/assets/templates/*.webp
├─ static/assets/CREDITS.md
└─ fonts/Inter-SemiBold.ttf + OFL.txt
```

### 4.2 模块职责

| 模块 | 负责 | 不负责 |
|---|---|---|
| `auth` | 登录、签发/校验 JWT、刷新令牌、登录限流 | 用户资料修改、注册、第三方登录 |
| `user` | 按 ID/邮箱读用户、校验状态 | 签发令牌、写任务 |
| `template` | 分类与模板只读查询、上下架过滤、按语言取翻译 | 模板 CRUD（P0 无） |
| `asset` | 上传校验、规范化、落盘、内容流、删除 | 任务创建、结果渲染 |
| `task` | 提交幂等、任务与步骤持久化、尝试记录、查询、worker、重试调度、启动恢复、流水线、结果渲染 | 鉴权逻辑、模板维护 |
| `simulation` | 单例配置读写、失败注入消费、可重试标记 | 用户任务数据 |
| `common.i18n` | `Accept-Language` 解析、`MessageSource` 封装、响应语言头 | 业务文案组织、模板翻译存储 |
| `infrastructure.storage` | 本地文件读写、原子移动、路径生成 | 业务权限判断 |

### 4.3 依赖规则

```
controller ──▶ service ──▶ mapper ──▶ MySQL
                   │
                   ├─▶ ObjectStorage
                   ├─▶ ImageNormalizer
                   └─▶ 其他模块的只读 service（不得直接调用其 Mapper）

worker ──▶ TaskPipeline ──▶ StepRunner ──▶ TaskStepService / AssetService / TemplateService
```

- `controller` 只做参数绑定、鉴权注解、DTO 转换与状态码；不写 SQL、不 sleep、不读文件。
- `service` 是事务边界与用例编排；不依赖 `HttpServletRequest`，便于单元测试。
- `mapper` 只做 SQL 与行映射；动态 SQL 只允许 `#{}`，禁止 `${}` 拼接。
- `common` 不依赖任何业务模块。
- `task` 需要素材信息时调用 `AssetService.getOwnedAsset(assetId, userId)`；需要模板快照时调用 `TemplateService.requirePublished(templateId)`。
- `asset` 需要模板限制时调用 `TemplateService.findLimits(templateId)`；不反向依赖 `task`。
- 不引入循环依赖；如出现，抽只读查询服务，而不是互相注入。

### 4.4 事务边界

| 操作 | 事务 | 说明 |
|---|---|---|
| 创建任务 | 一个事务 | task + 3 steps + materials；幂等键冲突时回滚并读取已有任务 |
| worker 认领 | 一个短事务 | `SKIP LOCKED` + task RUNNING + 目标 step RUNNING + attempt RUNNING |
| 模拟注入消费 | 独立短事务 | 先提交 `fail_times-1`，再决定是否失败；避免回滚把次数还回去 |
| 步骤工作 | 无事务 | 睡眠、解码、合成；不得长时间占用连接 |
| 步骤成功（非最后一步） | 一个短事务 | attempt SUCCEEDED + step SUCCEEDED + message_args + 下一步 RUNNING + 新 attempt RUNNING + current_stage |
| 最后一步成功 | 一个事务 | step3 SUCCEEDED + attempt SUCCEEDED + task SUCCEEDED + 结果字段 |
| 可重试失败 | 一个事务 | attempt FAILED + step FAILED + task RETRYING + `next_retry_at` / `last_error_*` / `retry_count+1` |
| 不可重试/耗尽失败 | 一个事务 | attempt FAILED + 当前步 FAILED + 后续 SKIPPED + task FAILED |
| 启动恢复 | 一个事务 | 活动 attempt → INTERRUPTED；RUNNING step → PENDING；RUNNING task → QUEUED；`resume_count+1` |
| 上传素材 | 文件落盘 + 一个短事务 | 先写文件，再入库；入库失败清理文件 |
| 删除素材 | 一个短事务 + 事后删文件 | 先查引用，再删记录，提交后删文件 |

> 事务中禁止：`Thread.sleep`、图片解码/合成、网络调用、文件大块读写。违反会导致连接池耗尽、锁等待和任务看起来“卡住”。重试等待不在事务中 sleep，只写 `next_retry_at` 后立即释放连接。

## 5. 关键请求流程

### 5.1 登录

```
POST /api/auth/login
  → AuthController 校验 DTO
  → LoginRateLimiter 检查失败次数
  → UserService.findByEmail（小写归一）
  → BCrypt.matches
  → JwtTokenService 签发 15 分钟访问令牌
  → RefreshTokenService 创建 family，生成 32 字节随机串，存 SHA-256 与 family_expires_at
  → Set-Cookie: framelab_rt=...; HttpOnly; SameSite=Lax
  → 200 { accessToken, expiresIn, user }
```

### 5.2 上传素材

```
POST /api/assets (multipart)
  → 鉴权；templateId 可选 → TemplateService 取限制
  → AssetService 校验大小/类型/魔数
  → ImageNormalizer 解码、方向纠正、缩放、重编码
  → ObjectStorage 写临时文件 → 原子移动
  → INSERT upload_asset（user_id 来自登录态）
  → 201 Asset
```

### 5.3 提交任务

```
POST /api/tasks
  → 鉴权
  → TaskSubmissionService 事务：
       按 (user_id, clientRequestId) 查重
         命中且载荷一致 → 返回已有任务（200 + Idempotent-Replay: true）
         命中但载荷不一致 → 409 IDEMPOTENCY_KEY_REUSED
       模板存在且上架
       assetIds 数量精确匹配、无重复
       逐个 AssetService 校验归属 + MIME + 大小
       INSERT task（含 max_step_attempts 快照）+ steps + materials
  → 202 + Location + TaskDetail
```

提交成功即返回，不等待 worker；最迟一个轮询周期内状态变为 RUNNING。并发插入由唯一索引兜底，失败方转为重放读取。

### 5.4 查询任务

```
GET /api/tasks/{id}
  → 鉴权
  → TaskQueryService 按 user_id + id 查询任务与步骤
  → 找不到 → 404 TASK_NOT_FOUND
  → 组装 DTO（result.url 仅在成功时存在）
  → 200，Cache-Control: no-store
```

查询不触发任何状态变化。

## 6. 后端工作线程与任务流水线

### 6.1 为什么用数据库认领

| 方案 | 结论 |
|---|---|
| 内存 `ThreadPoolExecutor` 队列 | 进程重启丢失排队任务；无持久化；不满足“关闭页面不中断”之外的可靠性 |
| MQ（RabbitMQ/Kafka） | 演示规模过重，增加部署与运维；题目不要求 |
| 数据库任务表 + `SKIP LOCKED` | 状态天然持久、可排队、可观察、可重试、可续跑；MySQL 8 支持 |
### 6.2 线程模型

- 默认 2 个 worker 线程（`FRAMELAB_WORKER_THREADS`），每个循环：
  1. 认领一个 `QUEUED` 或**到期的 `RETRYING`** 任务；没有则退避 500 ms。
  2. 从第一个非 `SUCCEEDED` 步骤开始顺序执行。
  3. 回到 1。
- 两个任务最多并行；其余在数据库排队，前端显示“排队中”或“等待重试”。
- 任务按 `created_at, id` 先进先出；重试任务保持原创建时间，不因重试获得额外优先级。
- worker 线程内的任何异常都必须被捕获：已映射的业务失败 → 按失败码决定重试或终态；未映射异常 → `INTERNAL_ERROR`（可重试）；线程本身不退出。
- 优雅停机：停止认领、等待当前步骤完成（最多 30 秒）；未完成的任务保持 `RUNNING`，下次启动由 `StartupRecovery` 回收为 `QUEUED` 并续跑。
- 重试等待不占用线程：写入 `next_retry_at` 后 worker 立即处理下一个任务。

### 6.3 认领（防重复）

```sql
START TRANSACTION;

SELECT id
  FROM task
 WHERE status = 'QUEUED'
    OR (status = 'RETRYING' AND next_retry_at <= UTC_TIMESTAMP(3))
 ORDER BY created_at, id
 LIMIT 1
 FOR UPDATE SKIP LOCKED;

-- 命中后：
UPDATE task
   SET status = 'RUNNING',
       current_stage = ?,
       next_retry_at = NULL,
       last_error_code = NULL,
       last_error_detail = NULL,
       last_error_simulated = NULL,
       started_at = COALESCE(started_at, UTC_TIMESTAMP(3)),
       row_version = row_version + 1
 WHERE id = ? AND status IN ('QUEUED','RETRYING')
   AND (status = 'QUEUED' OR next_retry_at <= UTC_TIMESTAMP(3));

-- 目标步骤 = 第一个非 SUCCEEDED 步骤（PENDING 或重试的 FAILED）
UPDATE task_step
   SET status = 'RUNNING',
       started_at = UTC_TIMESTAMP(3),
       finished_at = NULL,
       error_code = NULL,
       attempt_count = attempt_count + 1
 WHERE task_id = ? AND seq = ? AND status IN ('PENDING','FAILED');

INSERT INTO task_step_attempt
  (task_id, step_id, attempt_no, status, started_at)
SELECT task_id, id,
       (SELECT COALESCE(MAX(attempt_no), 0) + 1
          FROM task_step_attempt WHERE step_id = task_step.id),
       'RUNNING', UTC_TIMESTAMP(3)
  FROM task_step
 WHERE task_id = ? AND seq = ?;

COMMIT;
```

- 两个 worker 同时认领时，`SKIP LOCKED` 让后者跳过被锁行。
- 更新条件带 `status IN ('QUEUED','RETRYING')`，即使重复认领也不会二次执行。
- 认领事务只做状态写入，不加载大字段、不做文件操作。
- 校验影响行数：task 更新 0 行、step 更新 0 行或 attempt 插入失败时回滚并记录告警，不进入流水线。
- 目标步骤由服务层先查询并按 `seq` 升序选择；`current_stage` 取该步骤的 `stage`，恢复任务会跳过已成功步骤。

### 6.4 流水线

```
TaskPipeline.run(taskId)
  │
  ├─ 读取 task + steps（按 seq）
  ├─ 跳过所有 SUCCEEDED；首个目标步骤已在认领事务中置 RUNNING 并创建 attempt，
  │  后续步骤由前一步的成功事务启动
  │
  ├─ Step 1 MATERIAL_CHECK（若未成功）
  │    delay = simulation.materialCheckMs
  │    sleep(delay)  ── 无事务
  │    consumeFailure(MATERIAL_CHECK, retryable) ── 独立短事务
  │    校验素材数量/存在/可解码
  │    finishStepAndStartNext(1, SUCCEEDED, message_args={checked,required})
  │      └─ 同一事务：step1 SUCCEEDED + step2 RUNNING + attempt2 RUNNING + current_stage
  │
  ├─ Step 2 GENERATE
  │    sleep(generateMs)
  │    consumeFailure(GENERATE, retryable)
  │    LayoutPlanner.plan(count, canvas)
  │    finishStepAndStartNext(2, SUCCEEDED, message_args={width,height})
  │      └─ 同一事务：step2 SUCCEEDED + step3 RUNNING + attempt3 RUNNING + current_stage
  │
  └─ Step 3 FINALIZE
       sleep(finalizeMs)
       consumeFailure(FINALIZE, retryable)
       ResultRenderer.render(...) → storage/results/{userId}/{taskId}.jpg
       原子移动 + 计算宽高/字节
       finishFinalSuccess(3, result) ── 一个事务：step3 + attempt3 + task SUCCEEDED
```

步骤成功与下一步启动在同一事务中完成，因此不存在“任务 RUNNING 但没有 RUNNING 步骤”的已提交状态；若事务回滚，当前步骤仍为 RUNNING，恢复后整步重跑。

失败路径：

```
catch TaskFailureException e
  if e.retryable && step.attempt_count < task.max_step_attempts:
    TX:
      attempt → FAILED（error_code/error_detail/finished_at）
      step → FAILED（error_code/finished_at）
      task → RETRYING（next_retry_at=now+backoff(attempt_count),
                       current_stage=e.stage,
                       last_error_code/last_error_detail/last_error_simulated,
                       retry_count+1, row_version+1）
    COMMIT
    清理本次临时文件（尽力而为）
    返回；worker 继续处理其他任务，到期后重新认领
  else:
    TX:
      attempt → FAILED（error_code/error_detail/finished_at）
      step → FAILED（error_code/finished_at）
      后续 PENDING → SKIPPED
      task → FAILED（failure_code/failure_args/failure_detail/failure_simulated,
                     finished_at, last_error_*=NULL, row_version+1）
    COMMIT
    清理本次临时文件（尽力而为）
```

未捕获异常 → 包装为 `INTERNAL_ERROR`（默认可重试）走同一路径；错误详情只写日志与 `*_detail`，不返回堆栈。

步骤成功消息只写 `message_args`，展示时按语言渲染；失败原因只写稳定码与参数，不在数据库保存翻译后的句子。

### 6.5 启动恢复（断点续跑）

`StartupRecovery` 在 Flyway 之后、worker 开始认领之前执行：

1. 找出所有 `status='RUNNING'` 的任务（说明上次进程在步骤中途退出）。
2. 对每个任务加行锁，读取 steps 与 attempts。
3. 仍为 `RUNNING` 的 attempt → `INTERRUPTED`（`finished_at=now`）。
4. 仍为 `RUNNING` 的 step → `PENDING`（清空时间与错误）；若该步骤有刚被中断的 attempt，则 `attempt_count-1`。
5. 任务 → `QUEUED`，保留 `started_at` 与 `current_stage`，清空 `next_retry_at` / `last_error_*`，`resume_count+1`。
6. 清理该任务的临时目录 `storage/tmp/task-{taskId}/`。
7. 恢复事务提交后，worker 才启动；worker 认领后从第一个非 `SUCCEEDED` 步骤继续。

```
重启前：RUNNING, step2 RUNNING, attempt2 RUNNING
重启后：QUEUED, step1 SUCCEEDED, step2 PENDING, attempt2 INTERRUPTED,
        resume_count+1, attempt_count 回退
续跑：  认领 → step2 RUNNING, attempt3 RUNNING → 后续步骤正常执行
```

规则：

- **已成功步骤不重跑**：时间戳、消息、结果保持不变。
- **中断步骤整步重跑**：不保留步骤内部进度；结果文件按任务固定路径覆盖。
- **不消耗重试预算**：`INTERRUPTED` 不增加 `retry_count`，`attempt_count` 回退。
- **`RETRYING` 不恢复**：退避时间保持；到期后正常认领。
- **终态不触碰**：`SUCCEEDED` / `FAILED` 不扫描、不修改。
- 若发现任务所有步骤已成功但结果字段完整（只差最终提交），恢复为 `SUCCEEDED`，不重跑。
- 恢复失败（数据库异常等）时应用启动失败并重试，不允许未恢复就启动 worker。

这不是“把中断当失败”：用户看到的是 `QUEUED`（`resumeCount>0`）→ `RUNNING`，而不是 `FAILED`。单实例假设：如果同时运行两个 Java 实例，A 启动会把 B 正在执行的任务误回收；P0 只部署一个实例，多实例需引入心跳/租约（见 §13）。

### 6.6 自动重试

- 失败分类由 `FailureCode` 的 `retryable` 属性决定；模拟注入可用 `fail_retryable` 覆盖单次行为。
- 退避：`min(base × 2^(attempt_count-1), max)`，默认 2s → 4s，封顶 30s；`next_retry_at` 用数据库时间计算。
- 重试预算按步骤计算：`attempt_count < max_step_attempts` 时调度重试，否则终态失败。
- 每次执行创建新的 `task_step_attempt`；失败尝试保留，不覆盖。
- 重试等待期间任务 `RETRYING`，不占用 worker 线程；到期后与 `QUEUED` 共用认领路径。
- 不可重试失败不进入 `RETRYING`；`INTERNAL_ERROR` 默认可重试，但同样受上限约束。
- 详细状态转换见 [10 · E-02](10-reliability-i18n.md#3-e-02-自动重试)。

### 6.7 失败注入

- 每个步骤开始时读取 `simulation_setting` 的耗时与 `fail_stage/fail_times/fail_retryable`。
- 命中时通过独立事务原子递减 `fail_times`，提交后抛 `TaskFailureException`。
- 递减必须在失败事务之前提交，否则回滚会把次数恢复，导致“永远失败”。
- `fail_retryable=1` 时该次失败按可重试处理；同一任务重试会再次进入阶段并再次消耗 `fail_times`。
- 配置是全局单例，P0 不区分用户；并发多任务时失败落在最先进入该阶段的任务上。演示时一次只创建一个任务。

## 7. 结果合成

### 7.1 版式

| 素材数 | 布局 |
|---|---|
| 1 | 单图铺满画布，cover 裁剪；底部 20% 渐变遮罩 |
| 2 | 左右各 50%，中间 12 px 留白；各自 cover 裁剪 |
| 3 | 上方 62% 一张，下方 38% 两张并排；间距 12 px |

- 画布来自任务快照（创建时的模板 `canvas_width/height`）。
- 所有图片按 `max(scaleX, scaleY)` 居中裁剪，避免拉伸。
- 右下角绘制 `FRAMELAB · {taskId 前 8 位}`，使用打包的 `Inter-SemiBold.ttf`（OFL 许可）；容器内以 headless 模式运行，字体从 classpath 加载，不依赖系统字体。
- 输出 JPEG quality 0.9，无 EXIF；尺寸与画布一致。
- 不用中文绘制，避免容器缺字库。

### 7.2 原子写

```
storage/tmp/task-{taskId}/result-{uuid}.jpg
  → 写入、flush、close
  → Files.move(tmp, storage/results/{userId}/{taskId}.jpg, ATOMIC_MOVE)
  → 读取文件大小；宽高来自画布
  → 最后一步事务写 task 结果字段
```

- 临时文件位于任务专属目录，正常结束或启动恢复时清理整个目录。
- 若最终事务影响行数为 0（任务已被判定终态），删除新文件，记录警告。
- 若数据库写失败，尽力删除结果文件，避免孤立文件。
- 结果路径由服务端生成，不含用户文件名；读取时再次校验 `user_id`。

### 7.3 存储抽象

```java
interface ObjectStorage {
    StoredObject writeMaterial(long userId, String assetId, byte[] bytes, String ext);
    StoredObject writeResult(long userId, String taskId, Path tempFile);
    Resource open(String storageKey);
    void delete(String storageKey);
}
```

P0 实现 `LocalObjectStorage`；未来换 S3 只替换实现，不改任务服务。`storageKey` 永不接受客户端输入。

## 8. 前端状态与轮询

详细视觉与组件见 [06](06-frontend-design.md)，这里只定架构。

### 8.1 目录结构

```
frontend/
├─ package.json
├─ pnpm-lock.yaml
├─ next.config.ts              # output: 'standalone'
├─ tsconfig.json
├─ eslint.config.mjs
├─ vitest.config.ts
├─ Dockerfile
├─ .dockerignore
├─ public/                     # 仅 favicon 等静态资源；模板封面由 Java 提供
└─ src/
   ├─ app/                     # App Router：路由与页面装配
   │  ├─ layout.tsx
   │  ├─ globals.css
   │  ├─ page.tsx              # 模板列表
   │  ├─ loading.tsx
   │  ├─ error.tsx
   │  ├─ not-found.tsx
   │  ├─ templates/[templateId]/page.tsx
   │  ├─ history/page.tsx
   │  ├─ login/page.tsx
   │  └─ dev/simulation/page.tsx   # P1，ADMIN
   ├─ i18n/                    # 词典、LocaleProvider、Cookie 读写、Intl 格式化
   │  ├─ config.ts
   │  ├─ messages/zh-CN.ts
   │  ├─ messages/en-US.ts
   │  ├─ provider.tsx
   │  ├─ locale-cookie.ts
   │  └─ format.ts
   ├─ features/                 # 按业务切片
   │  ├─ auth/
   │  │  ├─ api.ts
   │  │  ├─ queries.ts
   │  │  ├─ store.ts            # 内存令牌、当前用户、启动状态
   │  │  ├─ schemas.ts
   │  │  └─ components/         # LoginForm、AuthBootstrap
   │  ├─ templates/
   │  │  ├─ api.ts
   │  │  ├─ queries.ts
   │  │  ├─ types.ts
   │  │  └─ components/         # CategoryChips、TemplateCard、TemplateGrid
   │  ├─ studio/
   │  │  ├─ api.ts
   │  │  ├─ queries.ts
   │  │  ├─ types.ts
   │  │  └─ components/         # TemplatePanel、UploadSlotGrid、UploadSlot、
   │  │                         # TaskHistoryPanel（复用 tasks 的任务卡）
   │  ├─ tasks/
   │  │  ├─ api.ts
   │  │  ├─ queries.ts
   │  │  ├─ types.ts
   │  │  ├─ hooks/useTaskPolling.ts
   │  │  └─ components/         # TaskCard、TaskStatusBadge、StageStepper、
   │  │                         # ResultDialog、TaskDetailDialog、TaskList
   │  └─ account/
   │     └─ components/AccountDialog.tsx、LocaleSwitcher.tsx
   ├─ shared/                   # 与业务无关的能力
   │  ├─ api/                   # client.ts、errors.ts、types.ts
   │  ├─ ui/                    # Button、Input、Dialog、Chip、Skeleton、Toast、
   │  │                         # EmptyState、ErrorState、Pagination
   │  ├─ lib/                   # cn、format、image、returnTo
   │  └─ config/env.ts
   ├─ assets/fonts/             # InterVariable.woff2、JetBrainsMono-Regular.woff2 + OFL
   └─ styles/tokens.css
```

依赖方向：

```
app/page → features/*/components → features/*/queries|hooks → features/*/api
                                                │
                                                ▼
                                        shared/api/client
shared/ui、shared/lib 不依赖任何 feature
features/studio 可复用 features/tasks 的卡片与轮询 hook，但不得直接调用其内部 api 实现
```

- `app/` 只做路由、布局、metadata 与页面装配，不写业务规则。
- 页面需要交互时使用 Client Component；`layout.tsx` 等静态外壳可为 Server Component。
- 不使用 Server Action / Route Handler 执行业务或持久化；所有数据经 `shared/api/client` 调用 Java。
- 类型与枚举集中在 `types.ts`，与 [04](04-api.md) 一致；不在组件里散落字符串。

### 8.2 状态分层

| 状态 | 归属 | 生命周期 |
|---|---|---|
| 访问令牌、当前用户、启动状态 | Zustand（内存） | 刷新页面丢失，由 refresh Cookie 恢复 |
| 当前语言 | `NEXT_LOCALE` Cookie + LocaleProvider | 一年有效；切换后更新 `<html lang>` 并清空查询缓存 |
| 模板列表、模板详情 | TanStack Query | 5 分钟 stale；分类切换按 key 缓存 |
| 任务列表、任务详情 | TanStack Query | `staleTime: 0`；按用户、语言与任务 ID 分 key |
| 上传草稿（已选文件、assetId、进度） | 组件 state / 局部 store | 离开页面即丢弃；不跨用户保留 |
| 提交中标志 | 组件 state | 请求结束即复位 |
| Toast | 全局 UI store | 短暂 |

### 8.3 查询键

```ts
['templates', locale, category ?? 'all']
['template', locale, templateId]
['template-categories', locale]
['tasks', userId, locale, { scope, templateId, page, size }]
['task', userId, locale, taskId]
['asset-image', userId, assetId]
['result-image', userId, taskId]
```

- 所有用户相关 key 必须带 `userId`；所有返回本地化文案的 key 必须带 `locale`。
- 登录、退出、切换账号时：取消所有请求 → `queryClient.clear()` → 清理 Blob URL → 重置上传草稿。
- 切换语言时：写 Cookie → 更新 Provider → 取消在途请求 → 使模板、任务、任务详情查询失效并重取；上传草稿与登录态保持不变。

### 8.4 轮询

- 列表：仅当当前页存在 `QUEUED/RUNNING/RETRYING` 时启用；`refetchInterval` 根据状态返回 `1500/2500/false`。
- 详情：`RUNNING` 1.5 s，`QUEUED` 2.5 s，`RETRYING` 2.5 s，终态 `false`。
- `refetchIntervalInBackground: false`；窗口重新聚焦时强制刷新一次。
- 请求失败：TanStack Query 保留 `data`；UI 显示“暂时无法更新”条，手动重试；不修改任务状态。
- 组件卸载、路由离开、用户切换：`AbortController.abort()` + 清理 interval。
- 多个任务共享一次列表轮询，不为每张卡片单独开定时器（降低请求数，避免相互覆盖）。
- 任务详情弹层独立轮询，只接受自己的 `taskId` 响应。
- 语言切换时取消旧语言请求，避免旧响应覆盖新语言界面。

### 8.5 请求客户端

```ts
async function apiFetch<T>(path: string, init?: RequestInit): Promise<T> {
  // 1. 注入 Bearer（若存在）
  // 2. 注入 Accept-Language（当前 locale，来自 NEXT_LOCALE）
  // 3. credentials 仅 /api/auth/* 使用 include
  // 4. 解析 problem+json → ApiError(code, status, detail, fieldErrors)
  // 5. 401 TOKEN_EXPIRED → 单飞 refresh → 重放一次原请求
  // 6. 401 其他 → 清会话；403/404/409/4xx → 抛给调用方
  // 7. 透传 AbortSignal
}
```

- 单飞刷新：模块级 `Promise`，并发 401 只发一次 refresh。
- 刷新失败：清空会话、取消查询、跳转 `/login?returnTo=当前路径`；不循环重试。
- 上传使用 XHR 以获得 `upload.onprogress`；同样携带 `Accept-Language`，支持 401 刷新后重试一次。
- 创建任务重试时复用同一个 `clientRequestId`；收到 202/200 后清空本次意图。
- 语言切换时取消所有在途请求，避免旧语言响应覆盖新词典与 API 文案。

## 9. 安全设计

### 9.1 Spring Security 过滤链

```java
http
  .csrf(csrf -> csrf.disable())
  .cors(Customizer.withDefaults())
  .sessionManagement(s -> s.sessionCreationPolicy(STATELESS))
  .authorizeHttpRequests(auth -> auth
      .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
      .requestMatchers("/error").permitAll()
      .requestMatchers("/api/auth/login", "/api/auth/refresh", "/api/auth/logout").permitAll()
      .requestMatchers(HttpMethod.GET, "/api/templates/**", "/api/template-categories").permitAll()
      .requestMatchers("/assets/templates/**", "/actuator/health").permitAll()
      .requestMatchers("/api/admin/**").hasRole("ADMIN")
      .requestMatchers("/api/**").authenticated()
      .anyRequest().denyAll())
  .exceptionHandling(e -> e
      .authenticationEntryPoint(problemAuthenticationEntryPoint)
      .accessDeniedHandler(problemAccessDeniedHandler))
  .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
```

- 关闭 CSRF 的理由见 [04 · 2.1](04-api.md#21-令牌模型)：Bearer 不随 Cookie 自动发送；刷新/退出只涉及会话，不改变业务数据。此决定必须在 README 中说明。
- `/api/admin/**` 除过滤链外，Controller 再加 `@PreAuthorize("hasRole('ADMIN')")`，形成双重保护；服务层不依赖 Web 注解。
- 没有 Session、没有表单登录、没有 Basic。
- JWT 过滤器：校验签名、`iss`、`aud`、`exp`，再按 `sub` 查询 `app_user.status` 与 `token_version`；只有 `ACTIVE` 且 `ver` 一致才构造 `AuthUser(id, email, role, ver)` 放入 SecurityContext。无效令牌不抛 500，由 EntryPoint 统一 401。
- 公开接口即使携带过期令牌也应可访问；过滤器只在令牌有效时建立身份，不阻断公开路径。

### 9.2 令牌与口令

| 项 | 设计 |
|---|---|
| 访问令牌 | HS256；密钥 ≥ 32 字节，来自 `FRAMELAB_JWT_SECRET`；15 分钟；`iss=framelab-api`、`aud=framelab-web` |
| 刷新令牌 | 32 字节 `SecureRandom` → Base64URL；只存 SHA-256；家族绝对 7 天；每次刷新轮换 |
| 轮换与重用 | 旧令牌标记 `ROTATED` 并写 `replaced_by_id`；30 秒宽限兜住多标签并发；超宽限重用撤销整个家族 |
| Cookie | `HttpOnly; SameSite=Lax; Path=/api/auth`；生产 `Secure`；前端 JS 不可读 |
| 口令 | BCrypt cost 12；最短 8 位（种子口令满足）；登录失败统一文案 |
| 登录限流 | Caffeine：同一 email+IP 15 分钟失败 5 次 → 429，`Retry-After`；成功清零 |
| 访问令牌版本 | JWT `ver` + 每请求主键查询 `token_version`；退出全部递增版本，禁用由状态校验拦截，旧令牌立即 401 |
| 注销 | 撤销当前家族 + 清 Cookie；其他设备访问令牌最多再活 15 分钟 |
| 退出全部 | 撤销用户全部家族 + 递增 `token_version` + 清 Cookie；所有设备立即失效 |
| 禁用用户 | 登录与刷新拒绝；过滤器校验状态，已签发访问令牌下一次请求即 401 |

密钥管理：

- 仓库只提交 `.env.example`，不含真实密钥。
- 应用启动时校验：生产部署（非 `dev` / `docker` 演示 profile）下密钥缺失、长度不足或等于示例值则拒绝启动。
- 开发 profile 可用固定开发密钥，但 README 标明仅本地使用。

### 9.3 CORS 与 Cookie

- 允许来源来自 `FRAMELAB_CORS_ALLOWED_ORIGINS`，逗号分隔；默认 `http://localhost:3000`。
- `dev` profile 额外允许 `http://localhost:*` 与 `http://127.0.0.1:*`（可用 `FRAMELAB_CORS_ALLOWED_ORIGIN_PATTERNS` 覆盖），避免 Next.js 因 3000 被占用自动切换端口时登录被 CORS 拒绝；`docker` 与生产 profile 不启用端口通配。
- `allowCredentials=true`；允许方法 `GET,POST,PUT,DELETE,OPTIONS`；允许头 `Authorization, Content-Type, Accept-Language`；暴露 `Location, Idempotent-Replay, X-Request-Id, Retry-After, Content-Language, Content-Disposition`。
- 不允许 `*` + credentials 的组合；通配仅用于本地回环地址的端口段。
- 开发时前端与后端都使用 `localhost`（不要一个 `127.0.0.1` 一个 `localhost`），否则 Cookie 域不一致。
- 生产建议同域反向代理（`/api` 与 `/assets` 转发到 Java），Cookie 路径不变。

### 9.4 资源归属

- 登录身份由 JWT 得出，**不接受**请求体/查询参数中的 `userId`。
- 每次查询都带 `WHERE user_id = ?`；任务详情、结果、素材内容、删除均先按“当前用户 + 资源 ID”查询。
- 他人资源与不存在资源统一 404，避免 ID 枚举。
- 管理端只读/写模拟配置，不提供“查看所有用户任务”的接口。

### 9.5 上传安全

| 威胁 | 措施 |
|---|---|
| 伪装图片 | 文件头魔数 + `ImageIO` 解码；不接受 SVG/HTML/压缩包 |
| 图片炸弹 | 像素总数 ≤ 4000 万；宽高 ≤ 8192；解码异常拒绝 |
| 路径穿越 | 服务端生成 UUID 路径；不读取原始文件名 |
| 覆盖他人文件 | 每次写入新 UUID 文件；不按用户输入命名 |
| 公开泄露 | 存储目录不在静态资源根下；内容接口鉴权 |
| EXIF 隐私 | 重编码去除元数据（保留方向已纠正） |
| 大文件 DoS | 单文件 ≤ 模板上限（≤ 10 MB）；请求体 ≤ 12 MB；Tomcat 限制 |
| 恶意内容分发 | `X-Content-Type-Options: nosniff`；`Content-Disposition: inline`；不缓存到公共代理 |

### 9.6 其他

- MyBatis 全部 `#{}` 参数化；排序字段固定，不拼接用户输入。
- React 默认转义；禁止 `dangerouslySetInnerHTML`；任务消息由服务端生成并限制长度。
- 日志不记录密码、令牌、Cookie、完整邮箱（可记录 userId）；错误响应不含堆栈与路径。
- 依赖版本锁定；CI 可加 `mvn dependency-check`（P1）。
- 前端 CSP 由 Next.js `headers()` 配置：`default-src 'self'; img-src 'self' blob: data: http://localhost:8080; connect-src 'self' http://localhost:8080;`，生产按域名调整。Blob URL 用于鉴权图片预览。

### 9.7 国际化架构

后端：

```
AcceptLanguageFilter（OncePerRequestFilter）
  → 解析 Accept-Language（q 权重、en/zh 别名）
  → 归一化为 zh-CN / en-US（不支持则 zh-CN）
  → LocaleContextHolder.setLocale(locale)
  → 响应写 Content-Language + Vary: Accept-Language
  → finally 清理，避免线程复用污染
```

- `MessageSource`：`basename=classpath:i18n/messages`、UTF-8、`fallbackToSystemLocale=false`；缺键回退默认 bundle。
- `ProblemFactory` 从 `ErrorCode` 取 `error.<CODE>`，字段错误取 `validation.<FIELD>.<RULE>`；`code` 保持英文。
- 任务查询把 `failure_code` / `last_error_code` / attempt `error_code` + JSON 参数交给 `MessageResolver`，在 DTO 组装时渲染 `message` / `errorMessage`。
- 模板与分类查询 `LEFT JOIN ..._translation ON locale = ?`；`COALESCE(translation, base)` 回退中文。
- `TaskSummary.templateTitle` 优先当前语言翻译，缺失时用任务快照；封面、画布、素材顺序不变。
- 错误码与失败码是稳定的英文枚举，不翻译；日志只记录码，不记录渲染后的句子。

前端：

- `src/i18n` 提供 `LocaleProvider`、`useI18n`、`useLocale`、`formatDate`、`formatNumber`。
- `NEXT_LOCALE` Cookie 由客户端切换写入；服务端组件读取同一 Cookie 决定首屏语言，避免 hydration 不一致。
- 词典只放 UI 文案；模板与任务消息来自 API；结果图水印固定英文。
- 查询键包含 locale；切换语言时取消在途请求并清空相关缓存（见 §8.3）。
- `<html lang>` 随语言更新；语言切换按钮可键盘操作，有 `aria-label`。

文案边界与回退规则详见 [10 · E-04](10-reliability-i18n.md#5-e-04-国际化)。

## 10. 配置项

| 环境变量 | 默认（开发） | 说明 |
|---|---|---|
| `SERVER_PORT` | `8080` | Java 端口 |
| `SPRING_PROFILES_ACTIVE` | `dev` | `dev` / `docker` |
| `MYSQL_HOST` | `localhost` | 数据库主机 |
| `MYSQL_PORT` | `3306` | |
| `MYSQL_DATABASE` | `framelab` | 库名 |
| `MYSQL_USER` | `framelab` | 应用账号 |
| `MYSQL_PASSWORD` | 无 | 必填；`.env` 提供 |
| `FRAMELAB_JWT_SECRET` | 无 | ≥ 32 字节；生产必填 |
| `FRAMELAB_ACCESS_TOKEN_TTL` | `15m` | 访问令牌 |
| `FRAMELAB_REFRESH_TOKEN_TTL` | `7d` | 刷新令牌家族绝对有效期 |
| `FRAMELAB_REFRESH_ROTATION_ENABLED` | `true` | 关闭后回退为不轮换，用于紧急回滚 |
| `FRAMELAB_REFRESH_ROTATION_GRACE` | `30s` | 轮换后旧令牌的并发宽限 |
| `FRAMELAB_STORAGE_ROOT` | `./storage` | 素材与结果根目录 |
| `FRAMELAB_CORS_ALLOWED_ORIGINS` | `http://localhost:3000` | 逗号分隔的精确来源 |
| `FRAMELAB_CORS_ALLOWED_ORIGIN_PATTERNS` | dev：`http://localhost:*,http://127.0.0.1:*` | 仅 dev 默认启用；docker/生产留空 |
| `FRAMELAB_COOKIE_SECURE` | `false`（本地 dev/docker 演示） | 生产 HTTPS 必须 true |
| `FRAMELAB_WORKER_THREADS` | `2` | 并发任务数 |
| `FRAMELAB_WORKER_POLL_MS` | `500` | 认领 `QUEUED` / 到期 `RETRYING` 的轮询间隔 |
| `FRAMELAB_RETRY_MAX_STEP_ATTEMPTS` | `3` | 新任务每步尝试上限（1～5），创建时快照 |
| `FRAMELAB_RETRY_BACKOFF_BASE_SECONDS` | `2` | 首次重试等待 |
| `FRAMELAB_RETRY_BACKOFF_MAX_SECONDS` | `30` | 退避封顶 |
| `NEXT_PUBLIC_BACKEND_URL` | `http://localhost:8080` | 前端拼接 API/静态资源 |

Spring 限制：

```yaml
spring:
  messages:
    basename: i18n/messages
    encoding: UTF-8
    fallback-to-system-locale: false
  servlet:
    multipart:
      max-file-size: 12MB
      max-request-size: 14MB
  datasource:
    hikari:
      maximum-pool-size: 10
      connection-timeout: 3000
```

## 11. 技术取舍

| ID | 决策 | 选择 | 理由 | 代价 / 何时重选 |
|---|---|---|---|---|
| ADR-01 | 任务推进机制 | 状态自适应轮询 | 题目不强制实时；实现简单、可测试、无需长连接基础设施 | 延迟 1.5～2.5 s；若要求秒级推送或多端协同，改 SSE |
| ADR-02 | 任务队列 | MySQL `QUEUED`/`RETRYING` + `SKIP LOCKED` | 持久、可观察、可重试、可续跑、少一个中间件 | 吞吐受数据库限制；任务量增大时引入 MQ |
| ADR-03 | 文件存储 | 本地目录 + Volume | 满足“服务端可用素材”，部署简单 | 多实例需共享卷；扩容改 S3 |
| ADR-04 | 鉴权 | JWT + 刷新 Cookie + 令牌版本 | 无状态、前后端分离、Spring Security 原生支持；退出全部/禁用可立即失效 | 每请求多一次主键查询；若压测有瓶颈再加 ≤30s 短缓存 |
| ADR-05 | Next.js 角色 | 页面 + 客户端数据编排，直连 Java | 满足 R-01 职责边界，避免双重业务实现 | 需处理 CORS、令牌刷新；生产建议同域代理 |
| ADR-06 | 结果生成 | Java2D 确定性合成 | 结果真实可预览，失败阶段有实际工作 | 不是 AI；版式固定，不评图像质量 |
| ADR-07 | ORM | MyBatis XML | SQL 可控、易解释、贴近题目要求 | 手写映射；复杂查询比 JPA 多写代码 |
| ADR-08 | 前端组件 | Tailwind + Radix 自建 | 视觉可控、可访问性有保障、无重型主题覆盖 | 需要自己维护组件规范（见 06） |
| ADR-09 | 数据库 | MySQL 8.4 | 题目要求；`SKIP LOCKED`、CHECK、JSON 能力足够 | 无 |
| ADR-10 | 重试 | 有界自动重试 + 指数退避 | 瞬时故障可自愈；失败分类与上限避免无效重试 | 需 `RETRYING` 状态、尝试表与退避配置；不重试业务性失败 |
| ADR-11 | 断点续跑 | 启动回收 `RUNNING`，从第一个未成功步骤继续 | 服务重启不丢已完成的步骤，不把中断误判为失败 | 仅步骤级；单实例假设；步骤内部从头重跑 |
| ADR-12 | 受理幂等 | `clientRequestId` + 数据库唯一键 | 双击、网络重试、并发提交只产生一个任务；跨进程也成立 | 同键不同载荷需 409；键与任务同寿命 |
| ADR-13 | 国际化 | 前端词典 + `NEXT_LOCALE`；后端 `MessageSource` + 翻译表 | P0 无需 URL 前缀即可覆盖界面、模板与 API 消息 | 无独立语言 URL；翻译需与种子同批维护 |

## 12. 失败模式与可观测性

| 故障 | 系统行为 | 用户看到 | 恢复 |
|---|---|---|---|
| MySQL 暂时不可用 | API 503/500；worker 认领失败退避 | “服务暂不可用，请稍后重试” | 数据库恢复后自动继续 |
| 磁盘写满/无权限 | 当前步骤失败 `STORAGE_UNAVAILABLE`；可重试则 `RETRYING`，否则 `FAILED` + 后续 SKIPPED | 失败原因 + 阶段；可能短暂“等待重试” | 清理磁盘/修权限后自动重试或重新创建任务 |
| worker 崩溃 | 任务保持 RUNNING | 一直显示处理中（直到重启） | 重启 → `StartupRecovery` 回收为 QUEUED，从断点续跑 |
| 重试等待中 | 任务 `RETRYING`，不占线程 | “等待重试”+尝试次数 | 到期自动认领；无需用户操作 |
| 重试耗尽 | 任务 FAILED | 失败原因 + 尝试历史 | 用户重新创建任务 |
| 任务大量排队 | 全部 `QUEUED` | “排队中” | 等待 2 个 worker 消化；或临时增线程 |
| 关闭页面 | 任务继续 | 重新打开后从服务端恢复 | 无需操作 |
| 刷新令牌过期 | 401 → 匿名态 | 要求重新登录 | 登录 |
| 访问令牌过期 | 单飞 refresh 后重放 | 无感 | 自动 |
| 轮询接口 5xx/断网 | 保留快照 | “暂时无法更新” + 重试 | 手动/聚焦重试 |
| 响应丢失但任务已创建 | 客户端重试同 `clientRequestId` | 返回同一任务 | 自动幂等 |
| 重复 worker 执行 | 终态守卫拒绝写入 | 状态不变 | 日志告警 |
| 翻译缺失 | 回退中文基础列 / 默认语言 | 不出现空文案 | 补翻译种子 |
| 迁移失败 | 应用启动失败 | 容器不健康 | 修复迁移/恢复备份 |
| 账号切换旧缓存 | key 含 userId + locale + clear | 不串数据 | 自动 |
| 结果文件丢失 | 读取 500/503，日志定位 | 无法预览，明确提示 | 备份恢复；P1 校验 |

可观测性：

- 日志字段：`traceId`、`userId`、`taskId`、`stage`、`attemptNo`、`status`、`event`、`durationMs`、`failureCode`、`retryCount`、`resumeCount`、`locale`。
- 事件示例：`task.submitted`、`task.idempotent_replay`、`task.claimed`、`step.started`、`step.succeeded`、`step.failed`、`task.retrying`、`task.recovered`、`task.succeeded`、`task.failed`、`asset.uploaded`、`auth.refresh_reuse_detected`。
- 不记录密码、令牌、Cookie、请求体原图、完整邮箱。
- `task_step` 与 `task_step_attempt` 的时间戳、状态与错误码是审计轨迹；前端详情可展开尝试历史。
- Actuator 只开放 `health`、`info`；不公开 env、beans、heapdump。

## 13. 单实例假设与扩展路径

P0 明确按**单实例**设计：

- `StartupRecovery` 假定所有 `RUNNING` 任务都属于上一个已退出的进程；多实例会互相回收。
- 本地存储假定只有当前进程写入。
- 登录限流在单进程内有效；刷新轮换与令牌版本校验依赖共享 MySQL，本身不阻碍多实例。
- 重试调度依赖数据库时间与单写者，不做分布式锁。

若要扩展为多实例：

1. 任务加 `lease_owner` / `heartbeat_at`，只清理心跳超时的任务；恢复改为租约接管。
2. 存储迁移到共享卷或 S3；结果读取走对象存储签名 URL 或继续代理。
3. 登录限流迁移到共享 Redis；刷新令牌已在 MySQL，可按需迁移；JWT 密钥共享。
4. 队列可继续用 MySQL（`SKIP LOCKED` 已支持多消费者），或迁移到 MQ。
5. 前端同域代理，Cookie 域统一。
6. 翻译表可加缓存，但必须按 locale 失效。

这些都不在 P0 验收范围，但设计上不封死。

## 14. 容量与性能

| 项 | 默认 | 说明 |
|---|---|---|
| 并发任务 | 2 | 2 个 worker 线程 |
| 单任务默认耗时 | 约 5 s | 800 + 3000 + 1200 ms |
| 理论吞吐 | 约 24 任务/分钟 | 实际受图片解码与磁盘影响 |
| 任务列表 | 20/页 | 索引 `(user_id, created_at)` |
| 素材 | ≤ 10 MB，最长边 4096 | 解码内存峰值需控制 |
| 结果 | 画布 ≤ 4096²，JPEG 0.9 | 单张通常 1～5 MB |
| 数据库连接池 | 10 | 8 个 HTTP + worker 足够 |
| 前端首屏 | 模板列表 10 条 | 无分页；覆盖图懒加载 |

性能红线：

- 不在事务中睡眠/渲染。
- 列表查询不加载 `task_step`；详情才加载三步。
- 模板与分类可缓存 60 秒；任务与结果不缓存 JSON。
- 图片按需加载，网格卡片使用 `loading="lazy"`、固定宽高比防抖动。
- 轮询有上限：仅活跃任务、页面可见、终态即停。
