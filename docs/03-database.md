# 03 · MySQL 数据模型

> 数据库：MySQL 8.4 LTS，InnoDB，`utf8mb4_0900_ai_ci`，连接时区 UTC。
> 迁移：Flyway，文件位于 `backend/src/main/resources/db/migration/`。
> 本文 DDL 与迁移文件必须逐字对齐；字段语义以 [02-domain-model.md](02-domain-model.md) 为准。

## 1. 约定

| 项 | 约定 |
|---|---|
| 表名 | 小写下划线；业务表不使用复数 |
| 主键 | `app_user` 用 `BIGINT UNSIGNED AUTO_INCREMENT`；模板与分类用 `VARCHAR` slug；任务/素材用 `CHAR(36)` UUID |
| 时间 | `DATETIME(3)`，UTC；任务/步骤/刷新令牌的过期时间使用 SQL `UTC_TIMESTAMP(3)` 计算，避免应用与数据库时钟偏差触发时间顺序 CHECK |
| 布尔 | `TINYINT(1)`，0/1 |
| 枚举 | `VARCHAR` + `CHECK`，可读性优先；Java 枚举与 API 同值 |
| 金额 | 无 |
| 外键 | 必须显式命名；默认 `RESTRICT`，仅从属记录允许 `CASCADE` |
| 软删除 | 不使用；任务与结果永久保留，素材物理删除 |
| 字符集 | 表级 `utf8mb4`；哈希列用 `ascii` 节省空间 |

迁移文件：

| 文件 | 内容 |
|---|---|
| `V1__schema.sql` | 全部表、索引、约束 |
| `V2__seed.sql` | 分类、模板、演示账号、模拟配置默认值 |

> 种子口令仅用于演示环境，README 必须提示修改；生产部署不得沿用。

## 2. 表一览

| 表 | 角色 | 生命周期 |
|---|---|---|
| `app_user` | 登录用户 | 永久 |
| `refresh_token` | 刷新令牌哈希 | 7 天，可撤销 |
| `template_category` | 模板分类 | 种子 |
| `template_category_translation` | 分类的多语言名称 | 种子，随分类 |
| `template` | 模板与素材要求 | 种子，P0 只读 |
| `template_translation` | 模板标题与描述的多语言文案 | 种子，随模板 |
| `upload_asset` | 用户上传素材 | 用户删除或清理 |
| `task` | 制作任务聚合根 | 永久 |
| `task_step` | 三步执行记录 | 随任务 |
| `task_step_attempt` | 每次步骤执行尝试 | 随任务，永久审计 |
| `task_material` | 任务与素材的顺序关联 | 随任务 |
| `simulation_setting` | 全局模拟配置（单行） | 永久 |

```
app_user 1─n refresh_token
app_user 1─n upload_asset
app_user 1─n task
template_category 1─n template 1─n task
template_category 1─n template_category_translation
template 1─n template_translation
task 1─n task_step 1─n task_step_attempt
task 1─n task_material n─1 upload_asset
app_user 1─n simulation_setting (updated_by)
```

## 3. 建表 DDL

```sql
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
```

### 3.1 索引设计说明

| 索引 | 服务的查询 |
|---|---|
| `uq_task_user_request` | 提交幂等：同一用户同一请求只建一个任务 |
| `idx_task_user_created` | 个人历史按创建时间倒序 |
| `idx_task_user_template_created` | 制作台右栏：当前用户 + 模板的历史 |
| `idx_task_queued` | worker 认领最早的 `QUEUED` 任务 |
| `idx_task_retry` | worker 认领到期的 `RETRYING` 任务 |
| `idx_task_running` | 启动恢复扫描 `RUNNING` 任务 |
| `idx_step_task_status` | 详情页读三步、定位当前步骤 |
| `uq_attempt_step_no` | 尝试编号唯一，防止并发重复插入 |
| `idx_attempt_task_created` | 任务详情按时间展开尝试历史 |
| `idx_attempt_step_status` | 查找当前 `RUNNING` attempt |
| `idx_asset_user_created` | 素材列表/清理（P1） |
| `idx_refresh_user_expires` | 校验用户令牌、清理过期 |
| `idx_refresh_expires` | 过期令牌批量清理 |
| `idx_material_asset` | 删除素材前检查是否被任务引用 |

不建全文索引、不做分表；演示数据量级不需要。`message_args` / `failure_args` 使用 JSON 仅为存储结构化参数，不在 JSON 内做查询，因此不建函数索引。

## 4. 不变量与约束分工

数据库负责“行内自洽”，服务层负责“跨行、跨表与业务语义”。

| 编号 | 不变量 | 由谁保证 |
|---|---|---|
| DB-01 | 邮箱唯一 | `uq_app_user_email` |
| DB-02 | 刷新令牌哈希唯一且只存哈希 | `uq_refresh_token_hash` + 服务层 |
| DB-03 | 模板素材张数 1～5、画布与文件大小在范围 | `CHECK` |
| DB-04 | 任务/步骤/尝试状态与阶段只能是枚举值 | `CHECK` |
| DB-05 | 成功任务必须有完整结果元数据 | `ck_task_result` |
| DB-06 | 失败任务必须有失败码；非失败任务不得带失败字段 | `ck_task_failure` / `ck_task_failure_only_failed` |
| DB-07 | 非 `QUEUED`/`RETRYING` 任务必须有 `started_at`；终态必须有 `finished_at` | `CHECK` |
| DB-08 | 步骤顺序唯一、阶段唯一；尝试编号唯一 | `uq_step_task_seq` / `uq_step_task_stage` / `uq_attempt_step_no` |
| DB-09 | 同一任务不重复引用同一素材 | `uq_material_task_asset` |
| DB-10 | 同一用户的 `client_request_id` 唯一 | `uq_task_user_request` |
| DB-11 | 任务结果路径唯一 | `uq_task_result_key` |
| DB-12 | 素材所有者 = 任务所有者 | 服务层（创建事务校验） |
| DB-13 | 同一任务最多一个 RUNNING 步骤；每个步骤最多一个 RUNNING attempt | 服务层 + 任务行锁（MySQL 无部分唯一索引） |
| DB-14 | 任务终态不可变 | 服务层 `WHERE status IN ('RUNNING','RETRYING')` + 影响行数检查 |
| DB-15 | 失败后后续步骤必须 SKIPPED；重试等待期间后续步骤保持 PENDING | 服务层同一事务写入 |
| DB-16 | 任务快照不随模板变化 | 创建时复制字段，服务层不更新快照 |
| DB-17 | `RETRYING` 必须有 `next_retry_at` 与 `last_error_code`，且不得有 `failure_code` / `finished_at` | `ck_task_retrying` |
| DB-18 | 非 `RETRYING` 任务不得保留 `next_retry_at` 或 `last_error_*` | `ck_task_next_retry` / `ck_task_last_error` |
| DB-19 | 每步 `attempt_count` 在 0～5 | `ck_step_attempt_count` |
| DB-20 | 尝试终态必须有 `finished_at`；`RUNNING` 不得有 | `ck_attempt_finished` |
| DB-21 | 只有 `FAILED` 尝试/步骤可带错误码 | `ck_attempt_error` / `ck_step_error` |
| DB-22 | 中断尝试不计入 `attempt_count` | 服务层恢复时回退 |
| DB-23 | 翻译 locale 仅 `zh-CN` / `en-US` | 翻译表 `CHECK` |
| DB-24 | attempt 的 `task_id` 必须等于所属 step 的 `task_id` | 服务层（冗余字段，便于按任务查询） |

> 注意：MySQL 的 `CHECK` 对 `NULL` 结果视为通过，因此结果字段与开始/结束时间都显式写了 `IS NOT NULL`。`ck_task_last_error` 允许 `last_error_simulated` 为 `NULL`，因为 `TINYINT` 的 0 与“无错误”语义不同。

## 5. 关键查询口径

### 5.1 认领下一个任务

```sql
START TRANSACTION;
SELECT id
  FROM task
 WHERE status = 'QUEUED'
    OR (status = 'RETRYING' AND next_retry_at <= UTC_TIMESTAMP(3))
 ORDER BY created_at, id
 LIMIT 1
 FOR UPDATE SKIP LOCKED;

-- 认领：恢复的 QUEUED 保留 started_at/current_stage，重试任务清空 last_error
UPDATE task
   SET status = 'RUNNING',
       current_stage = ?,
       next_retry_at = NULL,
       last_error_code = NULL,
       last_error_detail = NULL,
       last_error_simulated = NULL,
       started_at = COALESCE(started_at, UTC_TIMESTAMP(3)),
       row_version = row_version + 1
 WHERE id = ? AND status IN ('QUEUED','RETRYING')
   AND (status = 'QUEUED' OR next_retry_at <= UTC_TIMESTAMP(3));

-- 找到第一个非 SUCCEEDED 步骤（PENDING 或 RETRYING 的 FAILED），开启新 attempt
UPDATE task_step
   SET status = 'RUNNING',
       started_at = UTC_TIMESTAMP(3),
       finished_at = NULL,
       error_code = NULL,
       attempt_count = attempt_count + 1
 WHERE task_id = ? AND seq = ? AND status IN ('PENDING','FAILED');

INSERT INTO task_step_attempt
  (task_id, step_id, attempt_no, status, started_at)
SELECT task_id, id,
       (SELECT COALESCE(MAX(attempt_no), 0) + 1
          FROM task_step_attempt WHERE step_id = task_step.id),
       'RUNNING', UTC_TIMESTAMP(3)
  FROM task_step
 WHERE task_id = ? AND seq = ?;
COMMIT;
```

说明：

- `FOR UPDATE SKIP LOCKED` 保证两个 worker 不会认领同一任务。
- `QUEUED` 与到期的 `RETRYING` 共用一条认领路径；未到期重试不会被提前执行。
- `current_stage` 取目标步骤的 `stage`；恢复后的任务从第一个非 `SUCCEEDED` 步骤继续。
- 认领与创建 attempt 必须同一事务，避免“任务 RUNNING 但没有 attempt”的中间状态。
- 服务层校验 task、step、attempt 的影响行数；任一不一致则回滚并记录告警，不进入流水线。

### 5.2 个人历史

```sql
SELECT t.id, t.template_id,
       COALESCE(tt.title, tm.title, t.template_title) AS template_title,
       t.template_cover_path, t.status, t.current_stage,
       t.failure_code, t.failure_args, t.failure_simulated,
       t.retry_count, t.resume_count, t.next_retry_at,
       t.last_error_code, t.last_error_simulated,
       t.result_width, t.result_height,
       t.created_at, t.started_at, t.finished_at
  FROM task t
  LEFT JOIN template tm ON tm.id = t.template_id
  LEFT JOIN template_translation tt
    ON tt.template_id = t.template_id AND tt.locale = ?
 WHERE t.user_id = ?
 ORDER BY t.created_at DESC, t.id DESC
 LIMIT ? OFFSET ?;
```

筛选 `status=active` 时追加 `AND t.status IN ('QUEUED','RUNNING','RETRYING')`；筛选模板时追加 `AND t.template_id = ?`。翻译缺失时回退模板基础列，模板不存在时回退创建时快照。

### 5.3 制作台轮询活跃任务

```sql
SELECT t.id, t.status, t.current_stage,
       t.failure_code, t.failure_args, t.failure_simulated,
       t.retry_count, t.next_retry_at, t.last_error_code, t.last_error_simulated,
       t.result_width, t.result_height,
       t.created_at, t.finished_at, t.updated_at
  FROM task t
 WHERE t.user_id = ?
   AND t.template_id = ?
   AND t.status IN ('QUEUED','RUNNING','RETRYING')
 ORDER BY t.created_at;
```

### 5.4 安排自动重试

```sql
START TRANSACTION;
-- 1. 关闭当前尝试
UPDATE task_step_attempt
   SET status = 'FAILED', error_code = ?, error_detail = ?, finished_at = UTC_TIMESTAMP(3)
 WHERE id = ? AND status = 'RUNNING';

-- 2. 步骤记录最近失败
UPDATE task_step
   SET status = 'FAILED', error_code = ?, finished_at = UTC_TIMESTAMP(3)
 WHERE id = ? AND status = 'RUNNING';

-- 3. 任务进入等待重试：退避秒数由服务层按 attempt_count 计算
UPDATE task
   SET status = 'RETRYING',
       next_retry_at = DATE_ADD(UTC_TIMESTAMP(3), INTERVAL ? SECOND),
       current_stage = ?,
       last_error_code = ?,
       last_error_detail = ?,
       last_error_simulated = ?,
       retry_count = retry_count + 1,
       row_version = row_version + 1
 WHERE id = ? AND status = 'RUNNING' AND row_version = ?;
COMMIT;
```

不可重试或预算耗尽时不执行上述第 3 步，改为在同一事务中写 `status='FAILED'`、`failure_*`、`finished_at`，清空 `last_error_*`，并把后续 `PENDING` 步骤置 `SKIPPED`。

### 5.5 启动恢复

```sql
START TRANSACTION;

-- 0. 理论兜底：三步骤都已成功但任务仍是 RUNNING，直接补成 SUCCEEDED
UPDATE task
   SET status = 'SUCCEEDED', finished_at = UTC_TIMESTAMP(3),
       row_version = row_version + 1
 WHERE status = 'RUNNING'
   AND result_storage_key IS NOT NULL
   AND result_width IS NOT NULL AND result_height IS NOT NULL
   AND result_size_bytes IS NOT NULL
   AND NOT EXISTS (
     SELECT 1 FROM task_step s WHERE s.task_id = task.id AND s.status <> 'SUCCEEDED'
   );

-- 1. 记录本次被中断的步骤（只用于回退 attempt_count）
CREATE TEMPORARY TABLE recovered_step (step_id BIGINT UNSIGNED PRIMARY KEY);
INSERT INTO recovered_step (step_id)
SELECT DISTINCT a.step_id
  FROM task_step_attempt a
  JOIN task t ON t.id = a.task_id
 WHERE t.status = 'RUNNING' AND a.status = 'RUNNING';

-- 2. 活动尝试标记 INTERRUPTED
UPDATE task_step_attempt a
  JOIN task t ON t.id = a.task_id
   SET a.status = 'INTERRUPTED', a.finished_at = UTC_TIMESTAMP(3)
 WHERE t.status = 'RUNNING' AND a.status = 'RUNNING';

-- 3. 被中断步骤回到 PENDING，并回退 attempt_count
UPDATE task_step s
  JOIN recovered_step r ON r.step_id = s.id
   SET s.status = 'PENDING', s.started_at = NULL, s.finished_at = NULL,
       s.error_code = NULL,
       s.attempt_count = GREATEST(s.attempt_count - 1, 0);

-- 4. 兜底：没有 attempt 的 RUNNING 步骤也回到 PENDING
UPDATE task_step s
  JOIN task t ON t.id = s.task_id
   SET s.status = 'PENDING', s.started_at = NULL, s.finished_at = NULL,
       s.error_code = NULL
 WHERE t.status = 'RUNNING' AND s.status = 'RUNNING';

-- 5. RUNNING 任务回到队列，保留 started_at/current_stage
UPDATE task
   SET status = 'QUEUED', next_retry_at = NULL, finished_at = NULL,
       last_error_code = NULL, last_error_detail = NULL, last_error_simulated = NULL,
       resume_count = resume_count + 1,
       row_version = row_version + 1
 WHERE status = 'RUNNING';

DROP TEMPORARY TABLE recovered_step;
COMMIT;
```

该事务在 Flyway 之后、worker 启动之前执行；单实例前提下不会误回收其他进程的任务。`RETRYING` 与 `QUEUED` 不参与恢复；`RETRYING` 的退避时间保持不变。临时目录 `storage/tmp/task-{taskId}/` 由服务层按任务清理。

### 5.6 删除未绑定素材

```sql
SELECT COUNT(1)
  FROM task_material
 WHERE asset_id = ?;

DELETE FROM upload_asset
 WHERE id = ? AND user_id = ?;
```

服务层先确认素材属于当前用户且引用数为 0；否则 409 `ASSET_IN_USE`。物理文件在事务提交后删除。

## 6. 种子数据

### 6.1 演示账号

| ID | 邮箱 | 密码 | 角色 | 展示名 | 用途 |
|---|---|---|---|---|---|
| 1 | `demo@framelab.local` | `Demo1234!` | USER | 林小满 | 主演示账号 |
| 2 | `demo2@framelab.local` | `Demo1234!` | USER | 周屿 | 验证账号隔离 |
| 3 | `admin@framelab.local` | `Admin1234!` | ADMIN | 运营管理员 | 模拟控制 |

BCrypt（cost 12）哈希：

```text
demo  / Demo1234!  -> $2b$12$oDiRaSYrHZCgC1ttuA3YneJun5CYoQwnGS/PGB/falO5W9o/R3uQG
demo2 / Demo1234!  -> $2b$12$y5pTs4.KwaiIPsxMxWZM6unJ1n9BCjIJuiIjzvKrk82svxYVJp9dK
admin / Admin1234! -> $2b$12$tw0gJenxXaEbmRMWWabXoOZyKtLl4BhfZaWQIG6o7VMUiS18kf8zO
```

> 已验证上述哈希与口令匹配。种子仅用于本地/评审环境；根 README 必须明确写出并提示替换。

### 6.2 分类

| id | name | sort_order |
|---|---|---|
| `travel` | 旅行风景 | 10 |
| `creative` | 创意图片 | 20 |
| `festival` | 节日营销 | 30 |
| `portrait` | 人像写真 | 40 |
| `life` | 生活记录 | 50 |
| `commerce` | 电商海报 | 60 |

### 6.3 模板（10 个）

| id | 分类 | 标题 | 素材数 | 画布 | 文件上限 |
|---|---|---|---|---|---|
| `travel-postcard` | travel | 旅行明信片 | 2 | 1600×1200 | 5 MB |
| `film-memory` | creative | 胶片记忆 | 1 | 1200×1600 | 5 MB |
| `summer-coast` | travel | 夏日海岸 | 3 | 1600×1200 | 10 MB |
| `christmas-letter` | festival | 圣诞来信 | 2 | 1200×1600 | 5 MB |
| `new-year-card` | festival | 新年福卡 | 1 | 1200×1600 | 5 MB |
| `pet-portrait` | life | 毛孩子写真 | 3 | 1200×1600 | 10 MB |
| `wedding-invite` | portrait | 婚礼邀请 | 2 | 1200×1600 | 10 MB |
| `minimal-portrait` | portrait | 极简人像 | 1 | 1200×1600 | 10 MB |
| `coffee-launch` | commerce | 咖啡上新 | 1 | 1200×1600 | 10 MB |
| `brand-story` | commerce | 品牌主理人 | 2 | 1200×1600 | 10 MB |

封面路径统一为 `/assets/templates/{id}.webp`，由后端 `static/assets/templates/` 提供；`accepted_mime_types` 全部为 `image/jpeg,image/png`。

### 6.4 种子 SQL

```sql
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
```

> 六个分类均有至少 1 个模板；分布为旅行风景 2、创意图片 1、节日营销 2、人像写真 2、生活记录 1、电商海报 2。
> 翻译种子共 16 行：6 个分类 + 10 个模板，仅 `en-US`；`zh-CN` 直接使用基础列，缺翻译时回退基础列。

### 6.5 模板封面文件

- 位置：`backend/src/main/resources/static/assets/templates/{id}.webp`
- 规格：1200×1600（或画布同比例），WebP quality 82，单张 ≤ 300 KB。
- 来源：免费可商用图库（Unsplash/Pexels）或自制抽象图；逐张记录作者、链接、许可证到 `backend/src/main/resources/static/assets/CREDITS.md`。
- 禁止使用公司内部素材、付费图库、含可识别人脸却无授权的照片；如使用人像素材，需确保许可允许演示与分发。

## 7. 迁移与回滚

| 规则 | 说明 |
|---|---|
| 只前进 | Flyway 迁移一经提交不得修改；修复用新版本（`V3__...`） |
| 命名 | `V{n}__{snake_case}.sql`，n 连续 |
| 启动执行 | 后端启动时自动迁移；生产可改为独立迁移步骤 |
| 备份 | 升级前 `mysqldump --single-transaction` + 备份 `storage/` |
| 回滚 | 优先回滚应用镜像；若迁移含删列/改类型，必须用备份恢复，不写逆向迁移 |
| 校验 | CI 用 Testcontainers 从空库跑完整迁移，验证 DDL 与种子可用 |

## 8. 数据保留与清理

| 数据 | 保留策略 | 实现 |
|---|---|---|
| 过期刷新令牌 | 过期后 7 天删除 | P0 启动时/每日清理；`DELETE ... WHERE expires_at < UTC_TIMESTAMP() - INTERVAL 7 DAY` |
| 未绑定素材 | 7 天后清理（P1） | 定时任务；P0 仅提供用户主动删除 |
| 任务/步骤/尝试/结果 | 永久 | 不删除 |
| 模拟配置 | 永久单行 | 可重置为默认 |
| 模板/分类翻译 | 随模板/分类 | 种子维护；缺失回退中文 |
| 日志 | 容器日志轮转 | 见 07；不在库内存储业务日志 |

## 9. 与领域模型的对应

| 领域概念 | 表 | 备注 |
|---|---|---|
| 用户 | `app_user` | 角色与状态 |
| 会话 | `refresh_token` | 只存哈希 |
| 分类/模板 | `template_category` / `template` | 只读种子 |
| 分类翻译 | `template_category_translation` | `en-US`；缺省回退基础列 |
| 模板翻译 | `template_translation` | `en-US`；缺省回退基础列 |
| 素材 | `upload_asset` | READY 后不可变 |
| 任务 | `task` | 含模板快照、重试与续跑状态、结果 |
| 步骤 | `task_step` | seq 1～3，消息参数与稳定错误码 |
| 步骤尝试 | `task_step_attempt` | 每次执行的审计记录 |
| 任务素材 | `task_material` | slot_index 对应上传位 |
| 模拟控制 | `simulation_setting` | 单行，含失败是否可重试 |
