# 09 · Git 提交规范

> 本文件是本仓库提交信息的唯一口径。提交前对照第 8 节检查清单。
> 目标：一个提交只做一件事，历史可按“模块 × 层次”复盘，任意提交都可编译、可回滚、可审查。

## 1. 硬规则

1. **一个提交只改一个模块、一个层次。** 例如只改 `task` 的 Mapper，不把 Controller、前端页面、迁移混在一起。
2. **一个提交只放“同生共死”的 1～3 个文件。** 删掉其中任意一个，其他文件就编译不过或失去意义，才可放一起；超过 3 个必须在正文说明原因。
3. **按依赖顺序逐层提交。** 后端：迁移 → 领域/DTO → Mapper → Service → Controller → 测试；前端：类型/API → Query/Hook → 组件 → 页面/路由。
4. **契约变更必须同批。** 表结构、枚举、失败码、API 字段、重试/续跑语义、语言文案键变化，必须同时改文档、DDL/迁移、后端 DTO、前端类型、MSW 夹具与测试，不允许只改一边。
5. **每个提交可编译。** 不提交半截文件、空实现、注释掉的路由；确需分步，用 feature 分支并在合并前补齐。
6. **不提交秘密与运行数据。** `.env`、JWT 密钥、MySQL 数据卷、`storage/`、用户照片、`target/`、`node_modules/`、`.next/`、IDE 配置一律不入库。
7. **格式化、重命名、依赖升级、文档修订不搭车。** 各自独立提交；UI 视觉调整属于 `feat`/`fix`，不要用 `style`。

补充：

- 数据库迁移一经提交不得修改；修复用新版本 `V3__...`。
- 演示密码与种子账号可以入库（仅演示），但必须在 README 标注，且不得用于生产。
- 验收 commit 必须是可 `docker compose up --build` 的干净提交。

## 2. 提交信息格式

采用 Conventional Commits，描述使用中文：

```
<type>(<scope>): <subject>

<正文：为什么 / 做了什么 / 影响与验证>

<页脚>
```

示例：

```
feat(api/task): 实现任务提交与三步异步流水线

为什么：任务必须在服务端持久化并顺序执行，前端不能驱动状态。
做了什么：新增 task/task_step/task_material 写入、SKIP LOCKED 认领、
MATERIAL_CHECK→GENERATE→FINALIZE 三步执行与失败收口。
影响：新增 POST /api/tasks、GET /api/tasks/{id}；
失败后后续步骤为 SKIPPED；任务终态不可逆。
验证：BE-I-11、BE-I-14、BE-I-15、BE-I-17、BE-I-25 通过。

Refs: docs/02-domain-model.md, docs/04-api.md
```

### 2.1 type

| type | 用途 | 示例 |
|---|---|---|
| `feat` | 新增对外可见功能（接口、页面、交互、视觉） | `feat(web/studio): 新增素材上传位与逐文件进度` |
| `fix` | 修复错误行为、状态、数据归属 | `fix(api/task): 修复失败后仍执行后续阶段` |
| `refactor` | 不改变外部行为的结构调整、拆层、改名 | `refactor(api/asset): 将图片规范化移入独立组件` |
| `perf` | 查询、渲染、上传性能优化 | `perf(api/task): 为历史查询增加用户与时间复合索引` |
| `test` | 只新增或调整测试 | `test(web/studio): 补充多任务互不串扰用例` |
| `docs` | 只修改文档 | `docs(api/simulation): 补充模拟控制接口契约` |
| `style` | 只改代码格式（Prettier/ESLint 格式化、空白），不含界面样式 | `style(api/common): 统一 Java 缩进与 import` |
| `build` | 依赖、构建、打包、Dockerfile | `build(docker): 增加前端 standalone 镜像` |
| `chore` | 杂务：`.gitignore`、脚本、IDE 配置 | `chore(repo): 忽略 storage 与本地环境文件` |
| `revert` | 回滚既有提交 | `revert: feat(api/task): 实现任务提交` |

注意：

- UI 视觉、布局、状态样式属于 `feat`/`fix`，不要写 `style`。
- `reactor`、`feature`、`update` 不是合法 type。
- 一个提交只允许一个 type。

### 2.2 scope

| 范围 | scope |
|---|---|
| 后端身份 | `api/auth` `api/user` `api/security` |
| 后端业务 | `api/template` `api/asset` `api/task` `api/simulation` |
| 后端横切 | `db`（迁移）、`infra`（存储/配置）、`api/common`（错误/分页）、`api/i18n`（语言/消息） |
| 前端业务 | `web/auth` `web/templates` `web/studio` `web/tasks` `web/history` `web/account` |
| 前端公共 | `web/ui`（Token/基础组件）、`web/app`（布局/路由/Provider）、`web/i18n`（词典/Provider） |
| 工程 | `deps` `build` `ci` `docker` `docs` `repo` |

新增模块时，先在本表补一行，再开始提交。前端 scope 必须带 `web/` 前缀，避免与后端同名模块混淆。

### 2.3 subject

- 中文，动词开头：新增 / 实现 / 修复 / 调整 / 移除 / 拆分 / 重构 / 升级 / 补充。
- 说清“对象 + 结果”，不写过程；长度 ≤ 50 个字符；句末不加句号。
- 不用“更新代码”“修改 bug”“优化一下”“完善功能”等无法复盘的说法。
- issue 编号、文档链接放页脚，不塞进 subject。

### 2.4 正文与页脚

小改动可只写 subject。以下情况必须写正文，按“为什么 / 做了什么 / 影响与验证”三段：

- 数据库迁移、字段、索引、枚举变化；
- 状态机、失败码、步骤顺序、并发/幂等语义变化；
- 重试策略、退避参数、续跑计数、启动恢复逻辑变化；
- 语言协商、消息键、模板翻译、词典结构变化；
- API 契约（路径、字段、状态码）变化；
- 安全、鉴权、文件路径、权限归属变化；
- 前端轮询、缓存键、账号切换逻辑变化；
- 回滚成本较高或需要后续跟进的改动。

页脚：

```
Refs: docs/02-domain-model.md, docs/04-api.md
Closes: #12
```

## 3. 本项目分层与文件落点

| 层 | 目录 | 提交边界 |
|---|---|---|
| 文档 | `docs/` | 一个主题一个提交；设计基线与后续修订分开 |
| 数据库 | `backend/src/main/resources/db/migration/` | 一个迁移一个提交；seed 与 schema 分开 |
| 后端领域 | `backend/src/main/java/com/framelab/*/domain/` | 枚举/值对象/异常一起 |
| 后端 Mapper | `.../mapper/` + `resources/mapper/*.xml` | Java 接口与 XML 同一提交 |
| 后端 Service | `.../*/service` 或模块内 Service | 一个用例一个提交 |
| 后端 Controller | `.../*/Controller.java` + DTO | 接口与 DTO 一起 |
| 后端 Worker | `task/worker/`、`task/step/`、`task/render/` | 认领、重试、恢复、流水线分开提交 |
| 后端国际化 | `common/i18n/`、`resources/i18n/`、翻译种子 | 过滤器/消息源与文案分开 |
| 前端契约 | `frontend/src/features/*/{types.ts,api.ts}` | 与后端契约同批 |
| 前端数据 | `frontend/src/features/*/{queries.ts,hooks/}` | Query key 与轮询一起 |
| 前端视图 | `frontend/src/features/*/components/` | 组件与其状态测试一起 |
| 前端国际化 | `frontend/src/i18n/`、`LocaleSwitcher` | 词典、Provider、切换控件分开 |
| 前端页面 | `frontend/src/app/**/page.tsx` | 页面装配与路由一起 |
| 设计系统 | `frontend/src/styles/tokens.css` + `shared/ui/` | Token 与基础组件分开 |
| 工程 | `docker-compose.yml`、`Dockerfile`、`.github/workflows/ci.yml` | 各自独立提交 |

## 4. 推荐提交顺序

### 4.1 设计基线

```bash
git add docs/
git commit -m "docs: 建立全栈素材制作平台设计与验收基线"
```

### 4.2 数据库

```bash
# 1) 表结构
git add backend/src/main/resources/db/migration/V1__schema.sql docs/03-database.md
git commit -m "feat(db): 新增用户、模板、素材、任务与步骤表"

# 2) 种子数据
git add backend/src/main/resources/db/migration/V2__seed.sql
git commit -m "feat(db): 新增演示账号、分类、模板与模拟配置种子"
```

### 4.3 后端骨架与安全

```bash
git add backend/pom.xml backend/mvnw backend/mvnw.cmd
git commit -m "build(backend): 初始化 Spring Boot 3 与 MyBatis 依赖"

git add backend/src/main/java/com/framelab/FrameLabApplication.java backend/src/main/resources/application.yml
git commit -m "feat(api/common): 新增应用入口与基础配置"

git add backend/src/main/java/com/framelab/config/SecurityConfig.java backend/src/main/java/com/framelab/auth/
git commit -m "feat(api/security): 新增 JWT 鉴权与刷新令牌"

git add backend/src/test/java/com/framelab/auth/
git commit -m "test(api/auth): 补充令牌校验与登录限流用例"

git add backend/src/main/java/com/framelab/common/i18n/ backend/src/main/resources/i18n/
git commit -m "feat(api/i18n): 新增语言协商与本地化消息源"
```

### 4.4 后端业务（以 task 为例）

```bash
# 1) 领域枚举与失败模型
git add backend/src/main/java/com/framelab/task/domain/
git commit -m "feat(api/task): 新增任务状态机与失败码"

# 2) Mapper 与 XML
git add backend/src/main/java/com/framelab/task/TaskMapper.java backend/src/main/resources/mapper/TaskMapper.xml
git commit -m "feat(api/task): 新增任务与步骤数据访问"

# 3) 提交服务
git add backend/src/main/java/com/framelab/task/TaskSubmissionService.java
git commit -m "feat(api/task): 实现提交校验与幂等创建"

# 4) 查询服务
git add backend/src/main/java/com/framelab/task/TaskQueryService.java
git commit -m "feat(api/task): 实现个人任务列表与详情查询"

# 5) worker 与流水线
git add backend/src/main/java/com/framelab/task/worker/ backend/src/main/java/com/framelab/task/step/
git commit -m "feat(api/task): 实现数据库认领与三步顺序执行"

# 6) 自动重试与尝试记录
git add backend/src/main/java/com/framelab/task/worker/RetryScheduler.java \
        backend/src/main/java/com/framelab/task/TaskStepAttemptMapper.java \
        backend/src/main/resources/mapper/TaskStepAttemptMapper.xml
git commit -m "feat(api/task): 实现可重试失败与指数退避"

# 7) 启动恢复
git add backend/src/main/java/com/framelab/task/worker/StartupRecovery.java
git commit -m "feat(api/task): 实现服务重启后的分段续跑"

# 8) 结果渲染与存储
git add backend/src/main/java/com/framelab/task/render/ backend/src/main/java/com/framelab/infrastructure/storage/
git commit -m "feat(api/task): 实现结果合成与本地原子写入"

# 9) Controller 与 DTO
git add backend/src/main/java/com/framelab/task/TaskController.java backend/src/main/java/com/framelab/task/dto/
git commit -m "feat(api/task): 暴露任务提交、查询与结果接口"

# 10) 测试
git add backend/src/test/java/com/framelab/task/
git commit -m "test(api/task): 覆盖重试、续跑、步骤顺序与多任务隔离"
```

### 4.5 前端（以 studio 为例）

```bash
# 1) 契约
git add frontend/src/features/studio/types.ts frontend/src/features/studio/api.ts
git commit -m "feat(web/studio): 新增制作台接口类型与客户端"

# 2) 数据与轮询
git add frontend/src/features/studio/queries.ts frontend/src/features/tasks/hooks/useTaskPolling.ts
git commit -m "feat(web/tasks): 新增任务轮询与缓存键"

# 3) 国际化基础
git add frontend/src/i18n/messages/ frontend/src/i18n/config.ts frontend/src/i18n/provider.tsx \
        frontend/src/i18n/locale-cookie.ts frontend/src/i18n/format.ts
git commit -m "feat(web/i18n): 新增中英词典、语言 Cookie 与格式化"

# 4) 视图
git add frontend/src/features/studio/components/
git commit -m "feat(web/studio): 实现模板信息、上传位与制作记录"

# 5) 语言切换控件
git add frontend/src/features/account/components/LocaleSwitcher.tsx
git commit -m "feat(web/account): 新增顶栏与账号弹层语言切换"

# 6) 页面
git add frontend/src/app/templates/[templateId]/page.tsx
git commit -m "feat(web/studio): 接入模板详情与制作台页面"

# 7) 测试
git add frontend/src/features/studio/__tests__/
git commit -m "test(web/studio): 覆盖重试卡片、上传状态与多任务互不串扰"
```

### 4.6 跨端契约变更

当 API 字段变化时，一个提交可以包含以下“同生共死”的文件，但必须在正文说明：

```bash
git add docs/04-api.md backend/src/main/java/com/framelab/task/dto/ \
        frontend/src/features/tasks/types.ts frontend/src/test/fixtures/tasks.ts \
        backend/src/test/java/com/framelab/task/TaskApiTest.java
git commit -m "refactor(api/common): 统一任务阶段字段为 currentStage

为什么：前后端对阶段字段命名不一致，导致状态映射遗漏。
做了什么：文档、DTO、前端类型与夹具统一为 currentStage。
影响：仅字段重命名，无状态语义变化。
验证：契约测试与前端组件测试通过。"
```

### 4.7 工程与交付

```bash
git add .github/workflows/ci.yml
git commit -m "ci: 新增前后端 lint、类型检查、测试与构建流水线"

git add backend/Dockerfile frontend/Dockerfile docker-compose.yml .env.example
git commit -m "build(docker): 新增前后端与 MySQL 的 Compose 交付"

git add README.md docs/07-testing-ci-deploy.md
git commit -m "docs: 补充启动、演示、部署与回滚说明"
```

## 5. 分支与合并

- `main` 保持可构建、可演示；功能在 `feat/<scope>-<short>` 分支开发。
- 合并前自查：CI 绿、`docker compose up --build` 成功、验收清单 P0 条目通过。
- 不强制要求 PR；如使用 PR，描述包含：改动范围、演示步骤、测试证据、已知限制。
- 禁止对 `main` 强制推送；禁止提交后修改已发布迁移。
- 合并策略不强制 squash；保持“一提交一件事”的历史比合成一个大提交更有价值。

## 6. 提交信息模板

```text
<type>(<scope>): <subject ≤ 50 字>

为什么：
<问题/需求/约束>

做了什么：
<关键实现点，1～3 条>

影响与验证：
<接口/DB/状态机/安全影响；执行的测试或手工步骤>

Refs: <文档或 issue>
```

## 7. 好例与反例

好：

```
feat(api/task): 实现失败后停止后续阶段
fix(web/tasks): 修复查询异常被误标为任务失败
refactor(api/asset): 拆分图片规范化与存储写入
test(api/task): 覆盖重试耗尽与启动续跑
test(api/i18n): 覆盖语言协商与翻译回退
docs(db): 补充任务快照字段与索引说明
build(docker): 固定 MySQL 8.4 与 JRE 21 镜像
```

不好：

```
update code                    # 无法复盘
fix bug                        # 不知道修了什么
feat: 完成所有功能              # 一个提交跨多个模块
style(web): 调整页面视觉        # UI 视觉应为 feat
feat(api/task,web/studio): ... # 一个提交两个模块
```

## 8. 提交前检查清单

- [ ] `git status` 只包含本次提交预期的文件；无 `.env`、`storage/`、`target/`、`node_modules/`、`.next/`。
- [ ] 提交只涉及一个模块、一个层次；≤3 个文件或正文说明原因。
- [ ] 后端 `cd backend && ./mvnw -B verify` 通过。
- [ ] 前端 `cd frontend && pnpm lint && pnpm typecheck && pnpm test && pnpm build` 通过。
- [ ] 契约变化已同步 docs/02、03、04、前端类型、MSW 夹具、测试。
- [ ] 重试/续跑/幂等变化已同步 docs/10、DDL、接口与尝试记录测试。
- [ ] 语言文案与翻译变化已同步 docs/10、消息键、词典与翻译种子。
- [ ] 状态机/失败码变化已同步 docs/06 状态矩阵与验收清单。
- [ ] 数据库迁移未被修改；新迁移编号连续。
- [ ] 无真实密钥、令牌、用户照片、完整邮箱进入 diff。
- [ ] 日志与错误响应不含堆栈、SQL、磁盘路径。
- [ ] commit message 的 type/scope 合法，subject 动词开头且 ≤50 字。
- [ ] 如影响演示，已更新 docs/07 演示脚本或 README。
- [ ] 验收 commit 可 `docker compose up --build` 并从空库完成一次成功任务。

## 9. 仓库基线

当前仓库尚未初始化 Git。首次提交建议：

```bash
git init
git add docs/需求.doc docs/README.md docs/0*.md docs/10-reliability-i18n.md docs/11-development-plan.md docs/assets/
git commit -m "docs: 建立全栈素材制作平台设计与验收基线"
```

随后按第 4 节顺序推进，每完成一层提交一次；最终在 README 标注**验收 commit**（完整或短哈希），并确保该提交在干净环境可启动。
