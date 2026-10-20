-- Once only, after Phase 4. Accelerates task-level reconciliation joins.
ALTER TABLE `transaction_record` ADD INDEX `idx_tr_task_type` (`task_id`,`type`);
