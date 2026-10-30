-- V4__template_effects_video.sql
-- 模板效果与视频模板：只增不删，旧数据保持 IMAGE/legacy 语义。
SET NAMES utf8mb4;

ALTER TABLE template
  ADD COLUMN kind VARCHAR(16) NOT NULL DEFAULT 'IMAGE' AFTER category_id,
  ADD COLUMN render_spec JSON NULL AFTER accepted_mime_types,
  ADD COLUMN effect_tags JSON NULL AFTER render_spec,
  ADD COLUMN output_kind VARCHAR(16) NOT NULL DEFAULT 'IMAGE' AFTER effect_tags,
  ADD KEY idx_template_kind_published (kind, is_published),
  ADD CONSTRAINT ck_template_kind CHECK (kind IN ('IMAGE','VIDEO')),
  ADD CONSTRAINT ck_template_output_kind CHECK (output_kind IN ('IMAGE','VIDEO'));

ALTER TABLE task
  ADD COLUMN template_kind VARCHAR(16) NOT NULL DEFAULT 'IMAGE' AFTER template_id,
  ADD COLUMN render_spec_snapshot JSON NULL AFTER template_cover_path,
  ADD COLUMN output_kind VARCHAR(16) NOT NULL DEFAULT 'IMAGE' AFTER current_stage,
  ADD COLUMN result_mime_type VARCHAR(64) NULL AFTER result_storage_key,
  ADD COLUMN result_poster_key VARCHAR(255) NULL AFTER result_mime_type,
  ADD COLUMN result_duration_ms INT UNSIGNED NULL AFTER result_height,
  ADD UNIQUE KEY uq_task_poster_key (result_poster_key),
  ADD CONSTRAINT ck_task_template_kind CHECK (template_kind IN ('IMAGE','VIDEO')),
  ADD CONSTRAINT ck_task_output_kind CHECK (output_kind IN ('IMAGE','VIDEO')),
  ADD CONSTRAINT ck_task_result_mime CHECK (
    result_mime_type IS NULL OR result_mime_type IN ('image/jpeg','video/mp4')
  ),
  ADD CONSTRAINT ck_task_result_duration CHECK (
    output_kind = 'VIDEO' OR result_duration_ms IS NULL
  ),
  ADD CONSTRAINT ck_task_result_meta_success CHECK (
    status = 'SUCCEEDED' OR (
      result_mime_type IS NULL AND result_duration_ms IS NULL AND result_poster_key IS NULL
    )
  );
