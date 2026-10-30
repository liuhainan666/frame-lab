# 07 · 测试、CI、容器交付与演示

> 本文定义“怎么证明它能工作、怎么在全新环境启动、怎么演示和回滚”。
> 验收条目见 [08-acceptance.md](08-acceptance.md)；接口见 [04-api.md](04-api.md)。

## 1. 测试目标

必须证明的核心行为，而不是追求覆盖率数字：

1. 后端三步**顺序固定**、每步有独立记录，失败后**停止**；不可重试或耗尽时后续步骤 `SKIPPED`。
2. 前端能区分：上传中、提交中、排队、处理中、等待重试、正在恢复、成功、失败、查询异常；查询异常不被误判为任务失败。
3. 多任务、多用户互不串扰；切换用户后不残留旧数据。
4. 刷新页面后从服务端恢复任务并继续跟踪。
5. 成功有真实结果文件；失败有阶段、失败码、可理解原因与尝试历史。
6. 同一 `clientRequestId` 重复/并发提交只产生一个任务；服务重启后 `RUNNING` 任务从断点续跑。
7. 可重试失败按退避自动重试，达到上限后失败；中断不消耗重试预算。
8. 中文/英文界面、模板文案、API 错误与任务消息均可用；缺翻译回退不出现空白。
9. 全新环境可一条命令启动，种子账号与模板可用。

## 2. 测试分层

| 层 | 工具 | 范围 | 运行时机 |
|---|---|---|---|
| 后端单元 | JUnit 5 + AssertJ + Mockito | 状态机、校验、布局、令牌、模拟配置 | 每次 `mvn test` |
| 后端集成 | Spring Boot Test + MockMvc + Testcontainers MySQL | API、Flyway、MyBatis、事务、worker、权限 | 每次 `mvn verify` |
| 前端组件 | Vitest + React Testing Library + MSW | 页面状态、上传、提交、轮询、隔离、异常 | 每次 `pnpm test` |
| 静态检查 | ESLint、`tsc --noEmit`、Prettier | 类型与风格 | CI |
| 手工验收 | 浏览器 + 演示脚本 | 视觉、交互、刷新、断网、多账号 | 交付前 |
| E2E（P1） | Playwright | 登录 → 上传 → 成功 → 历史 | 不阻塞 P0 |

不要求端到端浏览器测试；R-12 只要求核心行为测试。

## 3. 测试矩阵

### 3.1 后端单元测试

| ID | 用例 | 断言 |
|---|---|---|
| BE-U-01 | 任务状态机合法迁移 | `QUEUED→RUNNING→RETRYING→RUNNING→SUCCEEDED/FAILED` 均允许；`RUNNING→QUEUED` 仅恢复路径允许 |
| BE-U-02 | 终态不可逆 | 对 `SUCCEEDED/FAILED` 再写状态抛异常/影响 0 行 |
| BE-U-03 | 步骤顺序 | 跳过 seq 2 直接执行 seq 3 被拒绝 |
| BE-U-04 | 提交数量校验 | 少于、多于、重复 `assetIds` 均失败 |
| BE-U-05 | 归属校验 | 他人素材返回 404 语义（不泄露） |
| BE-U-06 | 模板限制 | MIME、单文件大小、素材数按模板校验 |
| BE-U-07 | 幂等键 | 相同 `clientRequestId` 只产生一个任务；载荷不同触发冲突 |
| BE-U-08 | JWT | 正常解析；篡改签名、错误 `iss/aud`、过期均拒绝 |
| BE-U-09 | 刷新令牌 | 只存哈希；轮换写 `ROTATED`/`replaced_by_id`；撤销/过期不可用；宽限内旧令牌不撤销家族 |
| BE-U-10 | 登录限流 | 5 次失败后第 6 次 429；成功清零 |
| BE-U-11 | 模拟配置校验 | 负值、>60000、`remaining>0` 但无阶段、`remaining=0` 却 retryable 均拒绝 |
| BE-U-12 | 失败注入消费 | 命中原子递减一次；并发只有一个任务消费；`retryable` 正确传递 |
| BE-U-13 | 布局规划 | 1/2/3 张输出的槽位坐标与画布匹配，无重叠越界 |
| BE-U-14 | 结果渲染 | 输出 JPEG、尺寸等于画布、含右下角水印像素差异 |
| BE-U-15 | 原子写 | 临时文件移动后源文件不存在；目标存在且大小 >0 |
| BE-U-16 | 错误映射 | `TaskFailureException` → 对应失败码；未知异常 → INTERNAL_ERROR |
| BE-U-17 | 密码/令牌日志 | 日志与响应不出现明文密码、令牌、Cookie |
| BE-U-18 | 失败分类 | 各失败码按默认 retryable 分类；模拟覆盖只影响本次 |
| BE-U-19 | 退避计算 | 第 1 次 2s、第 2 次 4s、第 3 次 8s，封顶 30s；无抖动 |
| BE-U-20 | 重试预算 | `attempt_count` 达上限不再重试；每步预算独立 |
| BE-U-21 | 续跑计数 | `INTERRUPTED` 不增 retry_count；恢复时 attempt_count 回退 |
| BE-U-22 | 幂等载荷比较 | templateId 或 assetIds 顺序不同 → 409；顺序一致 → 重放 |
| BE-U-23 | 语言协商 | `en-US`/`en`/`zh-CN`/`zh`/q 权重/不支持语言均正确归一 |
| BE-U-24 | 消息渲染 | 失败码、步骤消息、参数插值在中英文下无缺键、无空串 |
| BE-U-25 | 翻译回退 | 缺 en-US 模板/分类翻译时回退基础中文 |

### 3.2 后端集成测试（Testcontainers MySQL）

启动一个真实 MySQL 8.4 容器，从空库执行 Flyway，再用 MockMvc 调用 API。测试 profile 把模拟耗时设为 0，避免等待。

| ID | 用例 | 断言 |
|---|---|---|
| BE-I-01 | 迁移与种子 | 表全部存在；6 分类、10 模板、3 用户、单行模拟配置；6+10 条 en-US 翻译 |
| BE-I-02 | 登录成功 | 200 + accessToken + Set-Cookie；DB 有令牌哈希 |
| BE-I-03 | 登录失败 | 401 `INVALID_CREDENTIALS`，响应不区分原因 |
| BE-I-04 | 刷新与会话 | refresh 200 并下发新 Cookie；logout 后 refresh 401；Cookie 属性正确 |
| BE-I-05 | 模板列表筛选 | 分类过滤正确；未知分类返回空；下架模板不出现 |
| BE-I-06 | 模板详情 | 200 字段完整；不存在 404 |
| BE-I-07 | 上传合法 JPEG/PNG | 201；文件落盘；尺寸/类型正确；非原文件名 |
| BE-I-08 | 上传非法内容 | 伪装图片 422；超大 413；SVG 415 |
| BE-I-09 | 素材归属 | B 读取 A 的素材 404；A 可读 |
| BE-I-10 | 删除素材 | 未绑定 204；绑定后 409 `ASSET_IN_USE` |
| BE-I-11 | 提交成功 | 202；task QUEUED、3 步 PENDING、materials 顺序正确、maxAttempts 快照正确 |
| BE-I-12 | 幂等重放 | 同 `clientRequestId` 第二次 200 + `Idempotent-Replay: true`，任务数 1；重放优先于模板/素材校验，即使模板此时下架仍返回原任务 |
| BE-I-13 | 提交校验 | 数量不符/他人素材/下架模板分别 400/404；同键不同载荷 409 `IDEMPOTENCY_KEY_REUSED` |
| BE-I-14 | 成功流水线 | 轮询至 SUCCEEDED；3 步全 SUCCEEDED；结果文件存在；GET 不改变状态 |
| BE-I-15 | 三阶段永久失败 | 对每个阶段注入 `retryable=false`：任务 FAILED；失败码/阶段匹配；后续 SKIPPED；无后续步骤开始 |
| BE-I-16 | 失败后不推进 | 失败后连续查询 3 次，状态与步骤不变 |
| BE-I-17 | 启动恢复 | 预置 RUNNING + RUNNING step/attempt：恢复后 task QUEUED、attempt INTERRUPTED、step PENDING、resume_count+1、attempt_count 回退 |
| BE-I-18 | 多任务隔离 | 2 个 worker 执行 4 个任务；每个任务状态独立、结果独立、无交叉写 |
| BE-I-19 | 用户隔离 | B 列表不含 A 任务；B 详情/结果 404 |
| BE-I-20 | 结果读取 | 未成功 409；成功后 200 image/jpeg、ETag、304 命中 |
| BE-I-21 | 模拟管理权限 | USER 403；ADMIN 200；非法配置 400 |
| BE-I-22 | 错误格式 | 4xx 为 `application/problem+json`，含 code/traceId；无堆栈 |
| BE-I-23 | 查询无副作用 | 反复 GET 不触发步骤、不更新 updatedAt |
| BE-I-24 | 并发幂等 | 同一 `(user, clientRequestId)` 并发 10 次，只建一个任务 |
| BE-I-25 | 可重试失败 | `remaining=1,retryable=true` + 默认 3 次：任务经历 RETRYING → SUCCEEDED，retryCount=1 |
| BE-I-26 | 重试耗尽 | `remaining>=3,retryable=true`：第 3 次失败后 FAILED，后续 SKIPPED，attempt 数=3 |
| BE-I-27 | 退避与到期 | RETRYING 时 `next_retry_at` 约为 now+2s；未到期不被认领；注入 Clock 后到期被认领 |
| BE-I-28 | 成功步骤不重跑 | 仅失败步骤新增 attempt；前序步骤时间戳与 message_args 不变 |
| BE-I-29 | 续跑完整流程 | 重启恢复后 worker 从第一个非成功步骤继续，最终 SUCCEEDED；resumeCount=1、retryCount 不因中断增加 |
| BE-I-30 | 多次重启 | 两次恢复后仍能成功，resumeCount=2，无重复结果文件 |
| BE-I-31 | RETRYING 重启 | 恢复不处理 RETRYING；退避时间保持，到期后正常认领 |
| BE-I-32 | 语言响应 | `Accept-Language: en-US` 返回英文模板/错误/任务消息，`Content-Language: en-US`、`Vary: Accept-Language` |
| BE-I-33 | 翻译回退 | 删除某模板 en-US 行后，英文请求返回中文基础列/任务快照，不返回空串 |
| BE-I-34 | CORS 语言头 | 预检允许 `Accept-Language`，暴露 `Content-Language` / `Idempotent-Replay` |
| BE-I-35 | 刷新轮换 | 旧令牌标记 `ROTATED`、同 family、`replaced_by_id` 指向新令牌；新 Cookie 不同 |
| BE-I-36 | 宽限与重放 | 30s 内旧令牌可再次刷新且不撤销家族；超宽限重用撤销家族，活跃令牌全部 401 |
| BE-I-37 | 即时失效 | 禁用用户旧 accessToken 下一次请求 401；logout-all 撤销全部家族并递增 `token_version` |
| BE-I-38 | 关闭轮换回滚 | `refresh-rotation-enabled=false` 时 refresh 200 且不重设 Cookie；旧令牌可重复使用 |

集成测试注意：

- 使用 `@ServiceConnection` 或 `@DynamicPropertySource` 注入容器 JDBC URL；每个测试类独立 schema 或清理数据。
- worker 在测试中可手动触发 `TaskPipeline.run(taskId)`，避免依赖线程时序；另有一组测试启动真实 worker + Awaitility 等待终态。
- 重试测试可注入 `Clock` 或直接改 `next_retry_at` 为过去时间，避免真实等待 2s/4s；生产逻辑仍使用数据库时间。
- 续跑测试预置 `RUNNING` 任务 + `RUNNING` step/attempt，再调用 `StartupRecovery`；不得依赖真实进程重启。
- 上传夹具使用 1×1、100×100 真实 JPEG/PNG 字节，不用伪造扩展名。
- 失败注入测试直接写 `simulation_setting`，不通过 HTTP，减少耦合；管理端 API 另有测试。
- 时间断言允许 10 ms 误差；步骤顺序用 `started_at` 与 seq 共同断言。
- 语言测试对同一资源分别请求 `zh-CN` 与 `en-US`，断言 `code` 不变、`detail/message` 变化、`Content-Language` 正确。
- 结果图断言尺寸与文件头，不做像素级黄金图对比（避免环境字体/渲染差异）。

### 3.3 前端组件测试

| ID | 用例 | 断言 |
|---|---|---|
| FE-U-01 | 模板列表 | MSW 返回 2 模板；分类/卡片/素材数渲染；筛选触发正确 query |
| FE-U-02 | 加载/空/错误 | 骨架、空态、错误态与重试 |
| FE-U-03 | 上传数量 | 超过模板数量不可添加；提示正确 |
| FE-U-04 | 上传类型/大小 | 前端即时拒绝；错误文案含限制值 |
| FE-U-05 | 上传进度 | 进度更新；成功后 assetId 绑定槽位；失败可重试 |
| FE-U-06 | 替换/移除 | 替换上传新素材；移除清空槽位；已上传的触发 DELETE |
| FE-U-07 | 提交禁用 | 素材未齐、上传中、提交中均禁用；提示原因 |
| FE-U-08 | 防重复提交 | 双击只发一次 POST；`clientRequestId` 相同；受理后生成新值 |
| FE-U-09 | 受理后状态 | 按钮恢复；toast；右栏出现 QUEUED 卡；不显示“提交中” |
| FE-U-10 | 多任务独立 | 3 张卡不同状态；更新一张不影响其他 |
| FE-U-11 | 轮询启停 | 有 QUEUED/RUNNING/RETRYING 才轮询；全部终态后停止（fake timers） |
| FE-U-12 | 查询异常 | MSW 500/断网：卡片保留原状态；出现提示条；不显示失败 |
| FE-U-13 | 查询恢复 | 重试成功后提示消失，状态更新 |
| FE-U-14 | 失败原因 | 失败卡显示阶段 + 原因 + 尝试次数；不显示“任务失败”占位 |
| FE-U-15 | 刷新恢复 | 启动 refresh 成功；历史加载；活跃任务继续轮询 |
| FE-U-16 | 登录过期 | 401 TOKEN_EXPIRED → 单飞 refresh → 重放一次 |
| FE-U-17 | 切换用户 | 清缓存/取消请求；B 看不到 A 任务 |
| FE-U-18 | 结果预览 | 鉴权取图 → blob URL；关闭释放；加载失败可重试 |
| FE-U-19 | 无权限 | 403 toast；不崩页 |
| FE-U-20 | 无障碍基础 | 上传/图标按钮有标签；Tab 可操作；弹层焦点陷阱 |
| FE-U-21 | RETRYING 卡片 | 显示“等待重试”、尝试次数、nextRetryAt、最近原因；样式不是失败终态 |
| FE-U-22 | 恢复卡片 | `resumeCount>0` 的 QUEUED 显示“正在恢复”；不显示“失败” |
| FE-U-23 | 尝试历史 | TaskDetail 展开 AttemptList；FAILED/INTERRUPTED/SUCCEEDED 区分；空列表不报错 |
| FE-U-24 | 状态不可推导 | nextRetryAt 到期但响应仍 RETRYING 时，不自行改 RUNNING |
| FE-U-25 | 语言切换 | 点 English 写 Cookie、`<html lang>` 变 en-US、文案切换、不跳路由 |
| FE-U-26 | 查询键语言 | 切换语言后模板/任务 key 变化并重取；旧语言请求被取消 |
| FE-U-27 | 语言持久化 | 重新挂载读取 NEXT_LOCALE；无 Cookie 默认 zh-CN |
| FE-U-28 | 英文布局 | 360px 下英文顶栏/卡片/按钮不溢出；长标题两行 |
| FE-U-29 | 本地化 API 消息 | 任务失败原因与错误提示使用 API message，不硬编码中文 |
| FE-U-30 | 缺键回退 | en-US 词典缺键时回退 zh-CN，不渲染 key 或空白 |

前端测试约定：

- MSW 的响应夹具与 [04](04-api.md) 字段一致；类型从 `features/*/types.ts` 引用，不用 `any`。
- 轮询相关用 `vi.useFakeTimers()` + `advanceTimersByTime`，并确保清理 timers；TanStack Query 使用独立 `QueryClient`，测试间重置。
- 每个测试显式设置 `userId` 与 `locale`，验证 query key 隔离；语言夹具覆盖 `zh-CN` 与 `en-US`。
- 语言切换测试清理 Cookie；Provider 必须可注入初始 locale，避免依赖真实浏览器 Cookie。
- 不测样式实现细节（类名、精确 px）；视觉靠 06 的检查清单与截图。

### 3.4 手工验收

见 [08 · 演示脚本](08-acceptance.md#10-演示脚本)，至少覆盖：

- 成功路径、三个阶段的永久失败注入、延迟调整、多任务排队。
- 可重试失败的等待、退避、成功与耗尽；模拟 `retryable`。
- 服务重启后的断点续跑与 `resumeCount`；中断尝试历史。
- 刷新恢复、断网查询异常、账号隔离。
- 中英文切换、模板翻译、失败原因与错误消息语言、刷新后语言保持。
- 360px 小屏可操作、键盘焦点、结果预览，中英文各一遍。

## 4. 测试数据与夹具

| 数据 | 位置 | 说明 |
|---|---|---|
| 演示账号 | Flyway 种子 | demo / demo2 / admin |
| 模板与封面 | Flyway + static | 10 个；封面本地 |
| 上传夹具 | `backend/src/test/resources/fixtures/` | 100×100 JPEG/PNG；非法文本、超大头像、伪装扩展名 |
| 前端 MSW 夹具 | `frontend/src/test/fixtures/` | 模板、任务各状态（含 RETRYING/恢复）、尝试历史、中英文 ProblemDetail |
| 结果断言 | 临时目录 | 每测试独立 `@TempDir`；结束后清理 |

不在仓库提交真实用户照片、个人信息或大文件。

## 5. CI（GitHub Actions）

`.github/workflows/ci.yml`：

```yaml
name: ci
on:
  push:
  pull_request:

permissions:
  contents: read

concurrency:
  group: ci-${{ github.ref }}
  cancel-in-progress: true

jobs:
  backend:
    runs-on: ubuntu-latest
    defaults:
      run:
        working-directory: backend
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
          cache: maven
      - name: Make wrapper executable
        run: chmod +x mvnw
      - name: Test and package
        run: ./mvnw -B verify

  frontend:
    runs-on: ubuntu-latest
    defaults:
      run:
        working-directory: frontend
    steps:
      - uses: actions/checkout@v4
      - uses: pnpm/action-setup@v4
        with:
          version: 10
      - uses: actions/setup-node@v4
        with:
          node-version: '22'
          cache: pnpm
          cache-dependency-path: frontend/pnpm-lock.yaml
      - name: Install
        run: pnpm install --frozen-lockfile
      - name: Lint
        run: pnpm lint
      - name: Typecheck
        run: pnpm typecheck
      - name: Test
        run: pnpm test
      - name: Build
        env:
          NEXT_PUBLIC_BACKEND_URL: http://localhost:8080
        run: pnpm build
```

说明：

- Testcontainers 在 `ubuntu-latest` 的 Docker 中运行；不需要额外 MySQL service。
- 后端 `verify` 同时执行测试与打包，满足 R-12 的“Java 测试和构建”。
- 前端 `lint`、`typecheck`、`test`、`build` 全部为 CI 硬门禁。
- 可选 P1：增加 `docker compose config -q` 与镜像构建作业；不作为 P0 阻塞。
- CI 不注入真实密钥；测试使用临时 JWT 密钥与 Testcontainers 随机密码。

本地等价命令：

```bash
# 后端
cd backend && ./mvnw -B verify

# 前端
cd frontend && pnpm install --frozen-lockfile && pnpm lint && pnpm typecheck && pnpm test && pnpm build
```

## 6. 容器交付

### 6.1 目标

全新机器（安装 Docker Desktop / Engine + Compose v2）执行：

```bash
docker compose up --build
```

即可获得：

- `http://localhost:3000` 前端
- `http://localhost:8080` Java API
- MySQL 8.4（仅本机绑定 3306）
- 自动迁移与种子数据
- 持久化卷：`mysql-data`、`storage`

### 6.2 服务拓扑

```
frontend :3000 ──HTTP──▶ backend :8080 ──JDBC──▶ mysql :3306
                              │
                              └── volume: storage（materials/results）
```

前端浏览器直连 `http://localhost:8080`（由构建参数注入）；服务端不代理前端请求。生产建议同域反向代理。

### 6.3 docker-compose.yml

```yaml
name: framelab

services:
  mysql:
    image: mysql:8.4
    environment:
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD:-framelab_root}
      MYSQL_DATABASE: ${MYSQL_DATABASE:-framelab}
      MYSQL_USER: ${MYSQL_USER:-framelab}
      MYSQL_PASSWORD: ${MYSQL_PASSWORD:-framelab_pass}
      TZ: UTC
    command:
      - --character-set-server=utf8mb4
      - --collation-server=utf8mb4_0900_ai_ci
    ports:
      - "127.0.0.1:${MYSQL_PORT:-3306}:3306"
    volumes:
      - mysql-data:/var/lib/mysql
    healthcheck:
      test: ["CMD-SHELL", "mysqladmin ping -h 127.0.0.1 -u root -p$$MYSQL_ROOT_PASSWORD --silent"]
      interval: 5s
      timeout: 5s
      retries: 20
      start_period: 20s

  backend:
    build:
      context: ./backend
    environment:
      SPRING_PROFILES_ACTIVE: docker
      MYSQL_HOST: mysql
      MYSQL_PORT: 3306
      MYSQL_DATABASE: ${MYSQL_DATABASE:-framelab}
      MYSQL_USER: ${MYSQL_USER:-framelab}
      MYSQL_PASSWORD: ${MYSQL_PASSWORD:-framelab_pass}
      FRAMELAB_JWT_SECRET: ${FRAMELAB_JWT_SECRET:-framelab-demo-secret-change-me-32bytes-minimum}
      FRAMELAB_STORAGE_ROOT: /app/storage
      FRAMELAB_CORS_ALLOWED_ORIGINS: ${FRAMELAB_CORS_ALLOWED_ORIGINS:-http://localhost:3000}
      FRAMELAB_COOKIE_SECURE: ${FRAMELAB_COOKIE_SECURE:-false}
      FRAMELAB_WORKER_THREADS: ${FRAMELAB_WORKER_THREADS:-2}
      FRAMELAB_WORKER_POLL_MS: ${FRAMELAB_WORKER_POLL_MS:-500}
      FRAMELAB_RETRY_MAX_STEP_ATTEMPTS: ${FRAMELAB_RETRY_MAX_STEP_ATTEMPTS:-3}
      FRAMELAB_RETRY_BACKOFF_BASE_SECONDS: ${FRAMELAB_RETRY_BACKOFF_BASE_SECONDS:-2}
      FRAMELAB_RETRY_BACKOFF_MAX_SECONDS: ${FRAMELAB_RETRY_BACKOFF_MAX_SECONDS:-30}
    ports:
      - "8080:8080"
    volumes:
      - storage:/app/storage
    depends_on:
      mysql:
        condition: service_healthy
    healthcheck:
      test: ["CMD-SHELL", "curl -fsS http://127.0.0.1:8080/actuator/health | grep -q UP"]
      interval: 10s
      timeout: 5s
      retries: 12
      start_period: 40s

  frontend:
    build:
      context: ./frontend
      args:
        NEXT_PUBLIC_BACKEND_URL: ${NEXT_PUBLIC_BACKEND_URL:-http://localhost:8080}
    environment:
      NODE_ENV: production
      PORT: 3000
      HOSTNAME: 0.0.0.0
    ports:
      - "${FRONTEND_PORT:-3000}:3000"
    depends_on:
      backend:
        condition: service_healthy
    healthcheck:
      test: ["CMD-SHELL", "wget -qO- http://127.0.0.1:3000/ >/dev/null || exit 1"]
      interval: 10s
      timeout: 5s
      retries: 12
      start_period: 20s

volumes:
  mysql-data:
  storage:
```

> 8080、3000 绑定所有网卡以便浏览器访问；MySQL 只绑定 `127.0.0.1`。云主机部署时用安全组/防火墙限制 3000/8080，或在前方加反向代理与 TLS。

### 6.4 backend/Dockerfile

```dockerfile
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -DskipTests package

FROM eclipse-temurin:21-jre-jammy
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl fontconfig \
 && rm -rf /var/lib/apt/lists/* \
 && useradd --system --uid 10001 --create-home framelab
WORKDIR /app
COPY --from=build /src/target/framelab-api.jar /app/app.jar
RUN mkdir -p /app/storage && chown -R framelab:framelab /app
USER framelab
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
```

- 构建阶段跳过测试（CI 已跑）；镜像内不包含源码与 Maven 缓存；`~/.m2` 使用 BuildKit 缓存挂载加速重复构建。
- `pom.xml` 设置 `<finalName>framelab-api</finalName>`，保证 Dockerfile 复制的 jar 名固定。
- 非 root 运行；`storage` 卷由启动时创建并授权（如挂载卷权限不足，见 §9 故障排查）。
- 字体 `Inter-SemiBold.ttf` 与模板封面在 `src/main/resources` 中，随 jar 打包。
- `backend/.dockerignore` 排除 `target/`、`.mvn/` 缓存与本地文件。

### 6.5 frontend/Dockerfile

```dockerfile
FROM node:22-alpine AS deps
WORKDIR /app
RUN corepack enable
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
RUN pnpm install --frozen-lockfile

FROM node:22-alpine AS build
WORKDIR /app
RUN corepack enable
COPY --from=deps /app/node_modules ./node_modules
COPY . .
ARG NEXT_PUBLIC_BACKEND_URL=http://localhost:8080
ENV NEXT_PUBLIC_BACKEND_URL=$NEXT_PUBLIC_BACKEND_URL
RUN pnpm build

FROM node:22-alpine AS run
WORKDIR /app
ENV NODE_ENV=production PORT=3000 HOSTNAME=0.0.0.0
RUN addgroup -S next && adduser -S next -G next
COPY --from=build --chown=next:next /app/.next/standalone ./
COPY --from=build --chown=next:next /app/.next/static ./.next/static
COPY --from=build --chown=next:next /app/public ./public
USER next
EXPOSE 3000
CMD ["node", "server.js"]
```

- `next.config.ts` 必须设置 `output: 'standalone'`。
- `pnpm-workspace.yaml` 的 `allowBuilds` 允许 `unrs-resolver` 执行 postinstall；缺失会导致 `ERR_PNPM_IGNORED_BUILDS`。
- `NEXT_PUBLIC_BACKEND_URL` 在构建时内联；改地址需重新 `--build`。README 说明。
- 前端不包含数据库凭据。
- `frontend/.dockerignore` 排除 `node_modules/`、`.next/`、`.env*`、测试产物。

### 6.6 环境变量

根目录 `.env.example`：

```dotenv
# 演示默认值：仅本地/评审使用，禁止用于生产
MYSQL_ROOT_PASSWORD=framelab_root
MYSQL_DATABASE=framelab
MYSQL_USER=framelab
MYSQL_PASSWORD=framelab_pass
MYSQL_PORT=3306

# 至少 32 字节；生产必须替换
FRAMELAB_JWT_SECRET=framelab-demo-secret-change-me-32bytes-minimum
FRAMELAB_CORS_ALLOWED_ORIGINS=http://localhost:3000
FRAMELAB_COOKIE_SECURE=false
FRAMELAB_REFRESH_ROTATION_ENABLED=true
FRAMELAB_REFRESH_ROTATION_GRACE=30s

# 重试与 worker（默认值可直接省略）
FRAMELAB_WORKER_THREADS=2
FRAMELAB_WORKER_POLL_MS=500
FRAMELAB_RETRY_MAX_STEP_ATTEMPTS=3
FRAMELAB_RETRY_BACKOFF_BASE_SECONDS=2
FRAMELAB_RETRY_BACKOFF_MAX_SECONDS=30

# 浏览器访问 Java 的地址；改后需重建前端镜像
NEXT_PUBLIC_BACKEND_URL=http://localhost:8080

# Compose 前端宿主端口；默认 3000，端口占用或并行验收时可改
FRONTEND_PORT=3000
```

- `.env` 不入库；`.env.example` 只含演示值。
- 生产：强随机 `FRAMELAB_JWT_SECRET`、`FRAMELAB_COOKIE_SECURE=true`、HTTPS、CORS 白名单、MySQL 强口令、限制端口。
- 不把 `NEXT_PUBLIC_BACKEND_URL` 指向内网管理地址。

### 6.7 初始化顺序

1. MySQL 启动并通过 healthcheck。
2. backend 启动，Flyway 执行 `V1__schema.sql`、`V2__seed.sql`、`V3__session_security.sql`。
3. backend healthcheck 通过。
4. frontend 启动，页面可访问。
5. 浏览器访问 3000，登录种子账号。

没有额外的 SQL 手工导入步骤；数据库结构和种子全部在迁移中。重置演示数据：

```bash
# 会删除所有任务、素材与结果，仅用于本地演示
docker compose down -v
docker compose up --build
```

> `down -v` 是破坏性操作，执行前确认卷中没有需要保留的数据。

## 7. 本地开发

### 7.1 只启动 MySQL

```bash
docker compose up -d mysql
```

### 7.2 后端

```bash
cd backend
SPRING_PROFILES_ACTIVE=dev \
MYSQL_HOST=localhost MYSQL_PORT=3306 \
MYSQL_DATABASE=framelab MYSQL_USER=framelab MYSQL_PASSWORD=framelab_pass \
FRAMELAB_JWT_SECRET=framelab-dev-secret-change-me-32bytes \
FRAMELAB_COOKIE_SECURE=false \
./mvnw spring-boot:run
```

Windows PowerShell 使用 `$env:NAME="value"` 逐行设置；Git Bash 使用上述形式。默认端口 8080。

### 7.3 前端

```bash
cd frontend
pnpm install
# .env.local
# NEXT_PUBLIC_BACKEND_URL=http://localhost:8080
pnpm dev
```

访问 `http://localhost:3000`。开发时不要混用 `localhost` 与 `127.0.0.1`，否则刷新 Cookie 不生效。

### 7.4 前端脚本

`frontend/package.json` 至少提供：

| script | 命令 | 用途 |
|---|---|---|
| `dev` | `next dev` | 本地开发 |
| `build` | `next build` | 生产构建 |
| `start` | `next start` | 本地生产预览 |
| `lint` | `eslint .` | 静态检查 |
| `typecheck` | `tsc --noEmit` | 类型检查 |
| `test` | `vitest run` | 组件测试（CI） |
| `test:watch` | `vitest` | 监听模式 |

### 7.5 常用命令

| 命令 | 说明 |
|---|---|
| `cd backend && ./mvnw test` | 单元测试 |
| `cd backend && ./mvnw verify` | 集成测试 + 打包 |
| `cd frontend && pnpm lint` | ESLint |
| `cd frontend && pnpm typecheck` | `tsc --noEmit` |
| `cd frontend && pnpm test` | Vitest |
| `cd frontend && pnpm build` | 生产构建 |
| `docker compose up --build` | 全套容器 |
| `docker compose logs -f backend` | 查看后端日志 |
| `docker compose ps` | 健康状态 |

## 8. 演示脚本

前置：全新 `docker compose up --build`，浏览器打开 `http://localhost:3000`。建议一个普通窗口 + 一个无痕窗口。

### 8.1 浏览与登录

1. 未登录浏览模板；切换“旅行风景 / 节日营销 / 电商海报”，确认卡片变化。
2. 打开“旅行明信片”详情，看到 2 张素材要求、JPG/PNG、5 MB。
3. 点击“登录后开始制作”，使用 `demo@framelab.local / Demo1234!` 登录，回到原模板。
4. 预期：顶栏显示“林小满”，右栏为空态。

### 8.2 上传与校验

5. 选择一张 JPG 和一张 PNG，看到预览与上传进度；全部完成后显示 `2 / 2`，按钮可用。
6. 尝试第 3 张（或超过 5 MB 的文件），前端立即拒绝并说明原因。
7. 替换第 1 张、移除第 2 张，确认槽位与按钮状态正确；重新补齐。

### 8.3 提交与处理

8. 点击“开始制作”，按钮显示“正在提交…”，随后恢复可用并出现 toast 与“排队中”卡片。
9. 观察卡片从排队 → 素材检查 → 模拟生成 → 结果整理 → 已完成；阶段与耗时可见。
10. 连续创建 3 个任务：确认最多 2 个并行、其余排队，卡片互不串扰。
11. 在处理中刷新页面：重新登录态恢复，历史仍在，未完成任务继续更新。
12. 成功后点击卡片打开结果图，确认是服务端合成的 JPEG（尺寸与任务画布一致）。

### 8.4 账号隔离

13. 退出登录，使用 `demo2@framelab.local / Demo1234!` 登录；确认看不到 demo 的任务与结果。
14. 切回 demo，确认记录仍在。

### 8.5 指定阶段永久失败

15. 用管理员账号登录（可开无痕窗口），获取 token：
    ```bash
    curl -s http://localhost:8080/api/auth/login \
      -H 'Content-Type: application/json' \
      -d '{"email":"admin@framelab.local","password":"Admin1234!"}'
    ```
16. 注入“生成阶段失败一次，不可重试”：
    ```bash
    curl -s -X PUT http://localhost:8080/api/admin/simulation \
      -H "Authorization: Bearer $ADMIN_TOKEN" \
      -H 'Content-Type: application/json' \
      -d '{"delays":{"materialCheckMs":800,"generateMs":3000,"finalizeMs":1200},"failure":{"stage":"GENERATE","remaining":1,"retryable":false}}'
    ```
17. 用 demo 创建任务。预期：素材检查成功 → 生成失败 → 结果整理 `SKIPPED`；任务卡显示“生成失败”与原因（含“模拟失败”），不进入 `RETRYING`。
18. 打开任务详情，确认三步状态、时间线与 1 次失败尝试；再次创建任务，确认正常成功（注入已消费）。
19. 重复步骤 16 可再次触发，证明“可重复”。

### 8.6 可重试失败与尝试历史

20. 设置生成阶段连续失败 2 次且可重试：
    ```bash
    curl -s -X PUT http://localhost:8080/api/admin/simulation \
      -H "Authorization: Bearer $ADMIN_TOKEN" \
      -H 'Content-Type: application/json' \
      -d '{"delays":{"materialCheckMs":800,"generateMs":3000,"finalizeMs":1200},"failure":{"stage":"GENERATE","remaining":2,"retryable":true}}'
    ```
21. 创建任务，观察卡片：处理中 → 等待重试（约 2 秒）→ 处理中 → 等待重试（约 4 秒）→ 处理中 → 已完成。
22. 打开详情，确认生成步骤有 3 条尝试（FAILED、FAILED、SUCCEEDED），任务 `retryCount=2`、最终 `SUCCEEDED`。
23. 把 `remaining` 设为 3 或以上，重复创建：第 3 次失败后任务 `FAILED`，后续 `SKIPPED`，尝试次数为 3。

### 8.7 调整耗时与多任务观察

24. 用完整 PUT 把生成耗时改为 8000 ms（`failure` 设为 `stage:null, remaining:0, retryable:false`），创建 2 个任务；观察一个 RUNNING、一个 QUEUED，进度不会跳变或倒退。
25. 恢复默认耗时与失败注入（完整 PUT：`failure.stage=null, remaining=0, retryable=false`）。

### 8.8 查询异常与恢复

26. 在浏览器 DevTools → Network 设为 Offline，等待一个轮询周期。
27. 预期：顶部出现“任务状态暂时无法更新”，卡片保留最后一次状态，**没有**变成失败，也没有变成“重试耗尽”。
28. 恢复 Online，点击“重试”（或等待聚焦刷新）；提示消失，任务继续更新。

### 8.9 刷新恢复与断点续跑

29. 任务处理中按 F5：确认登录态、历史与进行中状态恢复。
30. 用完整 PUT 把生成耗时改为 15000 ms（失败注入保持关闭），创建任务，等待进入生成阶段。
31. 执行 `docker compose restart backend`；等待健康检查通过。
32. 刷新页面：任务从 `QUEUED`（“正在恢复”）回到 `RUNNING`；`resumeCount=1`；素材检查步骤不重跑；生成步骤的尝试历史出现 `INTERRUPTED`，随后成功。
33. 预期：任务最终 `SUCCEEDED`，`retryCount` 不因重启增加；历史记录不产生新任务。

### 8.10 幂等

34. 在 DevTools 中复制提交请求为 cURL，连续执行两次相同 `clientRequestId`：
    - 第一次 202；
    - 第二次 200 + `Idempotent-Replay: true`；
    - 历史中只有一个任务。
35. 修改素材顺序但复用同一 `clientRequestId`，预期 `409 IDEMPOTENCY_KEY_REUSED`，不创建新任务。

### 8.11 国际化

36. 顶栏切换 `English`：页面不刷新、不跳路由；导航、按钮、状态标签、模板标题与描述变为英文。
37. 创建一个可重试失败任务，确认“Retrying”“Attempt 2 of 3”与失败原因为英文。
38. 刷新页面，确认仍为英文；再切换回“中文”，确认历史任务消息与模板文案同步切换。
39. 用 API 验证：
    ```bash
    curl -s http://localhost:8080/api/templates \
      -H 'Accept-Language: en-US' | jq '.items[0] | {title, description}'
    ```

## 9. 回滚与运维

### 9.1 备份

```bash
# 数据库（在项目根目录，使用 compose 服务内环境变量）
docker compose exec -T mysql sh -c 'mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction --routines --triggers framelab' > backup-$(date +%F-%H%M).sql

# 素材与结果
docker compose cp backend:/app/storage ./storage-backup
```

### 9.2 回滚步骤

1. 记录当前版本与数据库备份。
2. `docker compose down`（保留卷）。
3. 切回上一个通过验收的 commit/tag，重新 `docker compose up -d --build`。
4. **迁移只前进**：若新版本包含删列/改类型等不可逆迁移，必须用备份恢复数据库；不要手写逆向 SQL。
5. 若存储结构变化，恢复 `storage-backup` 到 `backend:/app/storage`。
6. 冒烟：healthcheck、登录、模板列表、创建一个任务、读取结果。
7. 在 README 记录回滚版本与时间。

### 9.3 常见故障

| 现象 | 排查 | 处理 |
|---|---|---|
| `backend` 不健康 | `docker compose logs backend`；检查 MySQL 密码、JWT 密钥长度、迁移错误 | 修正 `.env`；必要时恢复备份 |
| 登录后刷新掉线 | 是否混用 `localhost/127.0.0.1`；Cookie Secure 与 HTTP 是否冲突 | 统一域名；本地 `FRAMELAB_COOKIE_SECURE=false` |
| 上传 413 | 模板/全局大小限制、反向代理限制 | 调整模板或请求体上限 |
| 任务一直 RUNNING | backend 崩溃或 worker 卡住 | 重启；`StartupRecovery` 会回收为 QUEUED 并从断点续跑，日志定位 |
| 任务停在 RETRYING | 未到 `next_retry_at`、worker 停止、数据库时间异常 | 查看日志与 `next_retry_at`；恢复 worker；不要手工改状态 |
| 中英文切换后仍显示旧文案 | 查询缓存未失效、旧请求覆盖 | 检查 query key 是否含 locale；切换时取消在途请求并重取 |
| 结果 404/500 | storage 卷权限、文件被清理 | 检查卷挂载与权限；从备份恢复 |
| 前端请求 CORS 失败 | `FRAMELAB_CORS_ALLOWED_ORIGINS` 与访问地址不一致 | 改为实际前端 origin |
| 修改后端地址不生效 | `NEXT_PUBLIC_*` 构建期内联 | `docker compose build --no-cache frontend` |
| 中文水印变方块 | 结果图绘制了中文但容器无字体 | 按设计只用英文水印；或打包中文字体（P1） |

## 10. 安全交付检查

- [ ] `.env`、`storage/`、`target/`、`node_modules/`、`.next/` 未入库。
- [ ] 前后端 `.dockerignore` 存在，构建上下文不包含依赖、构建产物与本地密钥。
- [ ] `FRAMELAB_JWT_SECRET` 已替换为随机值（≥32 字节），未使用示例值。
- [ ] 演示密码已修改或明确标注仅供评审；生产环境删除种子账号。
- [ ] `FRAMELAB_COOKIE_SECURE=true` 且全站 HTTPS。
- [ ] CORS 仅允许实际前端域名。
- [ ] MySQL 不对公网开放；root 口令强随机。
- [ ] 素材/结果目录不在静态资源根下；仅 `/assets/templates/**` 公开。
- [ ] 日志不含密码、令牌、Cookie、完整邮箱。
- [ ] 容器以非 root 运行；镜像不含源码与构建缓存。
- [ ] README 的部署与回滚步骤可执行。

## 11. 已知限制（必须写入 README）

1. **单实例**：启动恢复假设只有一个 Java 进程；多实例会互相回收 `RUNNING` 任务，需要租约/心跳。
2. **本地存储**：不支持多节点共享；扩容需对象存储或共享卷。
3. **续跑只在步骤边界**：中断步骤整步重跑，不保留步骤内部进度；已成功步骤不重跑。
4. **重试有上限**：仅可重试失败码；默认每步 3 次尝试、2s/4s 退避、封顶 30s；不做无限重试或人工重试任务。
5. **刷新令牌为家族绝对 7 天**：轮换不延长家族寿命；7 天后必须重新登录。
6. **普通退出不即时吊销其他设备**：只撤销当前家族，其他设备访问令牌最多再存活 15 分钟；需要立即吊销全部时使用“退出所有设备”。
7. **轮询而非推送**：状态最多延迟约 1.5～2.5 秒。
8. **无自助注册**：仅种子账号。
9. **无任务删除/重做/下载**：题目不要求，界面不提供。
10. **无真实 AI**：结果由固定版式合成，不代表生成质量。
11. **模拟失败为全局单例**：并发多任务时失败落在最先进入该阶段的任务；`retryable` 只覆盖单次注入。
12. **国际化无 URL 前缀**：语言靠 `NEXT_LOCALE` Cookie，分享链接的语言取决于接收者；SEO 多语言路由列 P1。
13. **只支持两种语言**：`zh-CN` / `en-US`；模板与分类翻译需随种子维护，缺翻译回退中文。
14. **Compose 为演示部署**：不含 TLS、反向代理、资源限额、集中日志、告警。
15. **无 E2E 浏览器测试**：P1 可加 Playwright；P0 以组件/集成测试 + 手工脚本覆盖。

## 12. README 交付清单（R-13）

根 `README.md` 必须包含以下章节，缺一不可：

| 章节 | 内容 |
|---|---|
| 1. 项目简介 | 一句话定位、截图（可选）、验收 commit |
| 2. 技术版本 | 前端/后端/数据库/构建工具实际版本，与锁文件一致 |
| 3. 项目结构 | `backend/`、`frontend/`、`docs/`、CI、Compose |
| 4. 快速开始 | `docker compose up --build`；种子账号；端口 |
| 5. 本地开发 | MySQL、后端、前端命令；前后端与数据库如何连接 |
| 6. 配置项 | `.env.example` 每项含义；无真实凭据；生产注意事项 |
| 7. 数据库初始化 | Flyway 迁移、种子账号与模板、重置数据方式 |
| 8. 接口与状态 | 主要接口、字段、任务状态/阶段、后台执行方式（链接 04/05） |
| 9. 演示 | 成功、永久失败、可重试失败、调整耗时、多任务、刷新恢复、服务重启续跑、查询异常、账号隔离、中英文切换 |
| 10. 身份与个人记录 | 账号设计、JWT/Cookie、种子账号、隔离规则、演示方法 |
| 11. 测试与 CI | 命令、覆盖的核心行为（含幂等/重试/续跑/i18n）、CI 触发条件与检查项 |
| 12. 部署与回滚 | Compose、健康检查、重试参数、备份、回滚、常见故障 |
| 13. 完成度 | 已完成/未完成/已知限制/实际投入（作者如实填写，不代填） |
| 14. AI 使用说明 | 是否使用、工作流、一个具体例子、验证与修正；未使用则如实说明 |
| 15. 第三方资源 | 框架、脚手架、图标、封面素材来源与许可证 |

> 实际投入工时、AI 使用细节、验收 commit 由作者在开发完成后填写；本文档不编造这些事实。
