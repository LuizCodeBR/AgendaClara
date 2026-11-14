CREATE TABLE professional_service (
  business_id UUID NOT NULL,
  professional_id UUID NOT NULL,
  service_id UUID NOT NULL,
  PRIMARY KEY (business_id, professional_id, service_id),
  FOREIGN KEY (professional_id, business_id) REFERENCES professional(id, business_id) ON DELETE CASCADE,
  FOREIGN KEY (service_id, business_id) REFERENCES service_offering(id, business_id) ON DELETE CASCADE
);
CREATE INDEX idx_professional_service_offering ON professional_service(business_id, service_id, professional_id);

-- Preserve the combinations offered before this migration, including inactive profiles.
INSERT INTO professional_service(business_id, professional_id, service_id)
SELECT p.business_id, p.id, s.id FROM professional p
JOIN service_offering s ON s.business_id = p.business_id;
