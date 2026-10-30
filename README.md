# FrameLab · 素材制作平台

深色画廊风格的素材制作网站：浏览模板、上传图片、提交三步异步制作任务，并在处理期间继续使用网站，最后查看个人历史与结果。前端使用 Next.js，后端使用 Spring Boot 3 + Spring Security + MyBatis，数据持久化到 MySQL 8.4。

> 当前状态：M1 数据层完成（Spring Boot 骨架、Flyway V1/V2、演示封面与迁移测试），下一步进入 M2 安全与基础 API。设计文档仍是开发与验收的唯一口径。

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

### 5.1 当前里程碑验证（M1）

```bash
cd backend
./mvnw -B test
```

Testcontainers 会启动独立的 MySQL 8.4，执行 V1/V2 并校验 12 张表与种子行数。需要手工观察后端启动时，先启动 Compose 中的 MySQL，再以 `MYSQL_*` 环境变量运行 `spring-boot:run`。

## 6. 配置与安全

- `.env` 不入库；仓库只保留 [.env.example](.env.example) 中的演示值。
- `FRAMELAB_JWT_SECRET` 至少 32 字节；生产必须替换为强随机值并启用 HTTPS。
- 演示账号、数据库口令仅用于本地与评审，不得用于生产。
- 素材与结果存放在服务端私有目录，经鉴权接口读取；浏览器 `blob:` 只用于预览。
- 仓库不包含真实密钥、用户照片、数据库卷或构建产物。

## 7. 里程碑

| 里程碑 | 内容 | 状态 |
|---|---|---|
| M0 | 仓库、忽略规则、环境与文档基线 | 完成 |
| M1 | 数据库迁移与种子数据 | 完成 |
| M2 | 安全、错误/i18n、模板与素材接口 | 下一步 |
| M3 | 任务受理、流水线、模拟与结果 | 未开始 |
| M4 | 幂等、重试、续跑 | 未开始 |
| M5 | 前端底座与登录 | 未开始 |
| M6 | 五个页面与视觉对齐 | 未开始 |
| M7 | 联调与验收 | 未开始 |
| M8 | Compose、CI、README 与验收 commit | 未开始 |

## 8. 已知限制（开发中）

- Flyway 10.20.1 对 MySQL 8.4 会输出“支持未测试”的警告；V1/V2 已在 MySQL 8.4.13 上执行通过，升级 Flyway 时复核。
- 前端、鉴权、任务接口与 Compose 全链路尚未实现，当前只完成数据库层。

## 9. 交付状态（R-13）

- [x] 设计与验收文档
- [x] 开发计划与阶段门禁
- [ ] 前后端完整源码
- [x] 数据库结构与初始化数据
- [ ] 测试与 CI 配置
- [ ] Docker Compose 一键启动
- [ ] 演示脚本、AI 使用说明、已知限制
- [ ] 验收 commit

## 10. 参考界面

参考截图位于 [docs/assets/reference](docs/assets/reference)：UI-01 模板列表、UI-02 模板详情/上传、UI-03 处理中与历史、UI-04 账号。页面按“主布局与信息层级对齐、不逐像素复刻”的口径实现，逐页对照与差异登记见 [docs/11-development-plan.md](docs/11-development-plan.md#5-页面实施与参考图对齐)。
