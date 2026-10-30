-- V6__video_template_seed.sql
-- 照片轮播视频模板：5 张照片、15 秒、30fps、1080×1920。
SET NAMES utf8mb4;

INSERT INTO template
  (id, category_id, kind, title, description, cover_path, required_image_count,
   accepted_mime_types, max_file_size_bytes, render_spec, effect_tags, output_kind,
   canvas_width, canvas_height, sort_order, is_published)
VALUES
  ('city-walk-15s', 'travel', 'VIDEO', '城市漫游',
   '五张照片，十五秒的城市慢镜头。', '/assets/templates/city-walk-15s.webp',
   5, 'image/jpeg,image/png', 10485760,
   '{"version":1,"kind":"VIDEO","durationMs":15000,"fps":30,"resolution":{"width":1080,"height":1920},"slots":[{"seq":1,"durationMs":3400,"transition":"fade","transitionMs":500,"motion":"zoom-in"},{"seq":2,"durationMs":3400,"transition":"slide-left","transitionMs":500,"motion":"pan-right"},{"seq":3,"durationMs":3400,"transition":"fade","transitionMs":500,"motion":"zoom-out"},{"seq":4,"durationMs":3400,"transition":"slide-left","transitionMs":500,"motion":"zoom-in"},{"seq":5,"durationMs":3400,"transition":"fade","transitionMs":500,"motion":"none"}],"look":{"preset":"city-warm","lut":"luts/city-warm-v1.cube","lutStrength":0.8,"exposure":0.04,"contrast":1.04,"saturation":1.02,"temperature":4,"vignette":0.12,"grain":0.04},"audio":{"path":"audio/city-walk-v1.mp3","volume":0.8,"fadeOutMs":800},"text":[{"textKey":"video.cityWalk.title","startMs":500,"endMs":4000,"x":0.08,"y":0.12,"fontSize":56,"color":"#FFFFFF","align":"left"}],"watermark":true}',
   '["warm","cinematic"]', 'VIDEO', 1080, 1920, 110, 1);

INSERT INTO template_translation (template_id, locale, title, description) VALUES
  ('city-walk-15s', 'en-US', 'City Walk',
   'Five photos, fifteen seconds of a slow city portrait.');
