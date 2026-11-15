alter table professional add column whatsapp_phone varchar(15) not null default '';
alter table professional add constraint professional_whatsapp_phone check (whatsapp_phone='' or (char_length(whatsapp_phone) between 8 and 15));
