# 13 · 模板特效与视频模板设计（提案）

> 状态：设计提案，未实现；需评审通过后先修订 [01](01-requirements.md) 的非目标，再进入开发。
> 目标读者：产品、后端、前端、评审。范围决策以本文 §2 为准，数据与接口细节按 §7、§8 实现。
> 本文不改变现有 P0 的验收结论；现有 10 个图片模板的行为必须保持兼容。

## 0. 一页结论

| 项 | 结论 |
|---|---|
| 本期范围 | 图片模板“预设调色 + 轻人像”；视频模板 V1“照片轮播视频” |
| 图片特效 | 模板级 LUT / 曝光 / 对比 / 色温 / 高光阴影 / 暗角 / 颗粒 / 柔光；轻人像只做磨皮、提亮、肤色统一，不做瘦脸大眼 |
| 视频 V1 | 3～5 张照片 → 15 秒 MP4，转场 + 模板调色 + 内置音乐 + 模板文字；1080×1920 或模板画布尺寸 |
| 视频 V2 | 照片 + 视频混剪、裁切、原声/静音、字幕；本期不做 |
| 明确不做 | 完整时间线编辑器、关键帧、多轨、AI 换背景/分割/形变、4K/HDR、用户上传音乐 |
| 首个垂直切片 | 2 个图片特效模板 + 1 个视频模板，端到端可演示 |
| 粗略工期 | 图片特效 6～9 人日；照片轮播视频 9～13 人日；含测试与文档 |
| 关键技术 | Java2D + LUT 做图片；ffmpeg filter graph 做视频；两者共用 `.cube` LUT |
| 数据策略 | 模板增加 `kind` 与版本化 `render_spec` JSON；任务创建时快照 spec；新迁移只增不删 |
| 回退策略 | 模板 `is_published=0`；`render_spec` 缺省回退现有拼版渲染；不修改 V1～V3 迁移 |

## 1. 现状与差距

### 1.1 当前实现

| 位置 | 现状 | 对本期的影响 |
|---|---|---|
| `template` 表 | 只有 `required_image_count`、`accepted_mime_types`、画布尺寸、封面 | 没有模板类型、效果参数、视频时长/转场/音乐 |
| `accepted_mime_types` CHECK | 只允许 `image/jpeg,image/png` | 视频素材需要新约束与新校验 |
| `LayoutPlanner` | 只支持 1～3 张图片的固定两列/三格布局 | 视频模板需要独立的时长/转场编排 |
| `ResultRenderer` | Java2D 裁剪填充 + 单图底部渐变 + 水印，输出 JPEG | 没有调色、LUT、人像处理、视频编码 |
| `task` 结果字段 | `result_storage_key`、宽高、大小 | 缺少结果类型、MIME、时长、封面 |
| 前端结果预览 | `<img>` + blob URL | 视频需要 `<video>`、Range、封面 |
| 文档 01 | 视频与真实 AI 在非目标中 | 本期属于范围扩展，批准后必须改 01 |

### 1.2 差距结论

- “拼接”到“特效”不是换一张模板封面，而是要在渲染前增加**逐图颜色管线**和**可选的人像处理**。
- “图片”到“视频”不是把上传类型改成 `video/mp4`，而是增加**模板编排 DSL、ffmpeg 渲染、结果 Range 播放、视频任务可靠性**四层。
- 两项都必须保留 E-01～E-04：幂等、重试、断点续跑、国际化不能因为新渲染器退化。

## 2. 目标与非目标

### 2.1 目标

1. **模板驱动效果**：同一张照片选择不同模板，结果有可感知的调色/风格差异，而不是只换拼版。
2. **自动应用**：用户不需要调参数；上传后由模板 `render_spec` 自动决定 LUT、曝光、对比、暗角、颗粒与人像强度。
3. **轻人像**：人像模板自动做肤色区域的磨皮、提亮与肤色统一，保留五官与皮肤纹理，不做几何形变。
4. **视频模板 V1**：模板库出现“视频”类型；用户上传 3～5 张照片，生成 15 秒带转场、音乐、模板文字的 MP4，可在结果弹层播放。
5. **兼容性**：现有 10 个图片模板的输入、状态机、历史任务、API 字段保持可用；旧任务结果仍可查看。
6. **可靠性不降级**：视频渲染同样支持幂等、阶段失败、自动重试、启动续跑与中英消息。

### 2.2 非目标

- 视频 V1 不接受视频上传；视频上传、裁切、原声/静音属于 V2。
- 不做完整时间线编辑器、多轨、关键帧、逐帧编辑、画中画、变速曲线。
- 不做 AI 换背景、人体/头发分割、瘦脸、大眼、牙齿美白、表情迁移。
- 不做 4K/HDR、GPU 编码、云端转码、CDN、对象存储。
- 不做用户自定义音乐、字体、贴纸、文字内容；V1 文字来自模板词典。
- 不做实时所见即所得预览；V1 以提交后的服务端结果为准。
- 不提供下载、删除、重做；沿用 [01](01-requirements.md) 的非目标。
- 不做“自动识别风景/人像并选择效果”的场景分类；V1 由模板决定效果。

## 3. 产品形态与用户流程

### 3.1 模板库

- 模板卡右上或标题区增加类型徽标：`图片` / `视频`。
- 增加效果标签：`胶片暖调`、`清透人像`、`青橙`、`黑白`、`柔光` 等；标签来自 `effect_tags`，前端用词典渲染中英文。
- 视频模板卡显示时长徽标（如 `15s`）与播放图标；封面仍使用静态图。
- 不新增“视频”分类；视频是模板类型，旅行、生活等分类仍可包含视频模板。

### 3.2 制作台

**图片特效模板**

- 左栏在模板描述下增加“模板效果”行：效果名 + 2～3 个标签，例如 `胶片暖调 · 暖色 · 低饱和`。
- 上传位、数量、类型、大小校验与现状一致。
- V1 不增加效果强度滑杆；同一模板的效果固定，保证结果可复现。
- 提交后结果图直接体现调色；结果弹层仍为图片查看。

**照片轮播视频模板**

- 模板要求显示为 `3～5 张图片`，不显示“视频上传”。
- 左栏显示输出摘要：`15 秒 · 1080×1920 · 含配乐`；音乐名来自模板翻译，不允许用户替换。
- 上传位数量按模板要求生成；提交按钮文案为 `生成视频` / `Create video`。
- 提交后任务卡显示视频类型、时长与播放图标；进行中阶段仍为三步，不伪造百分比。
- 结果弹层使用 `<video controls playsinline poster>`；不自动播放；无下载按钮。

### 3.3 结果与历史

- 任务结果统一增加 `kind`：`IMAGE` / `VIDEO`。
- 历史页与制作记录的卡片：视频显示播放图标 + `15s`；成功卡仍以封面/结果帧铺满。
- 任务详情：视频显示结果帧、时长、分辨率；素材缩略图与步骤时间线不变。
- 查询异常、重试、恢复的展示语义不变。

## 4. 模板模型与渲染 DSL

### 4.1 模板字段扩展

在现有 `template` 上以新增列/JSON 扩展，不删除旧列：

| 字段 | 类型 | 语义 | 约束 |
|---|---|---|---|
| `kind` | `VARCHAR(16)` | `IMAGE` / `VIDEO` | 默认 `IMAGE`；CHECK |
| `render_spec` | `JSON` | 版本化渲染参数 | 必填；后端校验；旧模板回填默认 spec |
| `effect_tags` | `JSON` | 稳定标签键数组 | 如 `["film","warm"]`；最多 6 个 |
| `output_kind` | `VARCHAR(16)` | 输出类型 | `IMAGE` / `VIDEO`；与 `kind` 一致 |

`render_spec` 采用 JSON 而不是拆成多张表，原因：

- 模板仍是种子数据，没有模板管理后台；JSON 易于种子、版本化与任务快照。
- 布局、调色、转场、音频是强内聚的一套参数，拆表会增加无收益的 join。
- 前端只需要摘要字段，不需要读取完整 spec；完整 spec 不下发。

任务创建时必须把 `render_spec`、`kind`、画布尺寸、音频/资源引用**快照**到任务；模板后续修改不影响历史任务。资源文件使用版本化键（如 `film-warm-v1.cube`），不得原地覆盖；模板升级需新增资源键，保证历史任务可复现。

### 4.2 图片模板 spec

```json
{
  "version": 1,
  "kind": "IMAGE",
  "look": {
    "preset": "film-warm",
    "lut": "luts/film-warm-33.cube",
    "lutStrength": 0.85,
    "exposure": 0.08,
    "contrast": 1.06,
    "saturation": 0.96,
    "temperature": 6,
    "tint": -2,
    "highlights": -10,
    "shadows": 8,
    "vignette": 0.16,
    "grain": 0.05,
    "bloom": 0.06
  },
  "portrait": {
    "enabled": false,
    "skinSmooth": 0.0,
    "skinBrighten": 0.0,
    "warmth": 0.0
  },
  "layout": {
    "engine": "legacy",
    "gap": 12
  },
  "overlay": null,
  "watermark": true
}
```

- `layout.engine=legacy` 表示继续使用 `LayoutPlanner`，保证现有模板零行为变化。
- `overlay` 为可选的 PNG 边框/光效资源键；V1 可以只保留字段，不启用。
- `portrait.enabled=true` 时使用轻人像管线；非人像模板必须为 `false`。

### 4.3 视频模板 spec

```json
{
  "version": 1,
  "kind": "VIDEO",
  "durationMs": 15000,
  "fps": 30,
  "resolution": { "width": 1080, "height": 1920 },
  "slots": [
    { "seq": 1, "durationMs": 3400, "transition": "fade", "transitionMs": 500, "motion": "zoom-in" },
    { "seq": 2, "durationMs": 3400, "transition": "slide-left", "transitionMs": 500, "motion": "pan-right" },
    { "seq": 3, "durationMs": 3400, "transition": "fade", "transitionMs": 500, "motion": "zoom-out" },
    { "seq": 4, "durationMs": 3400, "transition": "slide-left", "transitionMs": 500, "motion": "zoom-in" },
    { "seq": 5, "durationMs": 3400, "transition": "fade", "transitionMs": 500, "motion": "none" }
  ],
  "look": {
    "preset": "city-warm",
    "lut": "luts/city-warm-33.cube",
    "lutStrength": 0.8,
    "exposure": 0.04,
    "contrast": 1.04,
    "saturation": 1.02,
    "temperature": 4,
    "vignette": 0.12,
    "grain": 0.04
  },
  "audio": {
    "path": "audio/city-walk.mp3",
    "volume": 0.8,
    "fadeOutMs": 800
  },
  "text": [
    {
      "textKey": "video.cityWalk.title",
      "startMs": 500,
      "endMs": 4000,
      "x": 0.08,
      "y": 0.12,
      "fontSize": 56,
      "color": "#FFFFFF",
      "align": "left"
    }
  ],
  "watermark": true
}
```

时间规则：

- `outputDurationMs = Σslot.durationMs - Σtransition.transitionMs`。
- 相邻转场只允许 `fade`、`slide-left`、`slide-right`、`zoom`；转场时长 300～800ms，且小于相邻两个 slot 的时长。
- 视频 15 秒、30fps、3～5 个 slot；输出分辨率不得超过 1080×1920。
- `textKey` 必须存在于中英词典；V1 不接受用户输入的文字。
- `audio.path` 必须是模板资源白名单中的路径；V1 不接受用户上传音频。

### 4.4 spec 校验

后端新增 `TemplateRenderSpecValidator`，在种子导入/模板读取/提交时校验：

1. `version` 已支持；`kind` 与模板列一致。
2. 数值范围：LUT 强度 0～1、曝光 -2～2、对比 0.5～2、色温 -50～50、暗角/颗粒/柔光 0～1。
3. LUT/音频/覆盖层路径必须是 `templates/` 下的相对资源键，不允许 `..`、绝对路径、URL。
4. 视频 slot 数量 3～5、时长与转场合法；`durationMs` 必须等于 `Σslot.durationMs - Σtransition.transitionMs`；分辨率、fps 合法。
5. 文本 key 在词典中存在；颜色为 `#RRGGBB`。
6. spec 序列化后不超过 16KB；未知字段拒绝（避免拼写错误静默生效）。

## 5. 图片特效管线

### 5.1 顺序

```text
读取/规范化
  → 逐图裁剪到 slot
  → 逐图 look（LUT/白平衡/曝光/对比/饱和/高光阴影/清晰度）
  → 逐图 portrait（可选，肤色蒙版磨皮/提亮）
  → 布局合成（现有 LayoutPlanner 或 spec.layout）
  → 全局效果（暗角/颗粒/柔光/覆盖层）
  → 水印
  → JPEG 编码
```

先逐图调色再合成，避免每张图色彩不一致；暗角、颗粒、柔光属于整张作品，放在合成后处理。

### 5.2 颜色算法

| 效果 | 实现 | 说明 |
|---|---|---|
| LUT | 解析 `.cube` 33×33×33，三线性插值，按 `lutStrength` 与原图混合 | LUT 与 ffmpeg 共用，保证图片/视频观感一致 |
| 曝光 | sRGB 近似 `value * 2^exposure` | 范围 -2～2 |
| 对比 | 以 0.5 为轴心的线性拉伸 | 范围 0.5～2 |
| 白平衡 | 温度/色调通道增益，保持亮度 | 温度 -50～50 |
| 饱和度 | Rec.709 亮度权重，向灰度插值 | 0～2 |
| 高光/阴影 | 按亮度蒙版分别压暗/提亮 | 避免整体发灰 |
| 清晰度 | 大半径反锐化掩模，低强度 | 风景模板可开 |
| 暗角 | 径向距离蒙版，smoothstep | 合成后应用 |
| 颗粒 | 固定 seed（任务 ID）的高斯噪声 | 保证同任务可复现 |
| 柔光 | 亮度阈值 + 高斯模糊 + screen 混合 | 低强度，避免过曝 |

实现使用 `BufferedImage` 像素循环 + `ConvolveOp`/`RescaleOp`，不引入 OpenCV。单张 1600×1200 目标处理时间 < 800ms；结果编码前限制在工作分辨率。

### 5.3 轻人像

V1 不引入人脸关键点模型，使用**肤色蒙版 + 边缘保留平滑**：

1. 将图像转到 YCbCr；肤色范围近似 `77 ≤ Cb ≤ 127`、`133 ≤ Cr ≤ 173`，并限制亮度不过暗/过曝。
2. 对蒙版做开运算/闭运算去噪，再用半径 8～16px 的高斯羽化。
3. 在蒙版内做双边近似/引导滤波，混合强度 `skinSmooth`（0～0.5）。
4. 在蒙版内 lift 亮度、轻微降低红色偏差，混合强度 `skinBrighten`（0～0.2）。
5. 保留边缘与纹理：高频层按 0.6～0.8 回加，避免“塑料脸”。

限制：

- 不做瘦脸、大眼、牙齿、眼袋、发际线；不做背景分割。
- 暖色背景、木纹、食物可能被误判为肤色；人像模板默认强度较低。
- V1.1 可增加人脸检测（YuNet/ONNX）限定蒙版区域；接口保持 `PortraitProcessor` 不变。

### 5.4 兼容与回退

- `render_spec` 为空或 `layout.engine=legacy` 时走现有 `ResultRenderer` 路径。
- 旧任务没有 `render_spec_snapshot`，继续使用旧渲染。
- 新模板必须显式声明 spec；解析失败视为模板配置错误，任务在 MATERIAL_CHECK 阶段失败并给出稳定错误码，不产生半成品。

## 6. 视频模板管线

### 6.1 V1 范围

- 输入：3～5 张图片；输出：15 秒、30fps、H.264/AAC MP4，1080×1920（或模板画布比例）。
- 转场：fade / slide-left / slide-right / zoom。
- 运动：Ken Burns 缓慢推拉（zoom-in / zoom-out / pan）。
- 调色：复用 §5 的 LUT 与基础参数；视频用 ffmpeg `lut3d`/`eq`/`colorbalance`/`vignette`/`noise`。
- V1 不做逐帧轻人像；人像视频模板的磨皮/提亮后续再评估，避免逐帧处理的成本与误判。
- 音频：模板内置一首免版权音乐，淡出 800ms；不支持用户上传。
- 文字：模板固定文案，使用打包字体 `Inter-SemiBold.ttf`；中文字体需额外打包 OFL 字体。
- 水印：沿用 `FRAMELAB · {taskId 前 8 位}`，放在右下角。
- 封面：结果首帧或第 0.5 秒帧，JPEG。

### 6.2 渲染器选择

| 方案 | 结论 | 原因 |
|---|---|---|
| 逐帧 Java2D + ffmpeg 编码 | 不选 | 15 秒 30fps = 450 帧，内存/CPU 成本高，转场与音频仍需 ffmpeg |
| ffmpeg filter graph | **选用** | 转场、缩放、LUT、音频、文字都是一等能力；命令可测试、可审计 |
| 外部 Python/GPU 服务 | 不选 | 本期不需要，增加部署面 |

### 6.3 渲染流程

```text
GENERATE:
  1. 校验 spec、素材数量/类型/尺寸
  2. 在 storage/tmp/{taskId}-{attempt}/ 准备输入与输出
  3. 构建 ffmpeg 参数列表（ProcessBuilder，不经 shell）
  4. 执行 ffmpeg，超时/退出码/输出文件校验
  5. 输出临时 MP4 + 封面 JPG
FINALIZE:
  1. ffprobe 校验时长、分辨率、编码、音频流
  2. 原子移动到 storage/results/{taskId}.mp4 与 .jpg
  3. 写 task 结果元数据；清理临时目录
```

ffmpeg 参数要点：

```text
-loop 1 -t {slotSeconds} -i photo-1.jpg
...
-i audio.mp3
-filter_complex "
  [0:v]scale=...,crop=...,zoompan=...,setsar=1[v0];
  [1:v]... [v1];
  [v0][v1]xfade=transition=fade:duration=0.5:offset=2.7[x1];
  ...
  [xN]lut3d=file=...:interp=trilinear,eq=...,vignette=...,noise=... [vout];
  [vout][audio]...
"
-map "[vout]" -map {audio}
-c:v libx264 -preset veryfast -crf 20 -pix_fmt yuv420p
-movflags +faststart -c:a aac -b:a 128k -t 15 output.mp4
```

- `offset` 按 `Σ前序 slot 时长 - Σ已发生转场时长` 计算；公式写入 `VideoFilterBuilder` 单元测试。
- 所有路径使用绝对路径 + 参数列表；不得拼接 shell 字符串。
- 超时默认 180 秒；`max-concurrent=1`；输出上限 200MB；临时目录按任务隔离。
- 成功后再原子移动；失败不会留下可读取的半成品。

### 6.4 结果与播放

- 结果类型 `VIDEO`，MIME `video/mp4`；`TaskResultView` 增加 `durationMs`、`posterUrl`、`mimeType`。
- `GET /api/tasks/{id}/result` 支持 `Range`：`200` 全量、`206` 分段、`416` 越界；响应头 `Accept-Ranges: bytes`、`Content-Range`、`ETag`、`Cache-Control: private, max-age=...`。
- `GET /api/tasks/{id}/result/poster` 返回 JPEG 封面，鉴权与归属校验同结果。
- 前端 `<video controls playsInline preload="metadata" poster={posterUrl}>`；不自动播放；无下载按钮。
- CORS 暴露头增加 `Accept-Ranges`、`Content-Range`，否则跨端口前端无法正确播放分段。

### 6.5 失败码

| 错误码 | 阶段 | 可重试 | 触发 |
|---|---|---|---|
| `VIDEO_INPUT_INVALID` | MATERIAL_CHECK | 否 | 素材缺失、尺寸/格式不合法、spec 非法 |
| `VIDEO_RENDER_FAILED` | GENERATE | 是 | ffmpeg 非零退出、输出缺失、临时 I/O 错误 |
| `VIDEO_TIMEOUT` | GENERATE | 是 | 超过 180 秒 |
| `VIDEO_OUTPUT_INVALID` | FINALIZE | 是 | ffprobe 时长/编码/分辨率不符 |
| `VIDEO_UNAVAILABLE` | MATERIAL_CHECK | 否 | 运行环境缺少 ffmpeg 或视频功能关闭 |

模拟控制仍按阶段注入失败；视频任务的重试预算、`INTERRUPTED`、启动恢复与图片任务一致。

## 7. 数据模型与迁移

### 7.1 V4：模板与任务扩展（只增不删）

```sql
ALTER TABLE template
  ADD COLUMN kind VARCHAR(16) NOT NULL DEFAULT 'IMAGE' AFTER category_id,
  ADD COLUMN render_spec JSON NULL AFTER accepted_mime_types,
  ADD COLUMN effect_tags JSON NULL AFTER render_spec,
  ADD COLUMN output_kind VARCHAR(16) NOT NULL DEFAULT 'IMAGE' AFTER effect_tags,
  ADD CONSTRAINT ck_template_kind CHECK (kind IN ('IMAGE','VIDEO')),
  ADD CONSTRAINT ck_template_output_kind CHECK (output_kind IN ('IMAGE','VIDEO'));

ALTER TABLE task
  ADD COLUMN template_kind VARCHAR(16) NOT NULL DEFAULT 'IMAGE' AFTER template_id,
  ADD COLUMN render_spec_snapshot JSON NULL AFTER template_cover_path,
  ADD COLUMN output_kind VARCHAR(16) NOT NULL DEFAULT 'IMAGE' AFTER current_stage,
  ADD COLUMN result_mime_type VARCHAR(64) NULL AFTER result_storage_key,
  ADD COLUMN result_duration_ms INT UNSIGNED NULL AFTER result_height,
  ADD COLUMN result_poster_key VARCHAR(255) NULL AFTER result_storage_key,
  ADD CONSTRAINT ck_task_template_kind CHECK (template_kind IN ('IMAGE','VIDEO')),
  ADD CONSTRAINT ck_task_output_kind CHECK (output_kind IN ('IMAGE','VIDEO')),
  ADD CONSTRAINT ck_task_result_mime CHECK (
    result_mime_type IS NULL OR result_mime_type IN ('image/jpeg','video/mp4')
  ),
  ADD CONSTRAINT ck_task_result_duration CHECK (
    output_kind = 'VIDEO' OR result_duration_ms IS NULL
  );
```

- `render_spec` 允许 NULL 以兼容旧模板；读取时缺省走 legacy。
- 旧任务的 `render_spec_snapshot` 为 NULL，渲染器按旧逻辑处理。
- `result_duration_ms` 只对视频写入；图片保持 NULL。
- 迁移不修改 V1～V3；不删除任何列。

### 7.2 V5：种子与回填

- 为现有 10 个图片模板回填 `kind=IMAGE`、默认 `render_spec`、`effect_tags`：
  - `film-memory`：胶片暖调；
  - `minimal-portrait`、`wedding-invite`：轻人像；
  - 其余模板先使用低强度基础调色，保持视觉接近现状。
- 新增 3 个演示模板：
  - `golden-hour`（风景，暖调 LUT）；
  - `clear-portrait`（人像，轻磨皮/提亮）；
  - `city-walk-15s`（视频，3～5 张照片，15 秒）。
- 视频模板的封面先使用现有模板封面或自制封面；音乐使用自制/免版权资源，来源写入 README 与模板资源清单。
- 新增资源目录：
  - `backend/src/main/resources/templates/luts/`
  - `backend/src/main/resources/templates/audio/`
  - `backend/src/main/resources/templates/overlays/`
- 资源许可与来源记录在 `docs/assets/template-resources.md`，不依赖外部 CDN。

### 7.3 存储

- 模板资源随 jar 打包，只读；任务不修改模板资源。
- 结果仍走现有 `LocalObjectStorage`：
  - 图片：`results/{taskId}.jpg`
  - 视频：`results/{taskId}.mp4`
  - 封面：`results/{taskId}.jpg`
- 临时目录：`storage/tmp/{taskId}-{attempt}/`；任务成功/失败/启动恢复时清理。
- 视频结果不公开静态目录；只通过鉴权结果接口读取。

## 8. API 契约

### 8.1 模板列表/详情（新增字段，保持旧字段）

```json
{
  "id": "city-walk-15s",
  "title": "城市漫游",
  "kind": "VIDEO",
  "effectTags": ["warm", "cinematic"],
  "requiredImageCount": 4,
  "acceptedMimeTypes": ["image/jpeg", "image/png"],
  "canvas": { "width": 1080, "height": 1920 },
  "output": {
    "kind": "VIDEO",
    "durationMs": 15000,
    "width": 1080,
    "height": 1920,
    "hasAudio": true
  }
}
```

- 图片模板 `output.kind=IMAGE`，`durationMs` 为 null。
- 不返回完整 `render_spec`；返回 `effectSummary`（效果名 key、标签）即可。
- 旧客户端只读旧字段仍可用。

### 8.2 任务结果

```json
{
  "result": {
    "available": true,
    "kind": "VIDEO",
    "mimeType": "video/mp4",
    "url": "/api/tasks/{id}/result",
    "posterUrl": "/api/tasks/{id}/result/poster",
    "width": 1080,
    "height": 1920,
    "durationMs": 15000,
    "sizeBytes": 8123456
  }
}
```

- 图片结果：`kind=IMAGE`、`mimeType=image/jpeg`、`posterUrl=null`、`durationMs=null`。
- 旧字段 `url`、`width`、`height`、`sizeBytes` 保留。

### 8.3 结果接口

| 方法/路径 | 行为 | 关键头 |
|---|---|---|
| `GET /api/tasks/{id}/result` | 按结果类型返回 JPEG/MP4；支持 Range | `Content-Type`、`Accept-Ranges`、`Content-Range`、`ETag`、`Cache-Control: private` |
| `GET /api/tasks/{id}/result/poster` | 仅视频封面 JPEG；图片请求返回 404 | `Content-Type: image/jpeg`、`ETag` |
| `GET /api/templates` | 列表增加 `kind`、`output`、`effectTags` | `Vary: Accept-Language` |
| `GET /api/templates/{id}` | 详情增加 `effectSummary`、`output` | 同上 |

- `Range` 支持单区间；多区间可降级为 200。
- 未登录 401；他人任务 404；结果不存在 404。
- 视频提交时若 ffmpeg 不可用，返回 `VIDEO_UNAVAILABLE`（409 或 503，按现有错误模型选择 503 更合适）。

### 8.4 错误码新增

- `UNSUPPORTED_MEDIA_KIND`：视频模板收到视频素材（V1）。
- `TEMPLATE_SPEC_INVALID`：模板配置错误（服务端记录，不暴露内部细节）。
- `VIDEO_UNAVAILABLE`、`VIDEO_INPUT_INVALID`、`VIDEO_RENDER_FAILED`、`VIDEO_TIMEOUT`、`VIDEO_OUTPUT_INVALID`。
- 中英文消息加入后端 `i18n/messages` 与前端词典；错误码稳定，不把 ffmpeg 日志返回给用户。

## 9. 前端设计

### 9.1 模板库

- `TemplateCard` 增加类型徽标与效果标签；视频卡加播放图标与 `15s`。
- 卡片封面仍优先；标签不抢标题。
- 列表/详情新增字段的类型定义、MSW 夹具与测试。
- 英文标签长度检查，360px 下不溢出。

### 9.2 制作台

- 图片模板：增加“模板效果”摘要；不增加参数控件。
- 视频模板：输出摘要、音乐名、时长；上传位与图片模板共用。
- 提交按钮：视频模板 `生成视频` / `Create video`；受理后清空草稿，toast 仍为“任务已创建，正在排队”。
- 模板要求中的“张图片”对视频模板保持“张图片”，不写“视频”。

### 9.3 结果

- `ResultDialog` 根据 `result.kind` 分支：
  - 图片：现有 `<img>` + blob；
  - 视频：`<video controls playsInline preload="metadata" poster>`，`src` 使用 fetch blob 或带鉴权的媒体 URL。
- 由于 `<video src>` 不能带 Authorization，采用与图片相同的 `fetchBlob` + `URL.createObjectURL`；这样 Range 不生效，但 15 秒短视频可接受。若后续要流式播放，再加短期签名 URL 或 Cookie 鉴权。
- 结果卡/详情显示播放图标、时长、分辨率；不自动播放，不提供下载。
- 视频加载失败显示“视频暂时无法播放” + 重试；不把查询失败画成任务失败。

### 9.4 国际化与无障碍

- 新增键：模板类型、效果标签、视频输出摘要、生成视频、视频加载失败、时长格式。
- `<video>` 提供 `aria-label`；封面图有 alt；控件键盘可达；无自动播放。
- 状态颜色、错误提示、焦点环沿用现有规范。

## 10. 可靠性与安全

### 10.1 幂等、重试、续跑

- 幂等键逻辑不变：同一用户同一 `clientRequestId` 返回原任务；视频任务同样适用。
- 视频渲染失败按 `VIDEO_RENDER_FAILED` / `VIDEO_TIMEOUT` 进入 `RETRYING`，退避与次数沿用现有策略；`VIDEO_INPUT_INVALID` 永久失败。
- 启动恢复：`RUNNING → QUEUED`；中断 attempt 标记 `INTERRUPTED`；临时目录清理；已成功步骤不重跑。
- FINALIZE 结果原子写：临时 MP4 + 封面校验通过后移动到最终路径，再写数据库；避免“有结果行但文件不完整”。

### 10.2 进程与资源

- `VideoRenderService` 使用 `ProcessBuilder` 参数列表；不经过 shell；禁止用户控制 filter 字符串。
- ffmpeg 路径由 `FRAMELAB_FFMPEG_PATH` 配置，默认 `ffmpeg`；启动时校验版本；`docker` profile 缺失时启动失败并记录。
- 并发：`FRAMELAB_VIDEO_MAX_CONCURRENT=1`；worker 线程默认 2，但视频渲染用信号量限制。
- 超时：180 秒；超时后 `destroyForcibly` 并清理临时目录。
- 输出上限：200MB；磁盘剩余空间低于阈值时拒绝新视频任务（可选，V1 记录告警）。
- ffmpeg 日志只记录退出码、耗时、输出大小；不把完整命令中的用户路径写入用户可见错误。

### 10.3 安全

- LUT/音频/覆盖层只允许 classpath 白名单资源键；拒绝 `..`、绝对路径、URL。
- 图片仍做魔数、解码、像素总数、尺寸校验；视频 V1 不接收视频输入。
- 结果读取必须带 `user_id` 归属；Range 请求同样鉴权。
- 视频结果设置 `X-Content-Type-Options: nosniff`；`Content-Disposition: inline`。
- 音频/字体/LUT 许可记录在资源清单，不使用来源不明的素材。

## 11. 测试与验收

### 11.1 后端单元

- `Lut3dParserTest`：解析、边界、非法文件、identity LUT。
- `ColorPipelineTest`：曝光/对比/饱和/白平衡的已知像素；无 NaN/越界。
- `PortraitMaskTest`：肤色蒙版、羽化、无脸图片回退。
- `TemplateRenderSpecValidatorTest`：范围、路径、视频时长/转场、未知字段。
- `VideoFilterBuilderTest`：xfade offset、总时长、转场数量、参数转义。
- `VideoProbeTest`：通过 ffprobe 校验输出（需要 ffmpeg 的测试可标记集成）。

### 11.2 后端集成

- `BE-I-39` 图片特效：固定输入 + 固定 spec，输出与 golden 图的 SSIM ≥ 0.98；无 LUT 时与旧结果一致。
- `BE-I-40` 轻人像：人像模板输出皮肤区域亮度/噪声变化在阈值内，非肤色区域变化受限。
- `BE-I-41` 视频成功：3～5 张照片生成 MP4；ffprobe 时长 15s、H.264、AAC、1080×1920；封面存在。
- `BE-I-42` 视频失败/重试：模拟 GENERATE 失败 → RETRYING → 成功；尝试历史完整。
- `BE-I-43` 视频续跑：GENERATE 中断后重启，任务回到 QUEUED，临时文件清理，不重复 FINALIZE。
- `BE-I-44` 结果 Range：`Range: bytes=0-1023` 返回 206 与正确 `Content-Range`；越界 416；他人 404。
- `BE-I-45` 幂等：视频任务同键同载荷重放返回原任务；异载荷 409。
- `BE-I-46` 兼容：旧图片模板/旧任务结果不变。

### 11.3 前端

- `FE-U-31` 模板卡类型/效果标签/视频时长。
- `FE-U-32` 视频模板上传 4 张照片并提交，按钮文案与输出摘要正确。
- `FE-U-33` 视频结果弹层加载 poster、显示 `<video>`、不自动播放。
- `FE-U-34` 视频加载失败提示与重试。
- `FE-U-35` 中英文标签不溢出；360px 可操作。
- 现有 49 项测试全部保持通过。

### 11.4 性能与视觉

- 图片：1600×1200、单图调色 + 合成 < 2 秒；内存峰值 < 512MB。
- 视频：15 秒 1080×1920、5 张照片，演示机 CPU 渲染 < 90 秒；并发 1。
- 视觉证据：每个效果模板一张输入/输出对比；视频一张首帧、一张播放中截图。
- 人工验收：效果差异肉眼可辨；人像不塑料、不偏色；视频转场/音乐/文字正常。

### 11.5 演示脚本（建议）

1. 打开模板库，筛选“胶片暖调”模板，上传照片，提交，结果明显暖调/低饱和。
2. 打开“清透人像”，上传人像，结果皮肤更均匀但保留纹理。
3. 打开 15 秒视频模板，上传 4 张照片，提交，任务卡显示视频时长；完成后播放。
4. 模拟 GENERATE 失败，确认 `RETRYING` 与自动重试；模拟视频超时，确认失败原因与尝试历史。
5. 重启服务，确认中断的视频任务恢复；结果可继续播放。
6. 英文界面检查标签、按钮与错误消息。

## 12. 分阶段实施计划

### 12.1 阶段 E：图片特效（6～9 人日）

| 编号 | 任务 | 产出 | 完成判据 |
|---|---|---|---|
| E-01 | V4 模板/任务扩展与 spec 校验 | 迁移、`TemplateRenderSpecValidator` | 空库/存量库迁移；旧模板回退 legacy |
| E-02 | LUT 与颜色管线 | `Lut3dParser`、`ColorPipeline` | 单测 + golden 图通过 |
| E-03 | 轻人像 | `PortraitProcessor`、肤色蒙版 | 人像/非人像样例通过 |
| E-04 | 渲染器接入与结果快照 | `ImageRenderService`、任务快照 | 旧模板结果不变；新模板生效 |
| E-05 | 种子模板与 API 字段 | V5、DTO、前端类型/MSW | 模板库可见效果标签 |
| E-06 | 前端效果摘要与测试 | 制作台/卡片/详情 | 49 项 + 新用例通过 |
| E-07 | 文档与证据 | 02/03/04/05/06/07/08 | 评审通过 |

### 12.2 阶段 V：照片轮播视频（9～13 人日）

| 编号 | 任务 | 产出 | 完成判据 |
|---|---|---|---|
| V-01 | ffmpeg 运行环境 | Docker 安装/版本固定/启动校验 | `ffmpeg -version` 可用；缺失时明确失败 |
| V-02 | 视频 spec 与种子 | `VideoSpecValidator`、1 个视频模板 | spec 校验与种子通过 |
| V-03 | `VideoRenderService` | filter builder、ProcessBuilder、超时/清理 | 生成 15 秒 MP4 + 封面 |
| V-04 | 结果 API 与 Range | DTO、`ResourceRegion`、poster 接口 | 206/416/ETag/归属通过 |
| V-05 | 前端视频结果 | 类型、结果弹层、卡片徽标 | 播放、错误、i18n 通过 |
| V-06 | 可靠性与模拟 | 失败码、重试、续跑、临时清理 | BE-I-42/43 通过 |
| V-07 | 测试、文档与演示 | 07/08/README/证据 | 演示脚本可重复 |

### 12.3 阶段 V2：视频上传/混剪（未排期）

- 视频上传、ffprobe 校验、代理转码、裁切、原声/静音、字幕。
- 需要单独设计素材模型、上传限制、预览与存储；不在本期承诺。

### 12.4 提交拆分（按 09）

1. `feat(db): 新增模板类型与渲染参数快照`
2. `feat(api/template): 支持模板效果与视频输出描述`
3. `feat(api/task): 接入图片调色与人像管线`
4. `feat(api/task): 实现照片轮播视频渲染`
5. `feat(api/task): 支持视频结果 Range 与封面`
6. `feat(web/templates): 展示模板类型与效果标签`
7. `feat(web/tasks): 支持视频结果播放`
8. `test(...)` 与 `docs(...)` 分层提交

## 13. 决策与待确认

| 编号 | 决策 | 默认选择 | 影响 |
|---|---|---|---|
| D-13-01 | 渲染参数存储 | JSON `render_spec` + 任务快照 | 易演进；不做模板后台时足够 |
| D-13-02 | 轻人像实现 | 肤色蒙版 + 边缘保留平滑，无人脸模型 | 无原生依赖；不做瘦脸大眼；暖色背景可能误判 |
| D-13-03 | 视频渲染 | ffmpeg filter graph | 需要打包 ffmpeg；命令需测试与限权 |
| D-13-04 | 视频上传 | V2 再做 | V1 只接受照片，降低范围 |
| D-13-05 | 视频规格 | 15s / 30fps / 1080×1920 / 3～5 张 | 输出可控；后续可加时长档位 |
| D-13-06 | 音乐与文字 | 模板内置，用户不可改 | 版权与输入面可控 |
| D-13-07 | 实时预览 | V1 不做 | 降低前端/服务端成本；以结果为准 |
| D-13-08 | 结果播放 | fetch blob + object URL | 15s 短视频可接受；后续可改签名 URL/Range 流式 |
| D-13-09 | 视频不可用 | 503 + `VIDEO_UNAVAILABLE` | 缺 ffmpeg 时不影响图片模板 |

需要产品确认的默认值：

- 首批 3 个模板的具体风格：胶片暖调、清透人像、城市漫游 15s。
- 视频比例：默认 1080×1920 竖版；如要横版/方形，需增加模板级 `resolution` 档位。
- 音乐：使用自制/免版权曲目，来源与许可写入 README。
- 轻人像强度：默认磨皮 0.35、提亮 0.12，不做用户滑杆。

## 14. 批准后需要修改的文档

| 文档 | 修改点 |
|---|---|
| [01](01-requirements.md) | 从非目标移除“视频处理”或以“V1 照片轮播视频”限定；新增 R-15 模板特效、R-16 视频模板 |
| [02](02-domain-model.md) | 模板 `kind`/`render_spec`、结果 `kind`、视频失败码与快照规则 |
| [03](03-database.md) | V4/V5、约束、索引、种子资源 |
| [04](04-api.md) | 模板 `output`/`effectTags`、结果 `kind`/`durationMs`/`posterUrl`、Range、错误码 |
| [05](05-architecture.md) | 渲染器分层、ffmpeg 进程管理、并发/超时/清理 |
| [06](06-frontend-design.md) | 模板徽标/效果标签、视频输出摘要、视频播放器 |
| [07](07-testing-ci-deploy.md) | ffmpeg 依赖、视频测试矩阵、golden 图、Range 用例 |
| [08](08-acceptance.md) | 新验收编号、演示脚本、已知限制 |
| [09](09-git-commit.md) | 新增 scope（如 `api/render`、`web/templates` 已存在） |
| [10](10-reliability-i18n.md) | 视频重试/续跑/临时清理、视频错误消息 |
| [11](11-development-plan.md) | 新里程碑 E/V、依赖与门禁 |
| [12](12-extension-plan.md) | 将本期挂到扩展计划并标注设计状态 |
| README | ffmpeg 依赖、视频模板说明、资源许可、已知限制 |

批准顺序建议：评审本文 → 修订 01/02/03/04 → 建 V4/V5 迁移 → 图片特效切片 → 视频切片 → 验收。
