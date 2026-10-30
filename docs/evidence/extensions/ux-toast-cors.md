# 开发端口 CORS 与顶部消息气泡修复（2026-10-30）

## 问题

1. 本机 3000 被其他应用占用，Next.js 自动改用 3001；后端 `dev` profile 只允许 `http://localhost:3000`，登录预检被拒，前端把网络失败显示为“网络连接失败，请检查后重试”。
2. 登录错误、上传校验、提交失败与查询异常使用内容区内联提示，出现时会把表单/网格向下推，占用页面空间。
3. 排查发现全局 `body > *` 规则把 Toast 的 `fixed` 覆盖成 `relative`，气泡实际排在页面底部；只改 `bottom` 为 `top` 不会生效。后续回归又发现：若提高选择器权重排除 `.fixed`，会连带覆盖 Dialog 与跳转链接的固定定位，导致结果弹层跑到页面底部、跳转链接常显。

## 修复

- 新增 `application-dev.yml`：`dev` profile 允许 `http://localhost:*`、`http://127.0.0.1:*` 作为 CORS origin patterns；`docker` 与生产不加载该 profile，仍只接受 `FRAMELAB_CORS_ALLOWED_ORIGINS` 的精确来源。未修改 `.env`。
- `CorsConfig` 支持 `FRAMELAB_CORS_ALLOWED_ORIGIN_PATTERNS`；保留 `allowCredentials=true`，不使用 `*` + credentials。
- 保持 `body > *` 原规则；Toast 使用独立的 `.toast-viewport` 类声明 `position: fixed`、`top: 76px`、`z-index: 80`，以类选择器压过元素选择器，不提高全局规则的权重。
- 结果弹层仍使用 `.dialog-overlay` / `.dialog-content` 的固定定位；跳转链接恢复 `fixed` 并在未聚焦时移出视口。
- 关闭 Next.js 开发指示器：`next.config.ts` 设置 `devIndicators: false`。
- `Toast` 移到顶部居中、顶栏下方 76px；宽 420px；success/info 4 秒、error 6 秒、warning 常驻；支持重试操作按钮与关闭。
- 登录失败、上传格式/大小校验、上传失败、提交失败、任务列表查询异常改为顶部气泡；查询异常气泡常驻并带“重试”，恢复后自动消失。
- 上传位仍保留珊瑚边框与“重试”覆盖层；字段级校验（邮箱/密码）与无数据时的整页/弹层错误态保留在原位，因为它们承载具体控件或替代缺失内容，无法由瞬时气泡替代。

## 验证

- `./mvnw -B verify`：104 项通过（新增 `CorsConfigTest` 2 项）。
- `pnpm lint && pnpm typecheck && pnpm test && pnpm build`：14 个测试文件、49 项通过。
- 预检：`OPTIONS /api/auth/login`，`Origin: http://localhost:3001` → `Access-Control-Allow-Origin: http://localhost:3001`、`Access-Control-Allow-Credentials: true`。
- 浏览器（Next 3001 + Java 8080 + MySQL 13306）：
  - 正常账号登录成功并跳回模板库。
  - 停止后端后登录：顶部气泡显示“网络连接失败，请检查后重试”，表单不下移。
  - 上传 `.txt` 到制作台：顶部气泡显示“第 1 张图片格式不支持，请选择 JPG / PNG。”，槽位保留错误边框。
  - Toast 视口计算样式为 `position: fixed`、`top: 76px`、`z-index: 80`。
  - 历史页点击“查看结果”：`.dialog-content` 计算样式为 `position: fixed`、`z-index: 51`，结果图加载为 1600×1200 blob；弹层可见。
  - 未聚焦时跳转链接矩形位于视口外（`bottom < 0`），按 Tab 聚焦后才出现。
  - 页面左下角不再显示 Next.js 开发工具按钮。

## 回滚

- 删除 `application-dev.yml` 与 `corsAllowedOriginPatterns` 即可恢复只允许 3000；Toast 变更不涉及数据库，可随前端提交回退。
