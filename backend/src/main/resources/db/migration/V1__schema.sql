-- V1__schema.sql
SET NAMES utf8mb4;

CREATE TABLE app_user (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  email           VARCHAR(190)    NOT NULL,
  display_name    VARCHAR(64)     NOT NULL,
  password_hash   CHAR(60)        NOT NULL,
  role            VARCHAR(16)     NOT NULL DEFAULT 'USER',
  status          VARCHAR(16)     NOT NULL DEFAULT 'ACTIVE',
  created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                                  ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uq_app_user_email (email),
  CONSTRAINT ck_app_user_role   CHECK (role IN ('USER','ADMIN')),
  CONSTRAINT ck_app_user_status CHECK (status IN ('ACTIVE','DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='登录用户';

CREATE TABLE refresh_token (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id       BIGINT UNSIGNED NOT NULL,
  token_hash    CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  expires_at    DATETIME(3)     NOT NULL,
  revoked_at    DATETIME(3)     NULL,
  last_used_at  DATETIME(3)     NULL,
  created_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uq_refresh_token_hash (token_hash),
  KEY idx_refresh_user_expires (user_id, expires_at),
  KEY idx_refresh_expires (expires_at),
  CONSTRAINT fk_refresh_user FOREIGN KEY (user_id)
    REFERENCES app_user (id) ON DELETE CASCADE,
  CONSTRAINT ck_refresh_expiry CHECK (expires_at > created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='刷新令牌，仅存 SHA-256';

CREATE TABLE template_category (
  id          VARCHAR(32)  NOT NULL,
  name        VARCHAR(32)  NOT NULL,
  sort_order  INT          NOT NULL DEFAULT 0,
  created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uq_template_category_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='模板分类';

CREATE TABLE template (
  id                    VARCHAR(64)   NOT NULL,
  category_id           VARCHAR(32)   NOT NULL,
  title                 VARCHAR(80)   NOT NULL,
  description           VARCHAR(500)  NOT NULL,
  cover_path            VARCHAR(255)  NOT NULL,
  required_image_count  TINYINT UNSIGNED NOT NULL,
  accepted_mime_types   VARCHAR(120)  NOT NULL DEFAULT 'image/jpeg,image/png',
  max_file_size_bytes   INT UNSIGNED  NOT NULL DEFAULT 10485760,
  canvas_width          SMALLINT UNSIGNED NOT NULL DEFAULT 1200,
  canvas_height         SMALLINT UNSIGNED NOT NULL DEFAULT 1600,
  sort_order            INT           NOT NULL DEFAULT 0,
  is_published          TINYINT(1)    NOT NULL DEFAULT 1,
  created_at            DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at            DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                                      ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_template_category (category_id, sort_order),
  KEY idx_template_published (is_published, sort_order),
  CONSTRAINT fk_template_category FOREIGN KEY (category_id)
    REFERENCES template_category (id) ON DELETE RESTRICT,
  CONSTRAINT ck_template_title      CHECK (CHAR_LENGTH(title) BETWEEN 1 AND 80),
  CONSTRAINT ck_template_desc       CHECK (CHAR_LENGTH(description) BETWEEN 1 AND 500),
  CONSTRAINT ck_template_count      CHECK (required_image_count BETWEEN 1 AND 5),
  CONSTRAINT ck_template_size       CHECK (max_file_size_bytes BETWEEN 1048576 AND 10485760),
  CONSTRAINT ck_template_published  CHECK (is_published IN (0,1)),
  CONSTRAINT ck_template_canvas_w   CHECK (canvas_width  BETWEEN 64 AND 4096),
  CONSTRAINT ck_template_canvas_h   CHECK (canvas_height BETWEEN 64 AND 4096),
  CONSTRAINT ck_template_mime       CHECK (accepted_mime_types IN
                                     ('image/jpeg,image/png','image/jpeg','image/png'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='制作模板';

CREATE TABLE template_category_translation (
  category_id  VARCHAR(32)  NOT NULL,
  locale       VARCHAR(10)  NOT NULL,
  name         VARCHAR(80)  NOT NULL,
  PRIMARY KEY (category_id, locale),
  CONSTRAINT fk_category_translation FOREIGN KEY (category_id)
    REFERENCES template_category (id) ON DELETE CASCADE,
  CONSTRAINT ck_category_translation_locale CHECK (locale IN ('zh-CN','en-US')),
  CONSTRAINT ck_category_translation_name   CHECK (CHAR_LENGTH(name) BETWEEN 1 AND 80)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='分类翻译；基础列 name 为 zh-CN';

CREATE TABLE template_translation (
  template_id  VARCHAR(64)   NOT NULL,
  locale       VARCHAR(10)   NOT NULL,
  title        VARCHAR(80)   NOT NULL,
  description  VARCHAR(500)  NOT NULL,
  PRIMARY KEY (template_id, locale),
  CONSTRAINT fk_template_translation FOREIGN KEY (template_id)
    REFERENCES template (id) ON DELETE CASCADE,
  CONSTRAINT ck_template_translation_locale CHECK (locale IN ('zh-CN','en-US')),
  CONSTRAINT ck_template_translation_title  CHECK (CHAR_LENGTH(title) BETWEEN 1 AND 80),
  CONSTRAINT ck_template_translation_desc   CHECK (CHAR_LENGTH(description) BETWEEN 1 AND 500)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='模板翻译；基础列为 zh-CN，缺翻译回退基础列';

CREATE TABLE upload_asset (
  id           CHAR(36)        NOT NULL,
  user_id      BIGINT UNSIGNED NOT NULL,
  storage_key  VARCHAR(255)    NOT NULL,
  mime_type    VARCHAR(64)     NOT NULL,
  size_bytes   BIGINT UNSIGNED NOT NULL,
  width        INT UNSIGNED    NOT NULL,
  height       INT UNSIGNED    NOT NULL,
  created_at   DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uq_asset_storage_key (storage_key),
  KEY idx_asset_user_created (user_id, created_at),
  CONSTRAINT fk_asset_user FOREIGN KEY (user_id)
    REFERENCES app_user (id) ON DELETE RESTRICT,
  CONSTRAINT ck_asset_mime   CHECK (mime_type IN ('image/jpeg','image/png')),
  CONSTRAINT ck_asset_size   CHECK (size_bytes > 0),
  CONSTRAINT ck_asset_width  CHECK (width  BETWEEN 1 AND 8192),
  CONSTRAINT ck_asset_height CHECK (height BETWEEN 1 AND 8192)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='用户上传素材，READY 后不可变';

CREATE TABLE task (
  id                    CHAR(36)        NOT NULL,
  user_id               BIGINT UNSIGNED NOT NULL,
  template_id           VARCHAR(64)     NOT NULL,
  template_title        VARCHAR(80)     NOT NULL,
  template_cover_path   VARCHAR(255)    NOT NULL,
  canvas_width          SMALLINT UNSIGNED NOT NULL,
  canvas_height         SMALLINT UNSIGNED NOT NULL,
  client_request_id     CHAR(36)        NOT NULL,
  status                VARCHAR(16)     NOT NULL,
  current_stage         VARCHAR(24)     NULL,
  max_step_attempts     TINYINT UNSIGNED NOT NULL DEFAULT 3,
  retry_count           INT UNSIGNED    NOT NULL DEFAULT 0,
  resume_count          INT UNSIGNED    NOT NULL DEFAULT 0,
  next_retry_at         DATETIME(3)     NULL,
  last_error_code       VARCHAR(48)     NULL,
  last_error_detail     VARCHAR(500)    NULL,
  last_error_simulated  TINYINT(1)      NULL,
  failure_code          VARCHAR(48)     NULL,
  failure_args          JSON            NULL,
  failure_detail        VARCHAR(500)    NULL,
  failure_simulated     TINYINT(1)      NOT NULL DEFAULT 0,
  result_storage_key    VARCHAR(255)    NULL,
  result_width          INT UNSIGNED    NULL,
  result_height         INT UNSIGNED    NULL,
  result_size_bytes     BIGINT UNSIGNED NULL,
  created_at            DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  started_at            DATETIME(3)     NULL,
  finished_at           DATETIME(3)     NULL,
  updated_at            DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                                        ON UPDATE CURRENT_TIMESTAMP(3),
  row_version           INT UNSIGNED    NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uq_task_user_request (user_id, client_request_id),
  UNIQUE KEY uq_task_result_key (result_storage_key),
  KEY idx_task_user_created (user_id, created_at),
  KEY idx_task_user_template_created (user_id, template_id, created_at),
  KEY idx_task_queued (status, created_at),
  KEY idx_task_retry (status, next_retry_at),
  KEY idx_task_running (status, updated_at),
  CONSTRAINT fk_task_user FOREIGN KEY (user_id)
    REFERENCES app_user (id) ON DELETE RESTRICT,
  CONSTRAINT fk_task_template FOREIGN KEY (template_id)
    REFERENCES template (id) ON DELETE RESTRICT,
  CONSTRAINT ck_task_status CHECK (status IN ('QUEUED','RUNNING','RETRYING','SUCCEEDED','FAILED')),
  CONSTRAINT ck_task_stage  CHECK (current_stage IS NULL OR
                                   current_stage IN ('MATERIAL_CHECK','GENERATE','FINALIZE')),
  CONSTRAINT ck_task_stage_required CHECK (status = 'QUEUED' OR current_stage IS NOT NULL),
  CONSTRAINT ck_task_queued_clean   CHECK (status <> 'QUEUED' OR finished_at IS NULL),
  CONSTRAINT ck_task_started CHECK (status = 'QUEUED' OR started_at IS NOT NULL),
  CONSTRAINT ck_task_finished CHECK (status IN ('QUEUED','RUNNING','RETRYING') OR finished_at IS NOT NULL),
  CONSTRAINT ck_task_retrying CHECK (
    status <> 'RETRYING' OR (
      next_retry_at IS NOT NULL AND
      last_error_code IS NOT NULL AND
      finished_at IS NULL AND
      failure_code IS NULL
    )
  ),
  CONSTRAINT ck_task_next_retry CHECK (
    (status =  'RETRYING' AND next_retry_at IS NOT NULL) OR
    (status <> 'RETRYING' AND next_retry_at IS NULL)
  ),
  CONSTRAINT ck_task_last_error CHECK (
    status = 'RETRYING' OR (
      last_error_code IS NULL AND last_error_detail IS NULL AND last_error_simulated IS NULL
    )
  ),
  CONSTRAINT ck_task_max_attempts CHECK (max_step_attempts BETWEEN 1 AND 5),
  CONSTRAINT ck_task_running_clean CHECK (
    status <> 'RUNNING' OR finished_at IS NULL
  ),
  CONSTRAINT ck_task_simulated CHECK (failure_simulated IN (0,1)),
  CONSTRAINT ck_task_last_simulated CHECK (last_error_simulated IS NULL OR last_error_simulated IN (0,1)),
  CONSTRAINT ck_task_result CHECK (
    status <> 'SUCCEEDED' OR (
      result_storage_key IS NOT NULL AND
      result_width  IS NOT NULL AND result_width  > 0 AND
      result_height IS NOT NULL AND result_height > 0 AND
      result_size_bytes IS NOT NULL AND result_size_bytes > 0
    )
  ),
  CONSTRAINT ck_task_failure CHECK (
    status <> 'FAILED' OR failure_code IS NOT NULL
  ),
  CONSTRAINT ck_task_result_only_success CHECK (
    status = 'SUCCEEDED' OR (
      result_storage_key IS NULL AND result_width IS NULL AND
      result_height IS NULL AND result_size_bytes IS NULL
    )
  ),
  CONSTRAINT ck_task_failure_only_failed CHECK (
    status = 'FAILED' OR (
      failure_code IS NULL AND failure_args IS NULL AND
      failure_detail IS NULL AND failure_simulated = 0
    )
  ),
  CONSTRAINT ck_task_time CHECK (
    (started_at  IS NULL OR started_at  >= created_at) AND
    (finished_at IS NULL OR finished_at >= created_at) AND
    (finished_at IS NULL OR started_at IS NULL OR finished_at >= started_at)
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='制作任务；模板字段为创建时快照，含重试与续跑状态';

CREATE TABLE task_step (
  id             BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT,
  task_id        CHAR(36)         NOT NULL,
  seq            TINYINT UNSIGNED NOT NULL,
  stage          VARCHAR(24)      NOT NULL,
  status         VARCHAR(16)      NOT NULL DEFAULT 'PENDING',
  attempt_count  TINYINT UNSIGNED NOT NULL DEFAULT 0,
  message_args   JSON             NULL,
  error_code     VARCHAR(48)      NULL,
  started_at     DATETIME(3)      NULL,
  finished_at    DATETIME(3)      NULL,
  updated_at     DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                                  ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uq_step_task_seq (task_id, seq),
  UNIQUE KEY uq_step_task_stage (task_id, stage),
  KEY idx_step_task_status (task_id, status),
  CONSTRAINT fk_step_task FOREIGN KEY (task_id)
    REFERENCES task (id) ON DELETE CASCADE,
  CONSTRAINT ck_step_seq    CHECK (seq BETWEEN 1 AND 3),
  CONSTRAINT ck_step_stage  CHECK (stage IN ('MATERIAL_CHECK','GENERATE','FINALIZE')),
  CONSTRAINT ck_step_status CHECK (status IN ('PENDING','RUNNING','SUCCEEDED','FAILED','SKIPPED')),
  CONSTRAINT ck_step_attempt_count CHECK (attempt_count BETWEEN 0 AND 5),
  CONSTRAINT ck_step_started CHECK (
    status NOT IN ('RUNNING','SUCCEEDED','FAILED') OR started_at IS NOT NULL
  ),
  CONSTRAINT ck_step_finished CHECK (
    status NOT IN ('SUCCEEDED','FAILED') OR finished_at IS NOT NULL
  ),
  CONSTRAINT ck_step_pending_skipped CHECK (
    status NOT IN ('PENDING','SKIPPED') OR (started_at IS NULL AND finished_at IS NULL)
  ),
  CONSTRAINT ck_step_error CHECK (
    status = 'FAILED' OR error_code IS NULL
  ),
  CONSTRAINT ck_step_time CHECK (
    finished_at IS NULL OR (started_at IS NOT NULL AND finished_at >= started_at)
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='任务三步记录；消息参数与稳定错误码，不存已翻译文本';

CREATE TABLE task_step_attempt (
  id            BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT,
  task_id       CHAR(36)         NOT NULL,
  step_id       BIGINT UNSIGNED  NOT NULL,
  attempt_no    TINYINT UNSIGNED NOT NULL,
  status        VARCHAR(16)      NOT NULL,
  error_code    VARCHAR(48)      NULL,
  error_detail  VARCHAR(500)     NULL,
  started_at    DATETIME(3)      NOT NULL,
  finished_at   DATETIME(3)      NULL,
  created_at    DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uq_attempt_step_no (step_id, attempt_no),
  KEY idx_attempt_task_created (task_id, created_at),
  KEY idx_attempt_step_status (step_id, status),
  CONSTRAINT fk_attempt_task FOREIGN KEY (task_id)
    REFERENCES task (id) ON DELETE CASCADE,
  CONSTRAINT fk_attempt_step FOREIGN KEY (step_id)
    REFERENCES task_step (id) ON DELETE CASCADE,
  CONSTRAINT ck_attempt_no CHECK (attempt_no BETWEEN 1 AND 100),
  CONSTRAINT ck_attempt_status CHECK (status IN ('RUNNING','SUCCEEDED','FAILED','INTERRUPTED')),
  CONSTRAINT ck_attempt_error CHECK (
    status = 'FAILED' OR (error_code IS NULL AND error_detail IS NULL)
  ),
  CONSTRAINT ck_attempt_finished CHECK (
    (status =  'RUNNING' AND finished_at IS NULL) OR
    (status <> 'RUNNING' AND finished_at IS NOT NULL)
  ),
  CONSTRAINT ck_attempt_time CHECK (
    finished_at IS NULL OR finished_at >= started_at
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='步骤执行尝试；RUNNING→SUCCEEDED/FAILED/INTERRUPTED';

CREATE TABLE task_material (
  task_id     CHAR(36)        NOT NULL,
  slot_index  TINYINT UNSIGNED NOT NULL,
  asset_id    CHAR(36)        NOT NULL,
  created_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (task_id, slot_index),
  UNIQUE KEY uq_material_task_asset (task_id, asset_id),
  KEY idx_material_asset (asset_id),
  CONSTRAINT fk_material_task FOREIGN KEY (task_id)
    REFERENCES task (id) ON DELETE CASCADE,
  CONSTRAINT fk_material_asset FOREIGN KEY (asset_id)
    REFERENCES upload_asset (id) ON DELETE RESTRICT,
  CONSTRAINT ck_material_slot CHECK (slot_index BETWEEN 1 AND 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='任务素材顺序，slot_index 对应上传位';

CREATE TABLE simulation_setting (
  id                       TINYINT UNSIGNED NOT NULL DEFAULT 1,
  material_check_delay_ms  INT UNSIGNED NOT NULL DEFAULT 800,
  generate_delay_ms        INT UNSIGNED NOT NULL DEFAULT 3000,
  finalize_delay_ms        INT UNSIGNED NOT NULL DEFAULT 1200,
  fail_stage               VARCHAR(24)  NULL,
  fail_times               INT UNSIGNED NOT NULL DEFAULT 0,
  fail_retryable           TINYINT(1)   NOT NULL DEFAULT 0,
  updated_by               BIGINT UNSIGNED NULL,
  updated_at               DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                                        ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  CONSTRAINT fk_simulation_user FOREIGN KEY (updated_by)
    REFERENCES app_user (id) ON DELETE SET NULL,
  CONSTRAINT ck_simulation_singleton CHECK (id = 1),
  CONSTRAINT ck_simulation_delay_check CHECK (material_check_delay_ms <= 60000),
  CONSTRAINT ck_simulation_delay_gen   CHECK (generate_delay_ms <= 60000),
  CONSTRAINT ck_simulation_delay_fin   CHECK (finalize_delay_ms <= 60000),
  CONSTRAINT ck_simulation_fail_stage  CHECK (
    fail_stage IS NULL OR fail_stage IN ('MATERIAL_CHECK','GENERATE','FINALIZE')
  ),
  CONSTRAINT ck_simulation_fail_pair   CHECK (
    (fail_times = 0 AND fail_stage IS NULL) OR
    (fail_times > 0 AND fail_stage IS NOT NULL)
  ),
  CONSTRAINT ck_simulation_retryable   CHECK (
    fail_retryable IN (0,1) AND (fail_times > 0 OR fail_retryable = 0)
  ),
  CONSTRAINT ck_simulation_fail_times  CHECK (fail_times <= 50)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='全局模拟配置，单行';
