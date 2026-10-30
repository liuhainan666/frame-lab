-- V5__template_effect_seed.sql
-- 为现有图片模板回填 LEGACY 布局的调色 spec，并新增两个图片效果模板。
SET NAMES utf8mb4;

UPDATE template
   SET kind = 'IMAGE',
       output_kind = 'IMAGE',
       effect_tags = '["travel","warm"]',
       render_spec = '{"version":1,"kind":"IMAGE","look":{"preset":"travel-warm","lut":"luts/film-warm-v1.cube","lutStrength":0.55,"exposure":0.04,"contrast":1.04,"saturation":1.02,"temperature":4,"highlights":-6,"shadows":6,"vignette":0.1,"grain":0.02},"portrait":{"enabled":false,"skinSmooth":0,"skinBrighten":0,"warmth":0},"layout":{"engine":"legacy","gap":12},"overlay":null,"watermark":true}'
 WHERE id = 'travel-postcard';

UPDATE template
   SET kind = 'IMAGE',
       output_kind = 'IMAGE',
       effect_tags = '["film","warm"]',
       render_spec = '{"version":1,"kind":"IMAGE","look":{"preset":"film-warm","lut":"luts/film-warm-v1.cube","lutStrength":0.85,"exposure":0.06,"contrast":1.06,"saturation":0.96,"temperature":6,"tint":-2,"highlights":-10,"shadows":8,"vignette":0.16,"grain":0.05,"bloom":0.06},"portrait":{"enabled":false,"skinSmooth":0,"skinBrighten":0,"warmth":0},"layout":{"engine":"legacy","gap":12},"overlay":null,"watermark":true}'
 WHERE id = 'film-memory';

UPDATE template
   SET kind = 'IMAGE',
       output_kind = 'IMAGE',
       effect_tags = '["clear","cool"]',
       render_spec = '{"version":1,"kind":"IMAGE","look":{"preset":"coastal-clear","lut":"luts/clear-cool-v1.cube","lutStrength":0.6,"exposure":0.05,"contrast":1.03,"saturation":1.08,"temperature":-4,"shadows":8,"vignette":0.08},"portrait":{"enabled":false,"skinSmooth":0,"skinBrighten":0,"warmth":0},"layout":{"engine":"legacy","gap":12},"overlay":null,"watermark":true}'
 WHERE id = 'summer-coast';

UPDATE template
   SET kind = 'IMAGE',
       output_kind = 'IMAGE',
       effect_tags = '["festival","warm"]',
       render_spec = '{"version":1,"kind":"IMAGE","look":{"preset":"festive-warm","lut":"luts/film-warm-v1.cube","lutStrength":0.5,"exposure":0.03,"contrast":1.04,"saturation":1.0,"temperature":5,"vignette":0.12,"grain":0.03,"bloom":0.04},"portrait":{"enabled":false,"skinSmooth":0,"skinBrighten":0,"warmth":0},"layout":{"engine":"legacy","gap":12},"overlay":null,"watermark":true}'
 WHERE id = 'christmas-letter';

UPDATE template
   SET kind = 'IMAGE',
       output_kind = 'IMAGE',
       effect_tags = '["festival","warm"]',
       render_spec = '{"version":1,"kind":"IMAGE","look":{"preset":"festive-warm","lut":"luts/film-warm-v1.cube","lutStrength":0.55,"exposure":0.03,"contrast":1.04,"saturation":1.02,"temperature":5,"vignette":0.14,"grain":0.03,"bloom":0.05},"portrait":{"enabled":false,"skinSmooth":0,"skinBrighten":0,"warmth":0},"layout":{"engine":"legacy","gap":12},"overlay":null,"watermark":true}'
 WHERE id = 'new-year-card';

UPDATE template
   SET kind = 'IMAGE',
       output_kind = 'IMAGE',
       effect_tags = '["bright","warm"]',
       render_spec = '{"version":1,"kind":"IMAGE","look":{"preset":"pet-bright","lut":"luts/film-warm-v1.cube","lutStrength":0.35,"exposure":0.08,"contrast":1.02,"saturation":1.05,"vignette":0.06,"bloom":0.05},"portrait":{"enabled":false,"skinSmooth":0,"skinBrighten":0,"warmth":0},"layout":{"engine":"legacy","gap":12},"overlay":null,"watermark":true}'
 WHERE id = 'pet-portrait';

UPDATE template
   SET kind = 'IMAGE',
       output_kind = 'IMAGE',
       effect_tags = '["portrait","soft"]',
       render_spec = '{"version":1,"kind":"IMAGE","look":{"preset":"wedding-soft","lut":"luts/film-warm-v1.cube","lutStrength":0.4,"exposure":0.06,"contrast":1.02,"saturation":0.98,"temperature":4,"vignette":0.08,"bloom":0.04},"portrait":{"enabled":true,"skinSmooth":0.25,"skinBrighten":0.08,"warmth":0.04},"layout":{"engine":"legacy","gap":12},"overlay":null,"watermark":true}'
 WHERE id = 'wedding-invite';

UPDATE template
   SET kind = 'IMAGE',
       output_kind = 'IMAGE',
       effect_tags = '["portrait","clean"]',
       render_spec = '{"version":1,"kind":"IMAGE","look":{"preset":"portrait-clean","lut":"luts/clear-cool-v1.cube","lutStrength":0.25,"exposure":0.07,"contrast":1.03,"saturation":0.95,"clarity":0.06,"vignette":0.1},"portrait":{"enabled":true,"skinSmooth":0.3,"skinBrighten":0.1,"warmth":0.03},"layout":{"engine":"legacy","gap":12},"overlay":null,"watermark":true}'
 WHERE id = 'minimal-portrait';

UPDATE template
   SET kind = 'IMAGE',
       output_kind = 'IMAGE',
       effect_tags = '["warm","rich"]',
       render_spec = '{"version":1,"kind":"IMAGE","look":{"preset":"coffee-rich","lut":"luts/film-warm-v1.cube","lutStrength":0.7,"exposure":0.03,"contrast":1.08,"saturation":1.06,"temperature":5,"clarity":0.1,"vignette":0.14,"grain":0.03},"portrait":{"enabled":false,"skinSmooth":0,"skinBrighten":0,"warmth":0},"layout":{"engine":"legacy","gap":12},"overlay":null,"watermark":true}'
 WHERE id = 'coffee-launch';

UPDATE template
   SET kind = 'IMAGE',
       output_kind = 'IMAGE',
       effect_tags = '["cinematic","warm"]',
       render_spec = '{"version":1,"kind":"IMAGE","look":{"preset":"brand-cinematic","lut":"luts/city-warm-v1.cube","lutStrength":0.65,"exposure":0.02,"contrast":1.06,"saturation":0.98,"temperature":3,"vignette":0.16,"grain":0.04},"portrait":{"enabled":false,"skinSmooth":0,"skinBrighten":0,"warmth":0},"layout":{"engine":"legacy","gap":12},"overlay":null,"watermark":true}'
 WHERE id = 'brand-story';

INSERT INTO template
  (id, category_id, kind, title, description, cover_path, required_image_count,
   accepted_mime_types, max_file_size_bytes, render_spec, effect_tags, output_kind,
   canvas_width, canvas_height, sort_order, is_published)
VALUES
  ('golden-hour', 'travel', 'IMAGE', '黄金时刻',
   '两张照片，被落日染成温柔的琥珀色。', '/assets/templates/golden-hour.webp',
   2, 'image/jpeg,image/png', 10485760,
   '{"version":1,"kind":"IMAGE","look":{"preset":"golden-hour","lut":"luts/film-warm-v1.cube","lutStrength":0.7,"exposure":0.07,"contrast":1.05,"saturation":1.06,"temperature":8,"highlights":-8,"shadows":10,"vignette":0.14,"grain":0.04,"bloom":0.08},"portrait":{"enabled":false,"skinSmooth":0,"skinBrighten":0,"warmth":0},"layout":{"engine":"legacy","gap":12},"overlay":null,"watermark":true}',
   '["warm","golden"]', 'IMAGE', 1600, 1200, 25, 1),
  ('clear-portrait', 'portrait', 'IMAGE', '清透人像',
   '一张人像，轻磨皮与通透肤色。', '/assets/templates/clear-portrait.webp',
   1, 'image/jpeg,image/png', 10485760,
   '{"version":1,"kind":"IMAGE","look":{"preset":"clear-portrait","lut":"luts/clear-cool-v1.cube","lutStrength":0.35,"exposure":0.08,"contrast":1.02,"saturation":0.95,"temperature":-2,"clarity":0.08,"vignette":0.08},"portrait":{"enabled":true,"skinSmooth":0.3,"skinBrighten":0.1,"warmth":0.04},"layout":{"engine":"legacy","gap":12},"overlay":null,"watermark":true}',
   '["portrait","clear"]', 'IMAGE', 1200, 1600, 85, 1);

INSERT INTO template_translation (template_id, locale, title, description) VALUES
  ('golden-hour', 'en-US', 'Golden Hour',
   'Two photos bathed in soft amber light.'),
  ('clear-portrait', 'en-US', 'Clear Portrait',
   'One portrait with gentle smoothing and translucent skin.');
