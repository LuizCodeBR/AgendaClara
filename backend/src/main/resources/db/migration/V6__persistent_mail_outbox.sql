alter table app_user add constraint uq_user_business unique(id,business_id);
create table mail_outbox (
  id uuid primary key,
  queue_seq bigint generated always as identity,
  business_id uuid not null,
  user_id uuid not null,
  purpose varchar(12) not null check(purpose in ('VERIFY','RESET','CHANGED')),
  delivery_mode varchar(8) not null check(delivery_mode in ('demo','resend')),
  status varchar(12) not null check(status in ('PENDING','PROCESSING','ACCEPTED','FAILED','EXPIRED','CANCELLED')),
  payload text,
  key_id varchar(64),
  attempts integer not null default 0 check(attempts>=0),
  manual_retries integer not null default 0 check(manual_retries between 0 and 1),
  created_at timestamp with time zone not null,
  expires_at timestamp with time zone not null,
  send_deadline timestamp with time zone not null,
  next_attempt_at timestamp with time zone,
  lease_until timestamp with time zone,
  lease_token uuid,
  accepted_at timestamp with time zone,
  last_error varchar(32),
  foreign key(user_id,business_id) references app_user(id,business_id)
);
create index idx_mail_due on mail_outbox(delivery_mode,status,next_attempt_at);
create index idx_mail_business on mail_outbox(business_id,created_at);
create index idx_mail_user on mail_outbox(user_id,created_at);
create table mail_worker_heartbeat (
  instance_id uuid primary key,
  delivery_mode varchar(8) not null,
  checked_at timestamp with time zone not null,
  last_error varchar(32)
);
create index idx_mail_expiry on mail_outbox(expires_at);

create table mail_key_identity (
  id integer primary key check(id=1),
  key_id varchar(64) not null
);
