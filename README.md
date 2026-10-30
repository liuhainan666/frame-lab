# FrameLab · 素材制作平台

深色画廊风格的素材制作网站：浏览模板、上传照片、提交三步异步制作任务，在处理期间继续浏览，最后查看个人历史与结果。图片模板带预设调色与轻人像，视频模板把照片渲染为 15 秒 MP4。前端 Next.js，后端 Spring Boot 3 + Spring Security + MyBatis，MySQL 8.4 持久化，视频使用 ffmpeg。

- 验收 tag：`v1.0-submission`（M8 干净环境证据见 [docs/evidence/m8-acceptance.md](docs/evidence/m8-acceptance.md)；X5 模板特效/视频与个人设置已并入最终版本）
- 视觉证据：[docs/assets/visual](docs/assets/visual)
- 设计口径：[docs/README.md](docs/README.md)，开发与验收以文档为准

## 1. 技术版本

| 层 | 选型 | 实际版本 |
|---|---|---|
| 前端 | Next.js App Router / React / TypeScript | Next 15.5.28 / React 19.1.10 / TS 5.9.3 |
| 样式 | Tailwind CSS 4 + CSS Variables + Radix Primitives | Tailwind 4.3.3 / Radix Dialog 1.3.1 |
| 前端状态 | TanStack Query / Zustand / Zod | 5.105.0 / 5.0.16 / 4.6.7 |
| 前端测试 | Vitest + Testing Library + MSW | 5.0.5 / 16.3.3 / 3.0.3 |
| 后端 | Java + Spring Boot | Java 21 / Spring Boot 3.4.13 |
| 安全 | Spring Security + Nimbus JOSE JWT | Security 6.4 / Nimbus 9.37.3 |
| 持久层 | MyBatis | 3.0.5 |
| 数据库 | MySQL / Flyway | 8.4 / Boot 管理版本 |
| 图片渲染 | Java2D + `.cube` LUT | 无额外运行时 |
| 视频渲染 | ffmpeg / ffprobe | 本机 8.1.2；镜像内由 apt 安装 |
| 运行时 | Node / pnpm | 容器 Node 22，本机 Node 24 / pnpm 12.16.1 |
| 交付 | Docker Compose / GitHub Actions | Compose v2+；workflow 见 `.github/workflows/ci.yml` |

## 2. 项目结构

```text
.
├─ backend/                     Spring Boot API、worker、Java2D/ffmpeg 渲染
│  ├─ src/main/java/com/framelab/
│  ├─ src/main/resources/db/migration/   V1 表结构、V2 种子、V3 会话安全、V4 模板/任务扩展、V5/V6 模板种子
│  ├─ src/main/resources/templates/      LUT、视频音乐、渲染资源
│  ├─ src/main/resources/static/assets/  模板封面
│  ├─ src/main/resources/mapper/         MyBatis XML
│  ├─ src/test/                         单元 + Testcontainers 集成测试
│  ├─ Dockerfile / .dockerignore
│  └─ pom.xml / mvnw
├─ frontend/                    Next.js App Router
│  ├─ src/app/                  页面与路由
│  ├─ src/features/             auth / templates / studio / tasks / account
│  ├─ src/i18n/                 zh-CN / en-US 词典与 Provider
│  ├─ src/shared/               API 客户端、基础 UI、工具
│  ├─ Dockerfile / .dockerignore
│  └─ package.json / pnpm-lock.yaml
├─ docs/                        设计、接口、测试、验收与证据
├─ .github/workflows/ci.yml
├─ docker-compose.yml
├─ .env.example
└─ README.md
```

## 3. 快速开始

要求：Docker Desktop / Engine + Compose v2。视频模板需要 ffmpeg，镜像内已安装；本机开发需自行安装 `ffmpeg`/`ffprobe`（或在 `.env` 关闭 `FRAMELAB_FFMPEG_ENABLED`，此时仅视频模板不可用）。

```bash
cp .env.example .env
docker compose up --build
```

启动顺序：MySQL 健康 → 后端 Flyway 迁移/种子 → 后端健康 → 前端。浏览器访问：

- 前端：`http://localhost:3000`
- API：`http://localhost:8080`
- 健康检查：`http://localhost:8080/actuator/health`

种子账号（仅演示）：

| 账号 | 密码 | 角色 |
|---|---|---|
| `demo@framelab.local` | `Demo1234!` | USER |
| `demo2@framelab.local` | `Demo1234!` | USER |
| `admin@framelab.local` | `Admin1234!` | ADMIN |

`.env` 为用户本地文件，不入库。生产部署必须替换 JWT 密钥、数据库口令并启用 HTTPS 与 `FRAMELAB_COOKIE_SECURE=true`。

### 3.1 端口被占用时

```bash
# 前端宿主端口（默认 3000）
FRONTEND_PORT=3300

# MySQL 宿主端口（默认 3306，只绑定 127.0.0.1）
MYSQL_PORT=13306
```

同时把 `FRAMELAB_CORS_ALLOWED_ORIGINS` 改成实际前端地址（例如 `http://localhost:3300`）。`NEXT_PUBLIC_BACKEND_URL` 在构建时内联，修改后需 `docker compose build frontend`。

## 4. 本地开发

### 4.1 只启动 MySQL

```bash
docker compose up -d mysql
```

### 4.2 后端

```bash
cd backend
# 按 .env 设置 MYSQL_HOST/MYSQL_PORT/MYSQL_USER/MYSQL_PASSWORD/FRAMELAB_JWT_SECRET
./mvnw spring-boot:run
```

Windows 需设置 `JAVA_HOME` 指向 JDK 21；本仓库测试使用 `./mvnw verify`。视频模板还要求本机 `ffmpeg -version` 可用（Windows 可 `winget install Gyan.FFmpeg` 或使用镜像）。

### 4.3 前端

```bash
cd frontend
pnpm install --frozen-lockfile
NEXT_PUBLIC_BACKEND_URL=http://localhost:8080 pnpm dev
```

默认 `http://localhost:3000`。`dev` profile 已允许 `http://localhost:*` / `http://127.0.0.1:*`，Next.js 自动切换端口也能登录；`docker` 与生产 profile 仍只接受 `FRAMELAB_CORS_ALLOWED_ORIGINS` 中的精确来源。

## 5. 配置

根目录 `.env.example` 列出全部变量：

| 变量 | 默认 | 说明 |
|---|---|---|
| `MYSQL_ROOT_PASSWORD` | `framelab_root` | 仅 Compose 初始化；生产必须替换 |
| `MYSQL_DATABASE` | `framelab` | 数据库名 |
| `MYSQL_USER` / `MYSQL_PASSWORD` | `framelab` / `framelab_pass` | 应用账号；生产必须替换 |
| `MYSQL_PORT` | `3306` | MySQL 宿主端口，仅绑定 `127.0.0.1` |
| `FRAMELAB_JWT_SECRET` | 演示值 | 至少 32 字节；生产 profile 必须替换 |
| `FRAMELAB_CORS_ALLOWED_ORIGINS` | `http://localhost:3000` | 允许的前端源，逗号分隔 |
| `FRAMELAB_CORS_ALLOWED_ORIGIN_PATTERNS` | dev 默认 localhost 任意端口 | 仅 dev 生效；docker/生产留空 |
| `FRAMELAB_COOKIE_SECURE` | `false` | 生产 HTTPS 设为 `true` |
| `FRAMELAB_REFRESH_ROTATION_ENABLED` | `true` | 刷新令牌轮换；紧急回滚时可关闭 |
| `FRAMELAB_REFRESH_ROTATION_GRACE` | `30s` | 轮换后旧令牌的并发宽限 |
| `FRAMELAB_WORKER_THREADS` | `2` | 任务 worker 并发数 |
| `FRAMELAB_WORKER_POLL_MS` | `500` | 认领轮询间隔 |
| `FRAMELAB_RETRY_MAX_STEP_ATTEMPTS` | `3` | 每步尝试上限快照 |
| `FRAMELAB_RETRY_BACKOFF_BASE_SECONDS` | `2` | 退避基数，实际 `min(base×2^(n-1), max)` |
| `FRAMELAB_RETRY_BACKOFF_MAX_SECONDS` | `30` | 退避上限 |
| `FRAMELAB_FFMPEG_ENABLED` | `true` | 视频渲染开关；关闭时视频模板提交返回 503 |
| `FRAMELAB_FFMPEG_BINARY` / `FRAMELAB_FFMPEG_PROBE` | `ffmpeg` / `ffprobe` | 可执行文件路径 |
| `FRAMELAB_FFMPEG_TIMEOUT` | `180s` | 单次视频渲染超时 |
| `FRAMELAB_FFMPEG_MAX_CONCURRENT` | `1` | 本实例并发生成数 |
| `FRAMELAB_FFMPEG_MAX_OUTPUT_BYTES` | `209715200` | 输出上限（200 MB） |
| `NEXT_PUBLIC_BACKEND_URL` | `http://localhost:8080` | 浏览器访问 API 的地址，构建时内联 |
| `FRONTEND_PORT` | `3000` | Compose 前端宿主端口 |

后端还支持 `FRAMELAB_STORAGE_ROOT`（容器内 `/app/storage`）与 `SPRING_PROFILES_ACTIVE`。

## 6. 数据库

- 迁移：`V1__schema.sql`（用户、模板、素材、任务、步骤、尝试、模拟配置）、`V2__seed.sql`（演示账号、分类、模板、模拟默认值）、`V3__session_security.sql`（令牌版本、刷新家族、轮换链）、`V4__template_effects_video.sql`（模板类型/spec、任务快照、视频结果字段）、`V5__template_effect_seed.sql`（图片效果与 2 个新模板）、`V6__video_template_seed.sql`（15 秒视频模板）。
- 最终种子：6 个分类、13 个模板（12 图片 + 1 视频）、3 个演示账号、单行模拟配置。
- 启动时 Flyway 自动执行；不需要手工建表或导入 SQL。
- 重置演示数据（会删除任务、素材与结果）：

```bash
docker compose down -v
docker compose up --build
```

> `down -v` 会删除 `mysql-data` 与 `storage` 卷，执行前确认没有需要保留的数据。

## 7. 接口与状态

主要接口（完整契约见 [docs/04-api.md](docs/04-api.md)）：

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/auth/login` | 邮箱密码登录，返回 accessToken，刷新 Cookie |
| POST | `/api/auth/refresh` | 轮换刷新令牌并换取新访问令牌 |
| POST | `/api/auth/logout` | 撤销当前刷新令牌家族 |
| POST | `/api/auth/logout-all` | 撤销全部设备并立即使访问令牌失效 |
| GET | `/api/template-categories` | 分类，公开 |
| GET | `/api/templates` / `/api/templates/{id}` | 模板库与详情，公开 |
| POST | `/api/assets` | 上传 JPEG/PNG，私有 |
| GET/DELETE | `/api/assets/{id}/content` / `/api/assets/{id}` | 鉴权读取/删除素材 |
| POST | `/api/tasks` | 提交任务，`clientRequestId` 幂等 |
| GET | `/api/tasks` / `/api/tasks/{id}` | 当前用户任务列表/详情 |
| GET | `/api/tasks/{id}/result` | 鉴权读取结果：图片 JPEG 或视频 MP4（支持 Range） |
| GET | `/api/tasks/{id}/result/poster` | 鉴权读取视频封面 JPEG |
| GET/PUT | `/api/admin/simulation` | 管理员配置耗时与失败注入 |

任务状态：`QUEUED → RUNNING → RETRYING / SUCCEEDED / FAILED`。步骤固定 `MATERIAL_CHECK → GENERATE → FINALIZE`，失败后后续步骤 `SKIPPED`，终态不可逆。服务重启将 `RUNNING` 恢复为 `QUEUED`（`resumeCount+1`），已完成步骤不重跑，中断尝试标记 `INTERRUPTED`。

## 8. 演示

1. 未登录浏览模板与分类；打开模板详情点击制作，跳登录并回原页。
2. `demo` 登录，上传 2 张 JPG/PNG，观察逐文件进度；尝试第 3 张或超 5 MB 文件应被拒绝。
3. 提交后观察三步、阶段与耗时；连续创建 3 个任务，确认 2 个并行、其余排队。
4. 处理中刷新页面，登录态、历史与任务继续更新。
5. 管理员注入 `GENERATE` 永久失败，任务失败且结果整理为 `SKIPPED`；再次提交恢复正常。
6. 注入 `GENERATE` 可重试失败 2 次，观察两次等待重试后成功，详情有 3 次尝试。
7. DevTools 离线，出现“任务状态暂时无法更新”且卡片不失败；恢复后提示消失。
8. 处理中重启后端，任务从断点续跑，`resumeCount=1`，素材检查不重跑。
9. 相同 `clientRequestId` 提交两次只建一个任务；改素材顺序返回 `409`。
10. `demo2` 登录看不到 `demo` 的任务与结果；切回 `demo` 记录仍在。
11. 顶栏切换 English，不刷新页面；模板、状态与失败原因变英文，刷新后保持。
12. 打开“黄金时刻”上传 2 张照片，结果明显暖调/琥珀；换“夏日海岸”可见冷调差异。
13. 打开“城市漫游”上传 5 张照片，点击“生成视频”；完成后结果弹层可播放、可拖动进度，卡片显示 `15 秒` 与封面。

管理员注入示例：

```bash
ADMIN_TOKEN=$(curl -s http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@framelab.local","password":"Admin1234!"}' | sed -E 's/.*"accessToken":"([^"]+)".*/\1/')

curl -s -X PUT http://localhost:8080/api/admin/simulation \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"delays":{"materialCheckMs":800,"generateMs":3000,"finalizeMs":1200},"failure":{"stage":"GENERATE","remaining":1,"retryable":false}}'
```

## 9. 身份与安全

- 访问令牌只在浏览器内存，以 `Authorization: Bearer` 发送；刷新令牌为 HttpOnly Cookie（`Path=/api/auth; SameSite=Lax`），每次刷新轮换，家族绝对 7 天。
- 30 秒宽限兜住多标签并发刷新；超出宽限的旧令牌重用会撤销整个家族。账号弹层提供“退出所有设备”，递增令牌版本并立即吊销全部访问令牌。
- 任务、素材、结果均按 `user_id` 查询；他人资源统一返回 `404`，不泄露存在性。
- 登录失败 5 次/15 分钟触发 `429`；口令 BCrypt cost 12；错误文案不区分账号是否存在。
- CORS 白名单、CSP、`X-Content-Type-Options`、`X-Frame-Options`、`Referrer-Policy`、`Permissions-Policy` 已配置。
- 素材与结果保存在私有卷，静态目录只公开模板封面；结果不可公开猜测。
- 详细安全设计见 [docs/05-architecture.md](docs/05-architecture.md)。

## 10. 测试与 CI

```bash
# 后端：单元 + Testcontainers MySQL 集成测试（125 个用例；视频用例无 ffmpeg 时跳过）
cd backend && ./mvnw verify

# 前端：类型、Lint、组件测试、生产构建（54 个用例）
cd frontend && pnpm typecheck && pnpm lint && pnpm test && pnpm build
```

覆盖登录/刷新/隔离、素材校验、三步流水线、受理幂等（含并发）、自动重试与退避、耗尽、终态失败、启动恢复与续跑、错误/i18n、LUT/调色/轻人像、视频渲染/Range/poster、前端多任务/查询异常/上传/筛选/详情/视频播放。GitHub Actions 在 push 与 PR 触发前后端两个 job；容器构建不跑测试（CI 已跑）。

## 11. 部署、备份与回滚

- 一键启动：`docker compose up --build -d`；健康检查覆盖 MySQL、后端、前端。
- 持久化：`mysql-data` 与 `storage` 两个命名卷；`docker compose down` 不删除卷。
- 备份：`docker run --rm -v framelab_mysql-data:/data -v $PWD:/backup alpine tar czf /backup/mysql-data.tgz -C /data .`；`storage` 同理。
- 回滚：保留上一版镜像 tag，`docker compose down` 后切回旧镜像/提交并 `up -d`；数据库迁移一经提交不修改，修复用新版本。
- 故障排查与生产注意见 [docs/07-testing-ci-deploy.md](docs/07-testing-ci-deploy.md)。

## 12. 完成度与已知限制

已完成：R-01～R-14 的功能闭环、E-01 受理幂等、E-02 自动重试、E-03 断点续跑、E-04 中英文；X5 图片模板调色/轻人像与照片轮播视频；五个页面、响应式、无障碍与视觉证据；Compose、CI 与 README；扩展 X1 的刷新令牌轮换、访问令牌即时失效与退出全部（[X1 验收](docs/evidence/extensions/x1-acceptance.md)、[X5 证据](docs/evidence/extensions/x5-effects-video.md)）。

已知限制：

- 单实例部署：登录限流在进程内生效；刷新轮换与令牌版本校验依赖共享 MySQL，本身可跨实例，但任务恢复、本地存储与 ffmpeg 并发信号量仍需单实例。
- 账号禁用会阻止新登录、刷新与已签发访问令牌（过滤器校验状态）；尚未提供管理端禁用界面。普通退出只撤销当前设备，其他设备最多再存活 15 分钟；账号弹层可用“退出所有设备”立即吊销全部。
- 本地卷存储：结果合成使用 Java2D 与自托管 Inter 字体，视频使用本机 ffmpeg；未接入 S3/CDN。
- 不提供注册、找回密码、删除账号、任务删除/重做、结果下载；视频上传/混剪未排期。
- 视频模板只接受照片（3～5 张），输出 15 秒 1080×1920 H.264/AAC；视频内文字固定英文 ASCII，不随界面语言变化。
- ffmpeg 缺失或关闭时，视频模板提交返回 503 `VIDEO_UNAVAILABLE`；图片模板不受影响。图片模板生成的调色效果依赖 LUT 与参数，不做 AI 内容理解。
- Flyway 对 MySQL 8.4 仍输出“支持未测试”警告；迁移已在 8.4 上验证。
- 模拟控制为全局单例，仅用于演示；生产不应暴露管理端。
- 生产需在反向代理配置 HTTPS/HSTS，并限制 3000/8080 暴露。

实际投入：待作者填写（设计 __ h、后端 __ h、前端 __ h、测试与 CI __ h、文档 __ h）；提交前请替换本行。

## 13. AI 使用说明

本项目在 AI 编程助手（Qoder）辅助下完成，流程如下：

- 先以 `docs/` 建立需求、领域、API、视觉、测试与提交规范，再按 M0～M8 里程碑实施；X5 模板特效/视频沿用同一流程并单独留证。
- 提供给助手的上下文包括需求文档、验收清单、参考截图与既有代码约束；代码、迁移、测试与文档同批更新。
- 验证不只依赖生成结果：后端 125 个测试（含 ffmpeg 视频用例）、前端 55 个测试、生产构建、真实浏览器五档宽度、断网/重启/切账号/失败注入均实际执行，并归档于 [docs/evidence](docs/evidence/)。
- 一个具体修正：M7 视觉复核发现任务卡状态位于左上角、模板封面为抽象渐变，与硬约束不符；改为状态居中并替换为照片封面后重新截图。安全巡检发现缺少 CSP，已在 `next.config.ts` 补齐并在浏览器验证。
- X5 修正：浏览器实测视频无法播放，控制台显示 CSP 拦截 blob；补 `media-src 'self' blob:` 后 `<video>` readyState=4。抽帧发现标题为缺字方框，原因是 `Locale.ENGLISH` 不命中 `en_US` 资源包，改为 `Locale.US` 后恢复 ASCII 文字。

## 14. 第三方资源

- Inter Variable 与 JetBrains Mono：SIL Open Font License 1.1，自托管于 `frontend/src/assets/fonts` 与后端渲染资源。
- 图标：lucide-react（ISC）。
- 模板封面：AI 生成的演示照片，随仓库提供，仅用于演示；来源与许可说明见 [backend/src/main/resources/static/assets/CREDITS.md](backend/src/main/resources/static/assets/CREDITS.md)。
- LUT、背景音乐与新增封面：本项目自制，随仓库提供；清单与许可见 [docs/assets/template-resources.md](docs/assets/template-resources.md)。
- ffmpeg：LGPL/GPL（取决于构建），仅作为运行时依赖调用，不随源码分发。
- 参考界面截图位于 [docs/assets/reference](docs/assets/reference)，仅用于布局对照。

## 15. 交付状态

- [x] 设计与验收文档
- [x] 前后端完整源码
- [x] 数据库结构与种子
- [x] 五个业务页面与视觉对齐
- [x] 后端 125 / 前端 55 测试
- [x] X5 图片调色、轻人像与 15 秒视频模板（含 Range/封面播放）
- [x] Docker Compose 一键启动
- [x] GitHub Actions CI
- [x] 演示脚本、AI 使用说明、已知限制
- [x] 验收 tag：`v1.0-submission`（M8 干净环境 15/15 + 全量链路 21/21 通过）
