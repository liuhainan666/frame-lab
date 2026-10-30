# FrameLab · 素材制作平台

深色画廊风格的素材制作网站：浏览模板、上传照片、提交三步异步制作任务，在处理期间继续浏览，最后查看个人历史与结果。前端 Next.js，后端 Spring Boot 3 + Spring Security + MyBatis，MySQL 8.4 持久化。

- 验收 commit：`dc06422`（容器与 CI 位于 `004eebe..22ef184`；干净环境证据见 [docs/evidence/m8-acceptance.md](docs/evidence/m8-acceptance.md)）
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
| 运行时 | Node / pnpm | 容器 Node 22，本机 Node 24 / pnpm 12.16.1 |
| 交付 | Docker Compose / GitHub Actions | Compose v2+；workflow 见 `.github/workflows/ci.yml` |

## 2. 项目结构

```text
.
├─ backend/                     Spring Boot API、worker、Java2D 渲染
│  ├─ src/main/java/com/framelab/
│  ├─ src/main/resources/db/migration/   V1 表结构、V2 种子
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

要求：Docker Desktop / Engine + Compose v2。

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

Windows 需设置 `JAVA_HOME` 指向 JDK 21；本仓库测试使用 `./mvnw verify`。

### 4.3 前端

```bash
cd frontend
pnpm install --frozen-lockfile
NEXT_PUBLIC_BACKEND_URL=http://localhost:8080 pnpm dev
```

默认 `http://localhost:3000`。开发服务器端口变化时，后端 `FRAMELAB_CORS_ALLOWED_ORIGINS` 要同步。

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
| `FRAMELAB_COOKIE_SECURE` | `false` | 生产 HTTPS 设为 `true` |
| `FRAMELAB_WORKER_THREADS` | `2` | 任务 worker 并发数 |
| `FRAMELAB_WORKER_POLL_MS` | `500` | 认领轮询间隔 |
| `FRAMELAB_RETRY_MAX_STEP_ATTEMPTS` | `3` | 每步尝试上限快照 |
| `FRAMELAB_RETRY_BACKOFF_BASE_SECONDS` | `2` | 退避基数，实际 `min(base×2^(n-1), max)` |
| `FRAMELAB_RETRY_BACKOFF_MAX_SECONDS` | `30` | 退避上限 |
| `NEXT_PUBLIC_BACKEND_URL` | `http://localhost:8080` | 浏览器访问 API 的地址，构建时内联 |
| `FRONTEND_PORT` | `3000` | Compose 前端宿主端口 |

后端还支持 `FRAMELAB_STORAGE_ROOT`（容器内 `/app/storage`）与 `SPRING_PROFILES_ACTIVE`。

## 6. 数据库

- 迁移：`V1__schema.sql`（用户、模板、素材、任务、步骤、尝试、模拟配置）、`V2__seed.sql`（演示账号、分类、模板、模拟默认值）。
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
| POST | `/api/auth/refresh` | 刷新访问令牌（单飞、轮换） |
| POST | `/api/auth/logout` | 撤销刷新令牌 |
| GET | `/api/template-categories` | 分类，公开 |
| GET | `/api/templates` / `/api/templates/{id}` | 模板库与详情，公开 |
| POST | `/api/assets` | 上传 JPEG/PNG，私有 |
| GET/DELETE | `/api/assets/{id}/content` / `/api/assets/{id}` | 鉴权读取/删除素材 |
| POST | `/api/tasks` | 提交任务，`clientRequestId` 幂等 |
| GET | `/api/tasks` / `/api/tasks/{id}` | 当前用户任务列表/详情 |
| GET | `/api/tasks/{id}/result` | 鉴权读取结果 JPEG |
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

- 访问令牌只在浏览器内存，以 `Authorization: Bearer` 发送；刷新令牌为 HttpOnly Cookie（`Path=/api/auth; SameSite=Lax`）。
- 任务、素材、结果均按 `user_id` 查询；他人资源统一返回 `404`，不泄露存在性。
- 登录失败 5 次/15 分钟触发 `429`；口令 BCrypt cost 12；错误文案不区分账号是否存在。
- CORS 白名单、CSP、`X-Content-Type-Options`、`X-Frame-Options`、`Referrer-Policy`、`Permissions-Policy` 已配置。
- 素材与结果保存在私有卷，静态目录只公开模板封面；结果不可公开猜测。
- 详细安全设计见 [docs/05-architecture.md](docs/05-architecture.md)。

## 10. 测试与 CI

```bash
# 后端：单元 + Testcontainers MySQL 集成测试（97 个用例）
cd backend && ./mvnw verify

# 前端：类型、Lint、组件测试、生产构建（45 个用例）
cd frontend && pnpm typecheck && pnpm lint && pnpm test && pnpm build
```

覆盖登录/刷新/隔离、素材校验、三步流水线、受理幂等（含并发）、自动重试与退避、耗尽、终态失败、启动恢复与续跑、错误/i18n、前端多任务/查询异常/上传/筛选/详情。GitHub Actions 在 push 与 PR 触发前后端两个 job；容器构建不跑测试（CI 已跑）。

## 11. 部署、备份与回滚

- 一键启动：`docker compose up --build -d`；健康检查覆盖 MySQL、后端、前端。
- 持久化：`mysql-data` 与 `storage` 两个命名卷；`docker compose down` 不删除卷。
- 备份：`docker run --rm -v framelab_mysql-data:/data -v $PWD:/backup alpine tar czf /backup/mysql-data.tgz -C /data .`；`storage` 同理。
- 回滚：保留上一版镜像 tag，`docker compose down` 后切回旧镜像/提交并 `up -d`；数据库迁移一经提交不修改，修复用新版本。
- 故障排查与生产注意见 [docs/07-testing-ci-deploy.md](docs/07-testing-ci-deploy.md)。

## 12. 完成度与已知限制

已完成：R-01～R-14 的功能闭环、E-01 受理幂等、E-02 自动重试、E-03 断点续跑、E-04 中英文；五个页面、响应式、无障碍与视觉证据；Compose、CI 与 README。

已知限制：

- 单实例部署：登录限流与单飞刷新在进程内生效；水平扩展需共享 Redis 与对象存储。
- 本地卷存储：结果合成使用 Java2D 与自托管 Inter 字体，未接入 S3/CDN。
- 不提供注册、找回密码、删除账号、任务删除/重做、结果下载与视频生成。
- Flyway 对 MySQL 8.4 仍输出“支持未测试”警告；迁移已在 8.4 上验证。
- 模拟控制为全局单例，仅用于演示；生产不应暴露管理端。
- 生产需在反向代理配置 HTTPS/HSTS，并限制 3000/8080 暴露。

实际投入：待作者按真实工时填写（设计 / 后端 / 前端 / 测试与 CI / 文档）。

## 13. AI 使用说明

本项目在 AI 编程助手（Qoder）辅助下完成，流程如下：

- 先以 `docs/` 建立需求、领域、API、视觉、测试与提交规范，再按 M0～M8 里程碑实施；每个提交按模块与层次拆分。
- 提供给助手的上下文包括需求文档、验收清单、参考截图与既有代码约束；代码、迁移、测试与文档同批更新。
- 验证不只依赖生成结果：后端 97 个测试、前端 45 个测试、生产构建、真实浏览器五档宽度、断网/重启/切账号/失败注入均实际执行，并归档于 [docs/evidence/m7-acceptance.md](docs/evidence/m7-acceptance.md)。
- 一个具体修正：M7 视觉复核发现任务卡状态位于左上角、模板封面为抽象渐变，与硬约束不符；改为状态居中并替换为照片封面后重新截图。安全巡检发现缺少 CSP，已在 `next.config.ts` 补齐并在浏览器验证。

## 14. 第三方资源

- Inter Variable 与 JetBrains Mono：SIL Open Font License 1.1，自托管于 `frontend/src/assets/fonts` 与后端渲染资源。
- 图标：lucide-react（ISC）。
- 模板封面：AI 生成的演示照片，随仓库提供，仅用于演示；来源与许可说明见 [backend/src/main/resources/static/assets/CREDITS.md](backend/src/main/resources/static/assets/CREDITS.md)。
- 参考界面截图位于 [docs/assets/reference](docs/assets/reference)，仅用于布局对照。

## 15. 交付状态

- [x] 设计与验收文档
- [x] 前后端完整源码
- [x] 数据库结构与种子
- [x] 五个业务页面与视觉对齐
- [x] 后端 97 / 前端 45 测试
- [x] Docker Compose 一键启动
- [x] GitHub Actions CI
- [x] 演示脚本、AI 使用说明、已知限制
- [x] 验收 commit：`dc06422`（干净环境 15/15 + 全量链路 21/21 通过）
