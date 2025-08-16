-- Database Schema for Job Monitoring System
-- One row per company with all jobs stored as JSON blob

-- Job data table - stores one row per company with all jobs as JSON blob
CREATE TABLE IF NOT EXISTS `job_data` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `company_name` varchar(255) NOT NULL,
  `ashby_name` varchar(255) NOT NULL,
  `job_data` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `unique_company` (`company_name`),
  KEY `idx_company_name` (`company_name`),
  KEY `idx_ashby_name` (`ashby_name`),
  KEY `idx_created_at` (`created_at`),
  KEY `idx_updated_at` (`updated_at`),
  KEY `idx_job_data_company_name` (`company_name`),
  KEY `idx_job_data_ashby_name` (`ashby_name`),
  KEY `idx_job_data_created_at` (`created_at`),
  KEY `idx_job_data_updated_at` (`updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Insert default company data
INSERT INTO `job_data` (`company_name`, `ashby_name`, `job_data`) VALUES 
('Kikoff', 'kikoff', NULL)
ON DUPLICATE KEY UPDATE 
    `ashby_name` = VALUES(`ashby_name`),
    `updated_at` = CURRENT_TIMESTAMP;
