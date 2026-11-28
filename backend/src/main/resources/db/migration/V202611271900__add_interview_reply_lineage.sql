ALTER TABLE `interview_message`
  ADD COLUMN `superseded_by_message_id` BIGINT NULL,
  ADD COLUMN `reply_to_message_id` BIGINT NULL;

ALTER TABLE `interview_message`
  ADD CONSTRAINT `fk_message_superseded_by`
  FOREIGN KEY (`superseded_by_message_id`) REFERENCES `interview_message` (`id`) ON DELETE SET NULL,
  ADD CONSTRAINT `fk_message_reply_to`
  FOREIGN KEY (`reply_to_message_id`) REFERENCES `interview_message` (`id`) ON DELETE SET NULL;

CREATE TEMPORARY TABLE `interview_reply_lineage` (
  `message_id` BIGINT PRIMARY KEY,
  `answer_id` BIGINT NOT NULL
);

INSERT INTO `interview_reply_lineage` (`message_id`, `answer_id`)
SELECT `id`, `answer_id`
FROM (
  SELECT
    `assistant`.`id`,
    `user_message`.`id` AS `answer_id`,
    ROW_NUMBER() OVER (PARTITION BY `user_message`.`id` ORDER BY `assistant`.`seq_num`) AS `reply_rank`
  FROM `interview_message` AS `assistant`
  JOIN `interview_message` AS `user_message`
    ON `user_message`.`session_id` = `assistant`.`session_id`
    AND `user_message`.`role` = 'user'
    AND `user_message`.`seq_num` < `assistant`.`seq_num`
  WHERE `assistant`.`role` = 'assistant'
    AND NOT EXISTS (
      SELECT 1
      FROM `interview_message` AS `intervening`
      WHERE `intervening`.`session_id` = `assistant`.`session_id`
        AND `intervening`.`role` = 'user'
        AND `intervening`.`seq_num` > `user_message`.`seq_num`
        AND `intervening`.`seq_num` < `assistant`.`seq_num`
    )
) AS `ranked`
WHERE `reply_rank` = 1;

UPDATE `interview_message` AS `message`
JOIN `interview_reply_lineage` AS `lineage` ON `lineage`.`message_id` = `message`.`id`
SET `message`.`reply_to_message_id` = `lineage`.`answer_id`;

DROP TEMPORARY TABLE `interview_reply_lineage`;

ALTER TABLE `interview_message`
  ADD UNIQUE KEY `uk_message_reply_to` (`reply_to_message_id`),
  ADD CONSTRAINT `chk_message_assessment_score` CHECK (
    (`assessment_status` IS NULL AND `score` IS NULL)
    OR (`assessment_status` = 'PENDING' AND `score` IS NULL)
    OR (`assessment_status` = 'SCORED' AND `score` BETWEEN 1 AND 10)
    OR (`assessment_status` = 'UNSCORED' AND `score` IS NULL)
  );
