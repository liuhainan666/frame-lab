# FrameLab · 素材制作平台

深色画廊风格的素材制作网站：浏览模板、上传图片、提交三步异步制作任务，并在处理期间继续使用网站，最后查看个人历史与结果。前端使用 Next.js，后端使用 Spring Boot 3 + Spring Security + MyBatis，数据持久化到 MySQL 8.4。

> 当前状态：M5 前端底座与登录完成（Next.js 15、设计 Token、基础组件、i18n、API 客户端、会话与登录页），下一步进入 M6 五个页面与视觉对齐。设计文档仍是开发与验收的唯一口径。

## 1. 文档入口

| 文档 | 内容 |
|---|---|
| [docs/README.md](docs/README.md) | 总览、范围、技术栈、术语与文档索引 |
| [docs/01-requirements.md](docs/01-requirements.md) | R-01 ～ R-14 需求拆解与验收映射 |
| [docs/02-domain-model.md](docs/02-domain-model.md) | 领域模型、状态机、失败模型与不变量 |
| [docs/03-database.md](docs/03-database.md) | MySQL DDL、索引、种子数据 |
| [docs/04-api.md](docs/04-api.md) | REST 契约、错误模型、轮询语义 |
| [docs/05-architecture.md](docs/05-architecture.md) | 模块边界、异步流水线、安全设计 |
| [docs/06-frontend-design.md](docs/06-frontend-design.md) | 设计 Token、组件、页面与状态规范 |
| [docs/07-testing-ci-deploy.md](docs/07-testing-ci-deploy.md) | 测试矩阵、CI、Compose、演示与回滚 |
| [docs/08-acceptance.md](docs/08-acceptance.md) | 验收清单、演示脚本与交付清单 |
| [docs/09-git-commit.md](docs/09-git-commit.md) | 提交规范、scope 与分层提交顺序 |
| [docs/10-reliability-i18n.md](docs/10-reliability-i18n.md) | 受理幂等、自动重试、断点续跑、国际化 |
| [docs/11-development-plan.md](docs/11-development-plan.md) | 里程碑、任务依赖、页面实施与阶段门禁 |
| [docs/assets/reference](docs/assets/reference) | UI-01 ～ UI-04 参考截图 |

## 2. 技术栈

| 层 | 选型 | 版本策略 |
|---|---|---|
| 前端 | Next.js App Router + React + TypeScript | Next 15.x / React 19.x / TS 5.x，锁定在 `pnpm-lock.yaml` |
| 前端样式 | Tailwind CSS 4 + CSS Variables + Radix Primitives | 锁定在 lockfile |
| 前端状态 | TanStack Query 5 + Zustand 5 | 锁定在 lockfile |
| 后端 | Java 21 + Spring Boot 3.4.x | 锁定在 `pom.xml` |
| 安全 | Spring Security 6.4 + JWT（Nimbus JOSE） | 随 Spring Boot BOM |
| 持久层 | MyBatis 3 | 锁定在 `pom.xml` |
| 数据库 | MySQL 8.4 LTS | Docker 镜像锁定 8.4.x |
| 迁移 | Flyway | 随 Spring Boot BOM |
| 测试 | JUnit 5 + MockMvc + Testcontainers；Vitest + Testing Library + MSW | 锁定在 lockfile |
| 交付 | Docker Compose + GitHub Actions | 见 [docs/07](docs/07-testing-ci-deploy.md) |

最终版本以锁文件与 `pom.xml` 为准。

## 3. 目录结构（规划）

```text
.
├─ backend/                  Spring Boot 3 后端（M1 起）
│  ├─ src/main/java/         API、领域、Mapper、worker、渲染
│  ├─ src/main/resources/    application.yml、Mapper XML、Flyway 迁移
│  └─ src/test/              单元与集成测试
├─ frontend/                 Next.js 15 前端（M5 起）
│  └─ src/                   页面、组件、features、styles、i18n
├─ docs/                     设计与验收文档
├─ .env.example              非敏感配置示例
├─ docker-compose.yml        M8 收口
└─ README.md
```

## 4. 环境要求与当前检查

检查时间：2026-10-29。

| 工具 | 要求 | 本机版本 | 状态 |
|---|---|---|---|
| Git | 任意近期版本 | 2.55.0 | 可用 |
| Node.js | 22 LTS 及以上 | 24.20.0 | 可用 |
| pnpm | 与 `packageManager` 一致 | 12.16.1 | 已通过 corepack 启用 |
| JDK | 21 LTS | 21.0.13 LTS（Microsoft OpenJDK） | 已安装；`JAVA_HOME` 已写入用户环境 |
| Maven | 使用 Maven Wrapper | 3.10.0（随仓库 `mvnw`） | 可用 |
| Docker | 支持 Compose 的近期版本 | 29.6.2 | 可用 |
| Docker Compose | v2 及以上 | v5.3.1 | 可用 |

安装 JDK 后需新开终端或重启 IDE，才能继承 `JAVA_HOME` 与 `PATH`。当前会话可用以下方式验证：

```bash
java -version
pnpm --version
docker compose version
```

## 5. 快速开始（M8 完成后启用）

目标启动方式：

```bash
cp .env.example .env
docker compose up --build
```

完成后访问 `http://localhost:3000`，后端 `http://localhost:8080`。本地前后端分别启动、数据库初始化、演示账号与模拟控制命令将在 M8 写入本文件与 [docs/07](docs/07-testing-ci-deploy.md)。

在 M8 之前，本仓库尚不能一键启动完整应用；请按 [docs/11-development-plan.md](docs/11-development-plan.md) 的阶段推进。

### 5.1 当前里程碑验证（M1 ～ M5）

```bash
cd backend
./mvnw -B verify

cd ../frontend
pnpm install --frozen-lockfile
pnpm lint
pnpm typecheck
pnpm test
pnpm build
```

Testcontainers 会启动独立的 MySQL 8.4，执行 V1/V2，并校验迁移、种子、登录会话、刷新令牌、问题响应、模板/分类、素材、任务提交与三步流水线，以及自动重试、退避、预算耗尽、终态失败与启动恢复。当前后端 97 个用例覆盖 BE-I-02 ～ 31 与 BE-U-07 ～ 25。

前端 20 个用例覆盖 FE-U-15/16/25/27/30、语言切换与查询键、problem+json 映射、单飞刷新、登录校验/错误/回跳与基础组件；五个业务页面在 M6 接入。UI 冒烟已验证语言切换即时生效并持久化、登录回到 `returnTo`、受保护路由重定向与退出登录。

手工联调（先启动 Compose MySQL，并以 `.env` 中的 `MYSQL_*` 环境变量运行 `spring-boot:run`）：

```bash
curl -s http://localhost:8080/api/templates -H 'Accept-Language: en-US'
curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"demo@framelab.local","password":"Demo1234!"}'
```

登录响应中的 `accessToken` 用于 `Authorization: Bearer`；刷新令牌只通过 HttpOnly Cookie `framelab_rt` 传递，素材内容必须经 `/api/assets/{id}/content` 鉴权读取。

## 6. 配置与安全

- `.env` 不入库；仓库只保留 [.env.example](.env.example) 中的演示值。
- `FRAMELAB_JWT_SECRET` 至少 32 字节；生产 profile 必须替换为强随机值，否则启动失败；生产必须启用 HTTPS 与 `FRAMELAB_COOKIE_SECURE=true`。
- 访问令牌放在前端内存并以 `Authorization: Bearer` 发送；刷新令牌是 HttpOnly Cookie（`Path=/api/auth; SameSite=Lax`）。业务接口不依赖 Cookie 自动鉴权，因此关闭 CSRF；刷新与退出只操作会话，不改变业务数据，前端禁止用 GET 执行写操作。
- CORS 仅允许 `.env` 中的 `FRAMELAB_CORS_ALLOWED_ORIGINS`（默认 `http://localhost:3000`），允许 `Authorization`、`Content-Type`、`Accept-Language` 并暴露 `Location`、`Idempotent-Replay`、`X-Request-Id`、`Retry-After`、`Content-Language`、`Content-Disposition`。
- 演示账号、数据库口令仅用于本地与评审，不得用于生产。
- 素材与结果存放在服务端私有目录，经鉴权接口读取；浏览器 `blob:` 只用于预览。
- 仓库不包含真实密钥、用户照片、数据库卷或构建产物。

## 7. 里程碑

| 里程碑 | 内容 | 状态 |
|---|---|---|
| M0 | 仓库、忽略规则、环境与文档基线 | 完成 |
| M1 | 数据库迁移与种子数据 | 完成 |
| M2 | 安全、错误/i18n、模板与素材接口 | 完成 |
| M3 | 任务受理、流水线、模拟与结果 | 完成 |
| M4 | 自动重试、失败收口、续跑 | 完成 |
| M5 | 前端底座与登录 | 完成 |
| M6 | 五个页面与视觉对齐 | 下一步 |
| M7 | 联调与验收 | 未开始 |
| M8 | Compose、CI、README 与验收 commit | 未开始 |

## 8. 已知限制（开发中）

- Flyway 10.20.1 对 MySQL 8.4 会输出“支持未测试”的警告；V1/V2 已在 MySQL 8.4.13 上执行通过，升级 Flyway 时复核。
- 五个业务页面、Compose 全链路与 CI 尚未实现；当前后端已具备登录会话、模板/分类、素材、任务提交/查询、三步流水线、自动重试、启动恢复、模拟控制与结果读取能力，前端已具备登录、会话恢复、语言切换与应用外壳。
- 数据库连接使用 `READ COMMITTED`，避免 `SKIP LOCKED` 认领在 `REPEATABLE READ` 下产生间隙锁死锁；调整隔离级别时需复跑并发认领用例。
- 本地 `.env` 若沿用旧的 root 账号或 3306 端口，需按 [.env.example](.env.example) 的演示值调整，或改用独立端口与账号；仓库不会提交 `.env`。
- 开发默认使用固定 JWT 密钥；生产 profile 必须提供至少 32 字节的随机 `FRAMELAB_JWT_SECRET`，否则启动失败。

## 9. 交付状态（R-13）

- [x] 设计与验收文档
- [x] 开发计划与阶段门禁
- [ ] 前后端完整源码
- [x] 数据库结构与初始化数据
- [x] 后端安全与基础 API（登录 / 刷新 / 退出 / 模板 / 素材）
- [x] 后端任务核心（提交 / 查询 / 三步流水线 / 模拟 / 结果）
- [x] 后端单元与 Testcontainers 集成测试
- [x] 前端底座与登录（Next.js、Token、i18n、会话、登录页）
- [ ] 五个业务页面与视觉对齐
- [ ] 测试与 CI 配置
- [ ] Docker Compose 一键启动
- [ ] 演示脚本、AI 使用说明、已知限制
- [ ] 验收 commit

## 10. 参考界面

参考截图位于 [docs/assets/reference](docs/assets/reference)：UI-01 模板列表、UI-02 模板详情/上传、UI-03 处理中与历史、UI-04 账号。页面按“主布局与信息层级对齐、不逐像素复刻”的口径实现，逐页对照与差异登记见 [docs/11-development-plan.md](docs/11-development-plan.md#5-页面实施与参考图对齐)。

## 11. 第三方资源

- Inter Variable 与 JetBrains Mono 以 SIL Open Font License 1.1 自托管，字体与许可证位于 [frontend/src/assets/fonts](frontend/src/assets/fonts)。
- 模板封面来源与许可证见 [backend/src/main/resources/static/assets/CREDITS.md](backend/src/main/resources/static/assets/CREDITS.md)。
