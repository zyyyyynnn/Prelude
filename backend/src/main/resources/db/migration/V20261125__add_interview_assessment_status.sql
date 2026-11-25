ALTER TABLE `interview_message`
  ADD COLUMN `assessment_status` ENUM('PENDING','SCORED','UNSCORED') NULL DEFAULT 'PENDING';

ALTER TABLE `interview_message`
  ADD COLUMN `assessment_error` TEXT NULL;

UPDATE `interview_message`
SET `assessment_status` = CASE WHEN `score` IS NULL THEN 'UNSCORED' ELSE 'SCORED' END;
