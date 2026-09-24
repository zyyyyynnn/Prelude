-- Message order has to be a fact the database holds, not a convention the application keeps.
-- `seq_num` was allocated by reading a session's highest value and adding one inside a
-- process-local lock, so two writers — or one writer after a restart, or two threads that each
-- got a different monitor from a bounded cache — could land the same number on two rows of one
-- session, and the index below only made that pair cheap to read. `findLatest` orders by
-- `seq_num`, so once a session has two rows numbered alike "which turn is next" stops having a
-- single answer.
--
-- Renumber each session by insertion order before constraining it: `id` is monotonic with the
-- order rows were written and `seq_num` is only ever read as that order, so a well-formed
-- session is unchanged by this and a collided one becomes ordered again.
UPDATE `interview_message` AS m
JOIN (
  SELECT `id`,
         ROW_NUMBER() OVER (PARTITION BY `session_id` ORDER BY `id`) - 1 AS `seq_num`
  FROM `interview_message`
) AS r ON r.`id` = m.`id`
SET m.`seq_num` = r.`seq_num`;

-- The unique key makes a collision impossible; `idx_message_session_id` is dropped because
-- `session_id` is already this key's leftmost prefix.
ALTER TABLE `interview_message`
  DROP INDEX `idx_message_session_id`,
  DROP INDEX `idx_message_session_seq`,
  ADD UNIQUE KEY `uk_message_session_seq` (`session_id`, `seq_num`);
