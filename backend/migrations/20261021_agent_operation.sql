-- Phase 3 only. Apply after existing campus and service-booking migrations.
-- Existing Java wallet/task/order tables remain authoritative.
CREATE TABLE IF NOT EXISTS agent_operation (
  operation_id CHAR(36) NOT NULL PRIMARY KEY,
  user_id BIGINT NOT NULL,
  campus_id BIGINT NOT NULL,
  mode VARCHAR(16) NOT NULL,
  payload_json JSON NOT NULL,
  payload_hash CHAR(64) NOT NULL,
  confirm_token_hash CHAR(64) NOT NULL,
  quoted_service_fee DECIMAL(10,2) NOT NULL,
  quoted_goods_budget DECIMAL(10,2) NOT NULL,
  quoted_total DECIMAL(10,2) NOT NULL,
  expires_at DATETIME NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'PREPARED',
  task_id BIGINT DEFAULT NULL,
  order_id BIGINT DEFAULT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_agent_operation_owner (user_id, campus_id, created_at),
  KEY idx_agent_operation_expiration (status, expires_at),
  CONSTRAINT chk_agent_mode CHECK (mode IN ('PUBLIC_TASK','SERVICE_OFFER')),
  CONSTRAINT chk_agent_status CHECK (status IN ('PREPARED','SUCCEEDED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
