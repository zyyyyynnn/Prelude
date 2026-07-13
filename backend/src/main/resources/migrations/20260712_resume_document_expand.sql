-- Manual expand migration for existing Prelude databases.
-- schema.sql contains the same idempotent guards and remains the bootstrap source of truth.

SET @sql = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `resume` ADD COLUMN `document_json` LONGTEXT NULL COMMENT ''ResumeDocument 结构化真源''',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'resume' AND COLUMN_NAME = 'document_json'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `resume` ADD COLUMN `document_version` INT NULL COMMENT ''结构化文档版本''',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'resume' AND COLUMN_NAME = 'document_version'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `resume` ADD COLUMN `source_type` VARCHAR(32) NULL COMMENT ''pdf_import/editor/fixture''',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'resume' AND COLUMN_NAME = 'source_type'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `resume` ADD COLUMN `plain_text_projection` MEDIUMTEXT NULL COMMENT ''确定性文本投影缓存''',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'resume' AND COLUMN_NAME = 'plain_text_projection'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
