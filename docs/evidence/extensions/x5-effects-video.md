# X5 · 模板特效与照片轮播视频 验收证据

日期：2026-10-30
范围：模板预设调色 + 轻人像；15 秒照片轮播视频；视频结果 Range/封面；前端类型/效果标签/播放。
设计：[13-effects-video-templates.md](../13-effects-video-templates.md)；验收映射：[08 §3.2](../../08-acceptance.md#32-模板特效与视频模板验收映射x5)。

## 1. 迁移与种子

```text
V4__template_effects_video.sql   模板 kind/render_spec/effect_tags/output_kind；任务快照与视频结果字段
V5__template_effect_seed.sql     回填 10 个图片模板 + 新增 golden-hour、clear-portrait
V6__video_template_seed.sql      新增 city-walk-15s（1080×1920，15s，5 槽位）
```

开发库 Flyway 已从 V3 升级到 V6；`/actuator/health` 为 `UP`。最终种子 13 个模板、19 条 en-US 翻译、13 张封面。

模板示例（真实 API 返回）：

```json
{
  "id": "city-walk-15s",
  "title": "城市漫游",
  "kind": "VIDEO",
  "effectTags": ["warm", "cinematic"],
  "requiredImageCount": 5,
  "canvas": { "width": 1080, "height": 1920 },
  "output": { "kind": "VIDEO", "durationMs": 15000, "width": 1080, "height": 1920, "hasAudio": true }
}
```

## 2. 图片特效链路

浏览器实测（demo 账号）：

- 模板“黄金时刻”：上传 2 张照片 → 提交 → 任务 `SUCCEEDED`（约 6 秒）。
- 结果弹层加载 1600×1200 JPEG（425.6 KB），暖调/琥珀色效果肉眼可辨。
- 模板卡与制作台显示：`图片` 徽标、`暖调`/`黄金时刻` 效果标签、素材数；无视频摘要行。

自动化：

- `EffectRenderingTest` 8 项：LUT 解析/采样、损坏 LUT、内置 LUT、曝光/饱和变化、肤色提亮与非肤色稳定、暗角/颗粒可重复、效果渲染 JPEG。
- `TemplateRenderSpecValidatorTest` 7 项：合法图片/视频 spec、未知字段、路径穿越、时长方程、缺文字键、kind 不匹配。

## 3. 视频任务与结果

浏览器实测（demo 账号）：

1. 打开“城市漫游”，上传 5 张照片，点击“生成视频”。
2. 任务依次 `RUNNING/MATERIAL_CHECK → GENERATE → FINALIZE`，约 32 秒后 `SUCCEEDED`。
3. 任务卡显示 `15 秒` 与 poster；结果弹层显示 `1080 × 1920 · 15 秒 · 2.8 MB`。
4. 视频元素 `readyState=4`、`duration=15`、`seekable=[0,15]`、`error=null`；页面隐藏时 Chromium 暂停播放，手动 seek 到 6s 成功，证明可解码、可拖动。

数据库实况：

```text
status: SUCCEEDED
result_storage_key: results/1/b1fa3b3e-ca5a-47b8-b3be-c93b305b75fd.mp4
result_mime_type: video/mp4
result_poster_key: results/1/b1fa3b3e-ca5a-47b8-b3be-c93b305b75fd.jpg
result_width/height: 1080 / 1920
result_duration_ms: 15000
result_size_bytes: 2937956
```

ffprobe：

```text
codec_name=h264
width=1080
height=1920
r_frame_rate=30/1
codec_name=aac
format_name=mov,mp4,m4a,3gp,3g2,mj2
duration=15.000000
size=2937956
```

抽帧（2s）确认：暖调 LUT、竖版构图、右下角 `FRAMELAB · b1fa3b3e` 水印；临时目录 `framelab-*` 已清理。

自动化：

- `VideoRenderServiceTest` 2 项（无 ffmpeg 时跳过）：MP4 + poster、LUT + 文字叠加。
- `VideoTaskApiTest` 2 项：视频任务成功 + 完整 200/`video/mp4` + `Range: bytes=0-99` 返回 206 + 100 字节 + poster `image/jpeg`；坏 spec 提交返回 500 `TEMPLATE_SPEC_INVALID`。

## 4. 发现并修复的问题

### 4.1 CSP 拦截 blob 视频

- 现象：结果弹层 `<video>` 提示“无法播放媒体”，控制台报 `Loading media from blob:... violates ... "default-src 'self'"`。
- 原因：`next.config.ts` 只有 `img-src blob:`，没有 `media-src`。
- 修复：加入 `media-src 'self' blob:`。
- 复验：`readyState=4`、`duration=15`、`error=null`、seek 正常。

### 4.2 视频标题缺字方框

- 现象：抽帧显示左上角标题为 4 个方框；水印英文正常。
- 原因：`MessageSource.get(Locale.ENGLISH, ...)` 在 `en_US` 资源包下会回退到默认中文包；容器/运行字体无 CJK 字形。
- 修复：改为 `Locale.US`，并补注释说明只用 ASCII。
- 复验：重编译并重启后新建视频任务 `3e237d84-2a65-45aa-a366-54e467758b01`，2s 抽帧标题为 `City Walk`，无方框；水印为 `FRAMELAB · 3e237d84`。

## 5. 测试与构建

| 项 | 结果 |
|---|---|
| 后端 `mvn test` | 125 项，0 失败 |
| 后端 `mvn verify` | 通过（最终门禁） |
| 前端 `pnpm test` | 54 项通过 |
| 前端 `pnpm lint` / `typecheck` / `build` | 通过（最终门禁） |
| 浏览器 | 模板库 13 项、图片结果、视频结果、历史页视频卡、中英文界面 |

## 6. 已知边界

- 视频上传/混剪、实时预览、用户换音乐/文字未实现；V1 只接受照片。
- 视频内文字固定英文 ASCII，不随界面语言变化。
- 本机 CPU 渲染 15s/1080×1920 约 32 秒；并发 1，超时 180 秒。
- 单实例假设：ffmpeg 并发信号量与本地存储不跨实例。
