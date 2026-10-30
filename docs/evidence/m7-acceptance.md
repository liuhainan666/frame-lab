# M7 联调与验收证据

- 日期：2026-10-30
- 基线：`a943474`（M6 完成）+ M7 工作区改动
- 环境：Windows 11 / JDK 21 / Node 24；后端 `localhost:8080`，前端 `localhost:3100`，MySQL 8.4（`framelab-mysql-1`，端口 13306）
- 结论：M7 范围内 Q-01～Q-04 通过；容器、CI 与最终 README 签署留待 M8。

## 1. Q-01 全链路

以 API 驱动执行，成功/失败/重试/续跑/幂等/隔离/国际化均可重复。共 37 项自动断言，36 项一次通过；未通过项为脚本把英文提示写死为 “not found”，实际返回 `The asset does not exist.`，单独复验通过，非产品缺陷。

| 场景 | 结果 | 证据 |
|---|---|---|
| 正常三步 | `QUEUED → MATERIAL_CHECK → GENERATE → FINALIZE → SUCCEEDED`；三步均 `SUCCEEDED`；结果 318,269 字节 JPEG，`Cache-Control: private` | 任务 `3d6e086d-aca2-4410-8828-b4319f9f85dd` |
| 永久失败 | `GENERATE` 失败后 `FINALIZE=SKIPPED`，全程无 `RETRYING`；注入消费后下一次正常成功 | 任务 `3588100d-b922-4f6e-9990-5c6187cb47b3` |
| 可重试两次 | 两次 `RETRYING`（约 2 s / 4 s），生成步骤 `FAILED, FAILED, SUCCEEDED`，`retryCount=2`，最终成功 | 任务 `848468c1-7a6d-4d4a-953f-026aa8039c87` |
| 重试耗尽 | 3 次尝试均失败，任务 `FAILED`，`FINALIZE=SKIPPED` | 任务 `8bd3fcef-7acd-4bf4-b73f-c9927902614a` |
| 受理幂等 | 同键同载荷第二次 `200 + Idempotent-Replay: true`；改素材顺序 `409 IDEMPOTENCY_KEY_REUSED`；并发双提交得到 `202 + 200` 且只新增 1 个任务 | 成功任务重放；并发任务 |
| 多任务 | 3 个 8 s 任务并发时采样 2 个 `RUNNING` + 1 个 `QUEUED`，最终 3 个独立结果全部成功 | 任务 `f5c76b74…`、`c8eaf6b2…`、`ecf5b4c8…` |
| 账号隔离 | demo2 读 demo 任务/结果/素材均 `404`；匿名读结果 `401`；demo2 列表不含 demo 任务；非管理员访问模拟接口 `403` | — |
| 国际化 | `Accept-Language: en-US` 返回英文模板标题、任务标题与失败原因，`Content-Language: en-US`；失败码 `GENERATION_FAILED` 不随语言变化 | 中文“旅行明信片”/英文 “Travel Postcard”；失败原因中英文 |
| 断点续跑 | 生成阶段强杀后端后重启：`resumeCount=1`，素材检查尝试仍为 1 次，生成尝试 `INTERRUPTED → SUCCEEDED`，`retryCount=0`，最终成功且未产生重复任务 | 任务 `a2700d01-17fc-4a7b-bfb8-c7b9ea252ea2` |

## 2. Q-02 生命周期与隔离

| 检查 | 结果 |
|---|---|
| 多任务互不串扰 | 3 个活跃任务分别渲染排队/处理中，最终各自成功 |
| 刷新恢复 | 处理中刷新后登录态、历史与未完成任务恢复并继续更新 |
| 查询异常 | 阻断任务请求 4.5 s 后出现“任务状态暂时无法更新，已保留最后一次结果”，卡片保持最后状态，未误判失败 |
| 恢复 | 恢复请求后提示消失，任务继续更新至成功 |
| 终态停轮询 | 全部终态后连续 6 s 内 `/api/tasks` 请求增量为 0 |
| 切账号 | demo2 历史 `0 个任务`，无 demo 缓存残留；切回 demo 后 `15 个任务` 仍在 |

## 3. Q-03 视觉与英文

四张参考图逐页比对，骨架、层级与状态语义一致；M7 修正了两处硬约束偏差：

1. 模板封面由抽象渐变替换为照片素材，恢复“照片占主导”的深色画廊。
2. 任务卡状态由左上角改为卡片内居中，处理中/等待重试/失败/成功四态一眼可分。

| 页面 | 证据 | 视口 |
|---|---|---|
| UI-01 模板库 | [1440](../assets/visual/m7-ui01-zh-1440.png) / [1280](../assets/visual/m7-ui01-zh-1280.png) / [768](../assets/visual/m7-ui01-zh-768.png) / [360](../assets/visual/m7-ui01-zh-360.png) / [EN 360](../assets/visual/m7-ui01-en-360.png) | 1440/1280/768/360 |
| UI-02 制作台 | [1440](../assets/visual/m7-ui02-zh-1440.png) / [1024](../assets/visual/m7-ui02-zh-1024.png) / [360](../assets/visual/m7-ui02-zh-360.png) / [EN 360](../assets/visual/m7-ui02-en-360.png) | 1440/1024/360 |
| UI-03 我的作品 | [处理中/失败/成功 1440](../assets/visual/m7-ui03-zh-1440.png) / [1280](../assets/visual/m7-ui03-zh-1280.png) / [等待重试 1280](../assets/visual/m7-ui03-retrying-zh-1280.png) / [EN 820](../assets/visual/m7-ui03-en-820.png) | 1440/1280/820 |
| 详情与结果 | [任务详情](../assets/visual/m7-task-detail-zh-1280.png) / [尝试历史](../assets/visual/m7-attempts-zh-1280.png) / [结果预览](../assets/visual/m7-result-zh-1280.png) | 1280 |
| UI-04 账号 | [中文 1280](../assets/visual/m7-ui04-zh-1280.png) / [English 820](../assets/visual/m7-ui04-en-820.png) | 1280/820 |

已登记的可调整差异与范围外元素：

- 参考图中的 credits、Premium、频道/工作流胶囊、账单、订阅、密码重置、条款与删除账号按非目标不实现；账号弹层保留分组卡片、身份、语言与退出。
- 任务卡在参考图的“居中状态”之外增加三步节点、失败阶段/原因、尝试次数与时间，用于满足 R-05～R-07；不提供删除/重做/下载。
- 精确像素、圆角与阴影以 `docs/06` 为准，不逐像素复刻；品牌替换为 FrameLab，模板封面使用 AI 生成的演示照片。
- 360 px 分类栏横向滚动，属参考图同类交互；页面无横向溢出。

## 4. Q-04 安全与性能

| 项 | 结果 |
|---|---|
| 归属 | 任务、素材、结果均按 `user_id` 查询；他人/不存在统一 `404` |
| 管理端 | `/api/admin/**` 仅 `ROLE_ADMIN`；普通用户 `403` |
| 登录限流 | 同一邮箱+IP 连续失败 5 次后 `429 RATE_LIMITED`，`Retry-After: 899` |
| 会话 | 刷新令牌 Cookie 为 `HttpOnly`、`SameSite=Lax`、限定 Path；JWT 仅内存保存 |
| 密钥 | 仓库仅含 `.env.example`；真实 `.env` 未入库；生产 profile 强制替换 JWT 密钥 |
| 日志 | 未发现密码、令牌、Cookie 或完整邮箱日志；错误响应不含堆栈与路径 |
| 注入 | MyBatis 全部使用 `#{}` 参数；前端无 `dangerouslySetInnerHTML` |
| 存储 | 素材/结果键为 `materials/{userId}/{assetId}`、`results/{userId}/{taskId}`，路径规范化并校验前缀；静态目录仅模板封面公开 |
| CSP | `next.config.ts` 已下发 CSP、`X-Content-Type-Options`、`X-Frame-Options`、`Referrer-Policy`、`Permissions-Policy`；登录与历史页在 CSP 下正常水合，无控制台报错 |
| N+1 | 列表 2 条查询（count + page，20/50 行上限）；详情 4 条固定查询（任务、步骤、尝试、素材），无逐行查询 |
| 轮询 | RUNNING 1.5 s、QUEUED/RETRYING 2.5 s、终态停止；详情关闭后停止查询 |

## 5. 质量门禁

| 门禁 | 结果 |
|---|---|
| `backend ./mvnw verify` | 97 个测试通过，BUILD SUCCESS |
| `pnpm typecheck` | 通过 |
| `pnpm lint` | 通过 |
| `pnpm test` | 13 个文件 / 45 个测试通过 |
| `pnpm build` | 通过；路由 `/`、`/history`、`/login`、`/templates/[templateId]` |

## 6. 遗留到 M8

- `docker compose up --build` 干净环境启动与空库迁移；
- GitHub Actions CI；
- README 最终签署与验收 commit；
- 生产反向代理的 HTTPS/HSTS 由部署层配置。
