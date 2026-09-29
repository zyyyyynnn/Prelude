-- Resume conversation pin, matching the interview session list affordance so the
-- sidebar's pin control has a real landing site on this side too.
ALTER TABLE `resume_conversation`
  ADD COLUMN `pinned_at` DATETIME DEFAULT NULL COMMENT '列表置顶时间，NULL 表示未置顶' AFTER `title`;

ALTER TABLE `resume_conversation`
  ADD KEY `idx_resume_conversation_account_pinned` (`account_id`, `pinned_at`, `updated_at`);
