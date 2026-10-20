-- Run ONCE on an existing runningerrands database; do not rerun destructive backend/runningerrands.sql.
-- No historic campus membership is guessed: pre-existing users and tasks remain NULL until reviewed.
CREATE TABLE IF NOT EXISTS school (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  status TINYINT NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_school_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS school_campus (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  school_id BIGINT UNSIGNED NOT NULL,
  name VARCHAR(100) NOT NULL,
  status TINYINT NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_school_campus (school_id, name),
  KEY idx_school (school_id),
  CONSTRAINT fk_campus_school FOREIGN KEY (school_id) REFERENCES school(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE user
  ADD COLUMN campus_id BIGINT UNSIGNED DEFAULT NULL COMMENT 'Admin-approved campus membership',
  ADD COLUMN pending_campus_id BIGINT UNSIGNED DEFAULT NULL COMMENT 'Campus chosen in unreviewed student certification';
CREATE INDEX idx_user_campus_certify ON user (campus_id, is_certify);

ALTER TABLE task
  ADD COLUMN campus_id BIGINT UNSIGNED DEFAULT NULL COMMENT 'Immutable campus snapshot when task was published';
CREATE INDEX idx_task_campus_hall ON task (campus_id, status, expire_time, created_at);
