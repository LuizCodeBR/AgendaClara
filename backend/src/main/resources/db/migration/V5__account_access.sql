alter table app_user add column email_verified_at timestamp with time zone;
alter table app_user add column demo_email_verified_at timestamp with time zone;
alter table app_user add column email_verification_required boolean not null default false;
alter table app_user add column credentials_version integer not null default 0 check (credentials_version >= 0);
alter table app_user add column demo_mailbox_digest varchar(64);
alter table app_user add column demo_mailbox_expires_at timestamp with time zone;
create table account_action (
  id uuid primary key,
  user_id uuid not null references app_user(id),
  purpose varchar(12) not null check (purpose in ('VERIFY','RESET')),
  token_digest varchar(64) not null unique,
  simulated boolean not null,
  created_at timestamp with time zone not null,
  expires_at timestamp with time zone not null,
  used_at timestamp with time zone
);
create index idx_account_action_user on account_action(user_id,purpose,created_at);

create unique index idx_user_demo_mailbox on app_user(demo_mailbox_digest);
create index idx_user_business on app_user(business_id);
