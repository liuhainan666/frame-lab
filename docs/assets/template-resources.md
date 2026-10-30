# 模板渲染资源清单与许可

本文件记录 X5 模板特效与照片轮播视频使用的二进制资源。所有资源随仓库分发，仅用于本项目的演示与评审。

## 1. LUT（3D `.cube`）

| 文件 | 用途 | 规格 | 来源 |
|---|---|---|---|
| `backend/src/main/resources/templates/luts/film-warm-v1.cube` | 胶片记忆、旅行明信片、圣诞来信等暖调模板 | 17³，`.cube`，约 130 KB | 本项目自制 |
| `backend/src/main/resources/templates/luts/city-warm-v1.cube` | 黄金时刻、城市漫游、咖啡上新 | 17³，`.cube`，约 130 KB | 本项目自制 |
| `backend/src/main/resources/templates/luts/clear-cool-v1.cube` | 夏日海岸、清透人像、极简人像 | 17³，`.cube`，约 130 KB | 本项目自制 |

- 生成方式：按目标色温/对比/饱和曲线对 RGB 立方体做程序化映射，导出 ReShade 兼容 `.cube`。
- 许可：随项目源码以同一许可分发；不含第三方 LUT 或品牌色彩科学文件。
- 校验：`CubeLut` 要求 `LUT_3D_SIZE` 为 17、通道值在 [0,1]；文件缺失或格式错误时模板 spec 校验失败，不进入渲染。

## 2. 视频背景音乐

| 文件 | 用途 | 规格 | 来源 |
|---|---|---|---|
| `backend/src/main/resources/templates/audio/city-walk-v1.mp3` | 城市漫游 15 秒模板 | MP3，约 15.5 秒，128 kbps | 本项目合成 |

- 由简单的和弦/节奏序列程序化合成，不含采样、人声或第三方曲目。
- 视频模板通过 `audio.fadeOutMs` 做尾部淡出；音量由 spec 固定，用户不可更换。
- 许可：随项目源码以同一许可分发；如后续替换为免版权曲目，必须记录曲名、作者、来源链接与许可证。

## 3. 模板封面

| 文件 | 规格 | 来源 |
|---|---|---|
| `travel-postcard` / `film-memory` / `summer-coast` / `christmas-letter` / `new-year-card` / `pet-portrait` / `wedding-invite` / `minimal-portrait` / `coffee-launch` / `brand-story` 的 `.webp` | 1200×1600，WebP q82 | `backend/tools/generate_covers.py` 程序化生成 |
| `golden-hour.webp` | 1200×1600，WebP | 本项目 AI 生成（Qoder ImageGen），无第三方版权素材 |
| `clear-portrait.webp` | 1200×1600，WebP | 本项目 AI 生成（Qoder ImageGen） |
| `city-walk-15s.webp` | 1080×1920，WebP | 本项目 AI 生成（Qoder ImageGen） |

- 均不含可识别人脸、公司内部素材、品牌 logo 或付费图库内容。
- 如后续替换为真实照片，必须逐张补充作者、来源链接、许可证，并确认允许演示与再分发。

## 4. 字体与图标

| 资源 | 许可 | 位置 |
|---|---|---|
| Inter / Inter Variable | SIL Open Font License 1.1 | `frontend/src/assets/fonts`、后端渲染资源 |
| JetBrains Mono | SIL Open Font License 1.1 | `frontend/src/assets/fonts` |
| lucide-react 图标 | ISC | 前端依赖 |
| ffmpeg / ffprobe | LGPL/GPL（取决于构建） | 运行时依赖，不随源码分发 |

## 5. 再分发检查

- 不提交任何用户上传照片、`storage/` 内容或真实个人信息。
- 新增资源必须在本文件登记；未登记的资源不得进入模板 spec。
- 模板 spec 的资源键只允许 `luts/`、`audio/`、`overlays/` 前缀，禁止 URL、绝对路径与 `..`。
