CREATE TABLE `retrieval_scope_tombstone` (
  `scope_type` VARCHAR(32) NOT NULL,
  `scope_id` BIGINT NOT NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`scope_type`, `scope_id`)
);

ALTER TABLE `interview_stage`
  ADD COLUMN `boundary_seq` INT NULL;
ALTER TABLE `interview_stage`
  ADD COLUMN `awaiting_answer` TINYINT(1) NOT NULL DEFAULT 0;

UPDATE `interview_stage` AS stage
SET `boundary_seq` = COALESCE((
  SELECT MAX(message.`seq_num`)
  FROM `interview_message` AS message
  WHERE message.`session_id` = stage.`session_id`
    AND message.`created_at` <= stage.`started_at`
), -1);

UPDATE `interview_stage` AS stage
SET `awaiting_answer` = (
  COALESCE((
    SELECT MAX(message.`seq_num`)
    FROM `interview_message` AS message
    WHERE message.`session_id` = stage.`session_id`
      AND message.`role` = 'assistant'
      AND message.`seq_num` > stage.`boundary_seq`
  ), -1) > stage.`boundary_seq`
  AND NOT EXISTS (
    SELECT 1
    FROM `interview_message` AS message
    WHERE message.`session_id` = stage.`session_id`
      AND message.`role` = 'user'
      AND message.`seq_num` > COALESCE((
        SELECT MAX(assistant.`seq_num`)
        FROM `interview_message` AS assistant
        WHERE assistant.`session_id` = stage.`session_id`
          AND assistant.`role` = 'assistant'
          AND assistant.`seq_num` > stage.`boundary_seq`
      ), -1)
  )
)
WHERE stage.`ended_at` IS NULL;

ALTER TABLE `interview_stage`
  MODIFY COLUMN `boundary_seq` INT NOT NULL;
ALTER TABLE `interview_stage`
  MODIFY COLUMN `awaiting_answer` TINYINT(1) NOT NULL DEFAULT 0;
