CREATE TABLE business (
  id UUID PRIMARY KEY,
  slug VARCHAR(80) NOT NULL UNIQUE,
  name VARCHAR(120) NOT NULL,
  category VARCHAR(80) NOT NULL,
  zone_id VARCHAR(80) NOT NULL,
  address VARCHAR(200) NOT NULL
);
CREATE TABLE app_user (
  id UUID PRIMARY KEY,
  business_id UUID NOT NULL REFERENCES business(id),
  email VARCHAR(180) NOT NULL UNIQUE,
  password_hash VARCHAR(100) NOT NULL,
  display_name VARCHAR(100) NOT NULL
);
CREATE TABLE professional (
  id UUID PRIMARY KEY,
  business_id UUID NOT NULL REFERENCES business(id),
  name VARCHAR(100) NOT NULL,
  specialty VARCHAR(160) NOT NULL,
  UNIQUE (id, business_id)
);
CREATE TABLE service_offering (
  id UUID PRIMARY KEY,
  business_id UUID NOT NULL REFERENCES business(id),
  name VARCHAR(100) NOT NULL,
  description VARCHAR(240) NOT NULL,
  duration_minutes INTEGER NOT NULL CHECK (duration_minutes BETWEEN 15 AND 240 AND MOD(duration_minutes, 15) = 0),
  price_cents INTEGER NOT NULL CHECK (price_cents BETWEEN 0 AND 1000000),
  UNIQUE (id, business_id)
);
CREATE TABLE weekly_hours (
  id UUID PRIMARY KEY,
  business_id UUID NOT NULL,
  professional_id UUID NOT NULL,
  weekday INTEGER NOT NULL CHECK (weekday BETWEEN 1 AND 7),
  opens_at TIME NOT NULL,
  closes_at TIME NOT NULL,
  CHECK (opens_at < closes_at),
  FOREIGN KEY (professional_id, business_id) REFERENCES professional(id, business_id)
);
CREATE TABLE time_block (
  id UUID PRIMARY KEY,
  business_id UUID NOT NULL,
  professional_id UUID NOT NULL,
  starts_at TIMESTAMP WITH TIME ZONE NOT NULL,
  ends_at TIMESTAMP WITH TIME ZONE NOT NULL,
  reason VARCHAR(160) NOT NULL,
  CHECK (starts_at < ends_at),
  FOREIGN KEY (professional_id, business_id) REFERENCES professional(id, business_id)
);
CREATE TABLE appointment (
  id UUID PRIMARY KEY,
  business_id UUID NOT NULL,
  professional_id UUID NOT NULL,
  service_id UUID NOT NULL,
  service_name VARCHAR(100) NOT NULL,
  price_cents INTEGER NOT NULL CHECK (price_cents >= 0),
  customer_name VARCHAR(100) NOT NULL,
  customer_phone VARCHAR(25) NOT NULL,
  starts_at TIMESTAMP WITH TIME ZONE NOT NULL,
  ends_at TIMESTAMP WITH TIME ZONE NOT NULL,
  status VARCHAR(20) NOT NULL CHECK (status IN ('BOOKED', 'CANCELLED')),
  reference VARCHAR(16) NOT NULL UNIQUE,
  request_key UUID NOT NULL,
  request_fingerprint VARCHAR(64) NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
  CHECK (starts_at < ends_at),
  UNIQUE (business_id, request_key),
  FOREIGN KEY (professional_id, business_id) REFERENCES professional(id, business_id),
  FOREIGN KEY (service_id, business_id) REFERENCES service_offering(id, business_id)
);
CREATE INDEX idx_appointment_calendar ON appointment(business_id, professional_id, starts_at, ends_at, status);
CREATE INDEX idx_block_calendar ON time_block(business_id, professional_id, starts_at, ends_at);
CREATE INDEX idx_hours_professional ON weekly_hours(business_id, professional_id, weekday);
