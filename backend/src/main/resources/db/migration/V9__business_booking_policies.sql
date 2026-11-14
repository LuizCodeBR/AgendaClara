ALTER TABLE business ADD COLUMN booking_notice_minutes INTEGER NOT NULL DEFAULT 0 CHECK (booking_notice_minutes BETWEEN 0 AND 10080);
ALTER TABLE business ADD COLUMN cancel_notice_minutes INTEGER NOT NULL DEFAULT 0 CHECK (cancel_notice_minutes BETWEEN 0 AND 10080);
ALTER TABLE business ADD COLUMN reschedule_notice_minutes INTEGER NOT NULL DEFAULT 0 CHECK (reschedule_notice_minutes BETWEEN 0 AND 10080);
ALTER TABLE business ADD COLUMN booking_policy_version INTEGER NOT NULL DEFAULT 0 CHECK (booking_policy_version >= 0);
ALTER TABLE appointment ADD COLUMN booking_notice_minutes INTEGER NOT NULL DEFAULT 0 CHECK (booking_notice_minutes BETWEEN 0 AND 10080);
ALTER TABLE appointment ADD COLUMN cancel_notice_minutes INTEGER NOT NULL DEFAULT 0 CHECK (cancel_notice_minutes BETWEEN 0 AND 10080);
ALTER TABLE appointment ADD COLUMN reschedule_notice_minutes INTEGER NOT NULL DEFAULT 0 CHECK (reschedule_notice_minutes BETWEEN 0 AND 10080);
ALTER TABLE appointment ADD COLUMN policy_version INTEGER NOT NULL DEFAULT 0 CHECK (policy_version >= 0);
