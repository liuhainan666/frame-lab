# M8 交付与干净环境验收

- 日期：2026-10-30
- 验收 commit：`dc06422`
- 镜像：`framelab-acceptance-backend`、`framelab-acceptance-frontend`（从当前源码构建）
- 环境：Docker 29.6.2 / Compose v5.3.1；独立项目 `framelab-acceptance`，前端宿主 3300、API 8080、MySQL 宿主 13307；使用独立命名卷，未触碰本地演示库。

## 1. 空库启动

```bash
docker compose -p framelab-acceptance --env-file acceptance.env up --build -d
```

| 检查 | 结果 |
|---|---|
| 构建 | 前端、后端镜像均从源码构建成功 |
| 容器 | mysql / backend / frontend 均 `healthy` |
| 迁移 | Flyway 版本 2（V1 表结构 + V2 种子） |
| 种子 | users=3、templates=10、categories=6、tasks=0 |
| 端口 | 前端 3300、API 8080、MySQL 仅 127.0.0.1:13307 |
| 页面 | `http://localhost:3300` 返回 200，含 CSP 与 nosniff |
| CORS | 预检允许 `http://localhost:3300` |

## 2. 核心闭环（15/15）

| # | 检查 | 结果 |
|---|---|---|
| 1 | 后端健康 | `UP` |
| 2 | 模板种子 | 10 个 |
| 3 | 分类种子 | 6 个 |
| 4 | 空卷上传两张照片 | 201 ×2 |
| 5 | 三步流水线 | `QUEUED → MATERIAL_CHECK → GENERATE → FINALIZE → SUCCEEDED` |
| 6 | 结果 | 私有 JPEG 474,007 字节，`Cache-Control: private` |
| 7 | 受理幂等 | 重放 200 + `Idempotent-Replay: true` |
| 8 | 永久失败 | `GENERATE` 失败，`FINALIZE` SKIPPED |
| 9 | 失败不重试 | 全程无 `RETRYING` |
| 10 | 账号隔离 | demo2 读 demo 任务 404 |
| 11 | 新用户历史 | 0 个任务 |
| 12 | 语言协商 | en-US 返回 “Travel Postcard” |
| 13 | 前端 CSP | `default-src 'self'`、`connect-src` 正确 |
| 14 | nosniff | `X-Content-Type-Options: nosniff` |
| 15 | CORS | 允许前端源 |

任务证据：成功 `1dd837a7-c8d2-4f32-8536-6a64ccc81001`；失败 `cf575aad-b887-4276-ace8-d50cac0f8cea`。

## 3. 生产前端 UI

- 以 `demo@framelab.local` 在 `http://localhost:3300` 登录成功，回到 `/history`。
- 历史显示本次干净环境产生的 2 个任务：1 个成功（可打开结果）与 1 个失败（显示生成阶段与原因）。
- 页面无 CSP 阻断；刷新令牌只在首次匿名加载时返回预期 401。

## 4. 质量门禁

| 门禁 | 结果 |
|---|---|
| `./mvnw verify` | 97 tests，BUILD SUCCESS |
| `pnpm typecheck` | 通过 |
| `pnpm lint` | 通过 |
| `pnpm test` | 13 files / 45 tests |
| `pnpm build` | 通过 |
| `docker compose up --build` | 三服务 healthy，15/15 验收通过 |

## 5. 收尾

验收后执行 `docker compose -p framelab-acceptance down -v`，删除独立容器、网络与卷；本地演示环境未受影响。

## 6. 未在远程执行的事项

- GitHub Actions 工作流已配置（`push`/`pull_request`），本地等价命令全部通过；因未 push，远程运行记录需在首次推送后确认。
- 生产 HTTPS/HSTS 由部署层反向代理负责，不在本仓库内验证。

## 7. 回滚

验收 commit 为交付序列末提交 `dc06422`（容器与 CI 在 `004eebe..22ef184`）。整体回滚时按该序列逆序 revert；数据库迁移未修改。镜像与数据卷可通过上一版 tag 与备份恢复。
