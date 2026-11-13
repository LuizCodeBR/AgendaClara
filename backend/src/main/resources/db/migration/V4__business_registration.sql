alter table business add column public_booking_enabled boolean not null default true;
alter table business add column publication_version integer not null default 0 check (publication_version >= 0);
alter table app_user add column registration_key uuid;
alter table app_user add constraint uq_user_registration unique (registration_key);
