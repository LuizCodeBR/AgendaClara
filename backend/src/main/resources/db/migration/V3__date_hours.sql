CREATE TABLE date_hours (
  id UUID PRIMARY KEY,
  business_id UUID NOT NULL,
  professional_id UUID NOT NULL,
  exception_date DATE NOT NULL,
  reason VARCHAR(160) NOT NULL,
  UNIQUE (business_id, professional_id, exception_date),
  UNIQUE (id, business_id),
  FOREIGN KEY (professional_id, business_id) REFERENCES professional(id, business_id)
);
CREATE TABLE date_hours_window (
  id UUID PRIMARY KEY,
  business_id UUID NOT NULL,
  exception_id UUID NOT NULL,
  opens_at TIME NOT NULL,
  closes_at TIME NOT NULL,
  CHECK (opens_at < closes_at),
  FOREIGN KEY (exception_id, business_id) REFERENCES date_hours(id, business_id)
);
CREATE INDEX idx_date_hours_window ON date_hours_window(business_id, exception_id, opens_at);
