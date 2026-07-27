-- Migration: 007_sys_user_add_updated_by
-- Purpose: Add updated_by column for tracking last modifier of user records
-- Related: UserManagementService writes updated_by on updateUser / updateUserStatus

ALTER TABLE sys_user
  ADD COLUMN updated_by BIGINT UNSIGNED DEFAULT NULL COMMENT 'Last modified by user ID'
  AFTER updated_at;
