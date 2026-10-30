# X1 · 安全与会话验收

- 日期：2026-10-30
- 计划：[12-extension-plan.md](../../12-extension-plan.md)
- 范围：刷新令牌轮换、重用检测、宽限期、访问令牌即时失效、退出全部、前端入口
- 结论：通过

## 1. 数据库

| 项 | 结果 |
|---|---|
| 迁移 | `V3__session_security.sql` 在已有 V2 库上执行成功；Flyway 版本 3 |
| 新列 | `app_user.token_version`；`refresh_token.family_id`、`family_expires_at`、`revoked_reason`、`replaced_by_id` |
| 索引/约束 | family、user+revoked、replaced_by 索引；家族过期与撤销原因 CHECK |
| 旧数据 | `family_id` 由 `UUID()` 回填，`family_expires_at` 按 `expires_at` 回填 |

## 2. 自动化测试

| 项 | 结果 |
|---|---|
| 后端 | `./mvnw -B verify`：102 项，0 失败，0 错误，BUILD SUCCESS |
| 轮换 | 旧令牌标记 `ROTATED`、同 family、`replaced_by_id` 指向新令牌；新 Cookie 不同 |
| 宽限 | 30s 内旧令牌可再次刷新，不撤销家族 |
| 重放 | 超宽限重用返回 401，家族活跃令牌全部撤销 |
| 即时失效 | 禁用用户旧 accessToken 下一次请求 401；logout-all 撤销全部家族并递增 `token_version` |
| 前端 | 14 个测试文件、47 项通过；`typecheck`、`lint`、`build` 通过 |

测试编号对应 [07 · BE-I-35 ～ 37](../../07-testing-ci-deploy.md#32-后端集成测试testcontainers-mysql) 与 `AuthApiTest`。

## 3. 浏览器实测

环境：Next.js `localhost:3001` + Spring Boot `localhost:8080` + MySQL `localhost:13306`；3000 被其他本地应用占用，后端 CORS 临时加入 3001（仅本地验证，未改默认值）。

| 步骤 | 结果 |
|---|---|
| 登录 `demo@framelab.local` | 200，进入模板库，账号菜单出现 |
| 打开账号弹层 | 显示“退出登录”和“退出所有设备” |
| 点击“退出所有设备” | `POST /api/auth/logout-all` 204；回到匿名首页，顶部显示“登录” |
| 会话表 | `app_user.token_version = 1`；该用户无 `revoked_at IS NULL` 的刷新令牌 |
| 再次登录后刷新 | `POST /api/auth/refresh` 200；同一 family 出现 `ROTATED` 行与 `replaced_by_id` 指向的新行 |

截图：[x1-account-menu-zh.png](../../assets/visual/x1-account-menu-zh.png)。

## 4. 回滚与限制

- `FRAMELAB_REFRESH_ROTATION_ENABLED=false` 可回退为不轮换；已轮换的旧令牌不会恢复，用户需重新登录。
- 30 秒宽限是多标签并发与重放检测的折中；超出宽限的旧令牌重用会撤销家族。
- 轮换失败路径在事务内完成家族撤销后返回无效结果，由 `AuthService` 在事务外抛 401，避免撤销随异常回滚。
- 普通退出只撤销当前家族，其他设备的访问令牌最多再存活 15 分钟；需要立即全部失效时使用“退出所有设备”。
- 登录限流仍在单进程内；会话列表/单条撤销、管理端禁用界面不在 X1 范围。
