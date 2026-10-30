-- V3__session_security.sql
SET NAMES utf8mb4;

ALTER TABLE app_user
  ADD COLUMN token_version INT UNSIGNED NOT NULL DEFAULT 0 AFTER status;

ALTER TABLE refresh_token
  ADD COLUMN family_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL AFTER user_id,
  ADD COLUMN family_expires_at DATETIME(3) NULL AFTER expires_at,
  ADD COLUMN revoked_reason VARCHAR(24) NULL AFTER revoked_at,
  ADD COLUMN replaced_by_id BIGINT UNSIGNED NULL AFTER revoked_reason;

UPDATE refresh_token
   SET family_id = UUID(),
       family_expires_at = expires_at
 WHERE family_id IS NULL;

ALTER TABLE refresh_token
  MODIFY COLUMN family_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  MODIFY COLUMN family_expires_at DATETIME(3) NOT NULL;

ALTER TABLE refresh_token
  ADD KEY idx_refresh_family (family_id),
  ADD KEY idx_refresh_user_revoked (user_id, revoked_at),
  ADD KEY idx_refresh_replaced_by (replaced_by_id),
  ADD CONSTRAINT fk_refresh_replaced_by FOREIGN KEY (replaced_by_id)
    REFERENCES refresh_token (id) ON DELETE SET NULL,
  ADD CONSTRAINT ck_refresh_revoked_reason CHECK (
    revoked_reason IS NULL OR revoked_reason IN ('LOGOUT','ROTATED','REUSE','DISABLED','EXPIRED')
  ),
  ADD CONSTRAINT ck_refresh_family_expiry CHECK (family_expires_at >= created_at);
