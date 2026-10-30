-- V2__seed.sql
INSERT INTO app_user (id, email, display_name, password_hash, role, status) VALUES
  (1, 'demo@framelab.local',  '林小满',   '$2b$12$oDiRaSYrHZCgC1ttuA3YneJun5CYoQwnGS/PGB/falO5W9o/R3uQG', 'USER',  'ACTIVE'),
  (2, 'demo2@framelab.local', '周屿',     '$2b$12$y5pTs4.KwaiIPsxMxWZM6unJ1n9BCjIJuiIjzvKrk82svxYVJp9dK', 'USER',  'ACTIVE'),
  (3, 'admin@framelab.local', '运营管理员', '$2b$12$tw0gJenxXaEbmRMWWabXoOZyKtLl4BhfZaWQIG6o7VMUiS18kf8zO', 'ADMIN', 'ACTIVE');

INSERT INTO template_category (id, name, sort_order) VALUES
  ('travel',   '旅行风景', 10),
  ('creative', '创意图片', 20),
  ('festival', '节日营销', 30),
  ('portrait', '人像写真', 40),
  ('life',     '生活记录', 50),
  ('commerce', '电商海报', 60);

INSERT INTO template
  (id, category_id, title, description, cover_path, required_image_count,
   accepted_mime_types, max_file_size_bytes, canvas_width, canvas_height, sort_order, is_published)
VALUES
  ('travel-postcard', 'travel', '旅行明信片',
   '把两张旅途照片拼成一张可以寄出的明信片。', '/assets/templates/travel-postcard.webp',
   2, 'image/jpeg,image/png', 5242880, 1600, 1200, 10, 1),
  ('film-memory', 'creative', '胶片记忆',
   '一张照片，一段被时间染色的旅程。', '/assets/templates/film-memory.webp',
   1, 'image/jpeg,image/png', 5242880, 1200, 1600, 20, 1),
  ('summer-coast', 'travel', '夏日海岸',
   '三张海边照片，拼成一张夏日明信片。', '/assets/templates/summer-coast.webp',
   3, 'image/jpeg,image/png', 10485760, 1600, 1200, 30, 1),
  ('christmas-letter', 'festival', '圣诞来信',
   '两张照片与家人做一张节日贺卡。', '/assets/templates/christmas-letter.webp',
   2, 'image/jpeg,image/png', 5242880, 1200, 1600, 40, 1),
  ('new-year-card', 'festival', '新年福卡',
   '一张照片，做一张克制的新年问候卡。', '/assets/templates/new-year-card.webp',
   1, 'image/jpeg,image/png', 5242880, 1200, 1600, 50, 1),
  ('pet-portrait', 'life', '毛孩子写真',
   '三张照片，为宠物做一张杂志封面。', '/assets/templates/pet-portrait.webp',
   3, 'image/jpeg,image/png', 10485760, 1200, 1600, 60, 1),
  ('wedding-invite', 'portrait', '婚礼邀请',
   '两张照片组成一张简洁的婚礼邀请卡。', '/assets/templates/wedding-invite.webp',
   2, 'image/jpeg,image/png', 10485760, 1200, 1600, 70, 1),
  ('minimal-portrait', 'portrait', '极简人像',
   '一张人像，留白与细字排版。', '/assets/templates/minimal-portrait.webp',
   1, 'image/jpeg,image/png', 10485760, 1200, 1600, 80, 1),
  ('coffee-launch', 'commerce', '咖啡上新',
   '一张产品图，生成新品预告海报。', '/assets/templates/coffee-launch.webp',
   1, 'image/jpeg,image/png', 10485760, 1200, 1600, 90, 1),
  ('brand-story', 'commerce', '品牌主理人',
   '两张照片，讲述品牌主理人的故事。', '/assets/templates/brand-story.webp',
   2, 'image/jpeg,image/png', 10485760, 1200, 1600, 100, 1);

-- 英文翻译；基础列即 zh-CN，因此不重复插入 zh-CN 行
INSERT INTO template_category_translation (category_id, locale, name) VALUES
  ('travel',   'en-US', 'Travel & Landscape'),
  ('creative', 'en-US', 'Creative'),
  ('festival', 'en-US', 'Festival Marketing'),
  ('portrait', 'en-US', 'Portrait'),
  ('life',     'en-US', 'Life Moments'),
  ('commerce', 'en-US', 'Commerce Posters');

INSERT INTO template_translation (template_id, locale, title, description) VALUES
  ('travel-postcard', 'en-US', 'Travel Postcard',
   'Turn two travel photos into a postcard you can send.'),
  ('film-memory', 'en-US', 'Film Memory',
   'One photo, one journey tinted by time.'),
  ('summer-coast', 'en-US', 'Summer Coast',
   'Three seaside photos composed into a summer postcard.'),
  ('christmas-letter', 'en-US', 'Christmas Letter',
   'Two photos and a festive card for the family.'),
  ('new-year-card', 'en-US', 'New Year Card',
   'One photo, a restrained New Year greeting.'),
  ('pet-portrait', 'en-US', 'Pet Portrait',
   'Three photos and a magazine cover for your pet.'),
  ('wedding-invite', 'en-US', 'Wedding Invitation',
   'Two photos compose a clean wedding invitation.'),
  ('minimal-portrait', 'en-US', 'Minimal Portrait',
   'One portrait, white space and fine typography.'),
  ('coffee-launch', 'en-US', 'Coffee Launch',
   'One product shot becomes a new-release poster.'),
  ('brand-story', 'en-US', 'Brand Story',
   'Two photos that tell the story of the founder.');

INSERT INTO simulation_setting
  (id, material_check_delay_ms, generate_delay_ms, finalize_delay_ms,
   fail_stage, fail_times, fail_retryable, updated_by)
VALUES (1, 800, 3000, 1200, NULL, 0, 0, 3);
