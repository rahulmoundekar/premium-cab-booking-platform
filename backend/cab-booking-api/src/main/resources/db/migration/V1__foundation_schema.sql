CREATE EXTENSION IF NOT EXISTS postgis;

CREATE TABLE roles (
  id UUID PRIMARY KEY, code VARCHAR(60) NOT NULL UNIQUE,
  display_name VARCHAR(120) NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE app_users (
  id UUID PRIMARY KEY, email VARCHAR(254) NOT NULL, phone_e164 VARCHAR(20),
  password_hash VARCHAR(255), full_name VARCHAR(160),
  account_status VARCHAR(30) NOT NULL DEFAULT 'PENDING_VERIFICATION',
  email_verified BOOLEAN NOT NULL DEFAULT FALSE, phone_verified BOOLEAN NOT NULL DEFAULT FALSE,
  last_login_at TIMESTAMPTZ, created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(), created_by VARCHAR(120), updated_by VARCHAR(120),
  CONSTRAINT ck_app_users_status CHECK (account_status IN ('PENDING_VERIFICATION','ACTIVE','LOCKED','SUSPENDED','CLOSED'))
);
CREATE UNIQUE INDEX ux_app_users_email_lower ON app_users (lower(email));
CREATE UNIQUE INDEX ux_app_users_phone ON app_users (phone_e164) WHERE phone_e164 IS NOT NULL;
CREATE TABLE user_roles (
  user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
  role_id UUID NOT NULL REFERENCES roles(id) ON DELETE RESTRICT,
  assigned_at TIMESTAMPTZ NOT NULL DEFAULT now(), PRIMARY KEY (user_id, role_id)
);
CREATE TABLE driver_profiles (
  id UUID PRIMARY KEY, user_id UUID NOT NULL UNIQUE REFERENCES app_users(id) ON DELETE RESTRICT,
  driver_code VARCHAR(40) NOT NULL UNIQUE, license_number VARCHAR(60) NOT NULL,
  license_expiry DATE NOT NULL, verification_status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
  availability_status VARCHAR(30) NOT NULL DEFAULT 'OFFLINE',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by VARCHAR(120), updated_by VARCHAR(120),
  CONSTRAINT ck_driver_verification CHECK (verification_status IN ('PENDING','VERIFIED','REJECTED','SUSPENDED')),
  CONSTRAINT ck_driver_availability CHECK (availability_status IN ('OFFLINE','AVAILABLE','ON_TRIP','ON_BREAK'))
);
CREATE TABLE vehicle_categories (
  id UUID PRIMARY KEY, code VARCHAR(60) NOT NULL UNIQUE, display_name VARCHAR(120) NOT NULL,
  description VARCHAR(500), min_passengers INTEGER NOT NULL, max_passengers INTEGER NOT NULL,
  luggage_pieces INTEGER NOT NULL, luggage_description VARCHAR(160),
  display_order INTEGER NOT NULL DEFAULT 0, active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by VARCHAR(120), updated_by VARCHAR(120),
  CONSTRAINT ck_vehicle_category_capacity CHECK (min_passengers > 0 AND max_passengers >= min_passengers),
  CONSTRAINT ck_vehicle_category_luggage CHECK (luggage_pieces >= 0)
);
CREATE TABLE vehicle_models (
  id UUID PRIMARY KEY, category_id UUID NOT NULL REFERENCES vehicle_categories(id) ON DELETE RESTRICT,
  model_code VARCHAR(80) NOT NULL UNIQUE, manufacturer VARCHAR(100) NOT NULL, model_name VARCHAR(120) NOT NULL,
  fuel_type VARCHAR(30) NOT NULL, min_passengers INTEGER NOT NULL, max_passengers INTEGER NOT NULL,
  luggage_pieces INTEGER NOT NULL, luggage_description VARCHAR(160),
  hybrid BOOLEAN NOT NULL DEFAULT FALSE, active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by VARCHAR(120), updated_by VARCHAR(120),
  CONSTRAINT ck_vehicle_model_capacity CHECK (min_passengers > 0 AND max_passengers >= min_passengers),
  CONSTRAINT ck_vehicle_model_luggage CHECK (luggage_pieces >= 0),
  CONSTRAINT ck_vehicle_model_fuel CHECK (fuel_type IN ('PETROL','DIESEL','CNG','HYBRID','ELECTRIC','OTHER'))
);
CREATE INDEX ix_vehicle_models_category_active ON vehicle_models(category_id, active, model_name);
CREATE TABLE vehicles (
  id UUID PRIMARY KEY, vehicle_number VARCHAR(20) NOT NULL UNIQUE,
  category_id UUID NOT NULL REFERENCES vehicle_categories(id) ON DELETE RESTRICT,
  model_id UUID NOT NULL REFERENCES vehicle_models(id) ON DELETE RESTRICT,
  passenger_capacity INTEGER NOT NULL, luggage_capacity INTEGER NOT NULL,
  manufacture_year SMALLINT, colour VARCHAR(40), operational_city VARCHAR(100) NOT NULL,
  status VARCHAR(30) NOT NULL DEFAULT 'INACTIVE', last_known_location GEOGRAPHY(POINT, 4326),
  last_location_at TIMESTAMPTZ, created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(), created_by VARCHAR(120), updated_by VARCHAR(120),
  CONSTRAINT ck_vehicle_capacity CHECK (passenger_capacity > 0 AND luggage_capacity >= 0),
  CONSTRAINT ck_vehicle_status CHECK (status IN ('INACTIVE','AVAILABLE','RESERVED','ON_TRIP','MAINTENANCE','RETIRED'))
);
CREATE INDEX ix_vehicles_city_status ON vehicles(operational_city, status);
CREATE INDEX ix_vehicles_last_location ON vehicles USING GIST(last_known_location);
CREATE TABLE service_zones (
  id UUID PRIMARY KEY, code VARCHAR(60) NOT NULL UNIQUE, name VARCHAR(120) NOT NULL,
  city VARCHAR(100) NOT NULL, boundary GEOMETRY(MULTIPOLYGON, 4326),
  active BOOLEAN NOT NULL DEFAULT TRUE, created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(), created_by VARCHAR(120), updated_by VARCHAR(120)
);
CREATE INDEX ix_service_zones_boundary ON service_zones USING GIST(boundary);
CREATE TABLE saved_locations (
  id UUID PRIMARY KEY, user_id UUID REFERENCES app_users(id) ON DELETE CASCADE,
  label VARCHAR(80), formatted_address VARCHAR(500) NOT NULL, place_reference VARCHAR(255),
  coordinates GEOGRAPHY(POINT, 4326) NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(), created_by VARCHAR(120), updated_by VARCHAR(120)
);
CREATE INDEX ix_saved_locations_coordinates ON saved_locations USING GIST(coordinates);
CREATE INDEX ix_saved_locations_user ON saved_locations(user_id);
CREATE TABLE audit_logs (
  id UUID PRIMARY KEY, actor_user_id UUID REFERENCES app_users(id) ON DELETE SET NULL,
  action VARCHAR(100) NOT NULL, aggregate_type VARCHAR(100) NOT NULL, aggregate_id VARCHAR(100),
  request_id VARCHAR(100), ip_address INET, user_agent VARCHAR(500),
  before_state JSONB, after_state JSONB, occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_audit_logs_aggregate ON audit_logs(aggregate_type, aggregate_id, occurred_at DESC);
CREATE INDEX ix_audit_logs_actor ON audit_logs(actor_user_id, occurred_at DESC);
CREATE TABLE idempotency_keys (
  id UUID PRIMARY KEY, idempotency_key VARCHAR(160) NOT NULL, scope VARCHAR(120) NOT NULL,
  request_hash VARCHAR(128) NOT NULL, response_status INTEGER, response_body JSONB,
  state VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS', expires_at TIMESTAMPTZ NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ux_idempotency_scope_key UNIQUE(scope, idempotency_key),
  CONSTRAINT ck_idempotency_state CHECK (state IN ('IN_PROGRESS','COMPLETED','FAILED'))
);
CREATE INDEX ix_idempotency_expiry ON idempotency_keys(expires_at);
CREATE TABLE outbox_events (
  id UUID PRIMARY KEY, aggregate_type VARCHAR(100) NOT NULL, aggregate_id VARCHAR(100) NOT NULL,
  event_type VARCHAR(160) NOT NULL, payload JSONB NOT NULL, event_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  attempts INTEGER NOT NULL DEFAULT 0, next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(), published_at TIMESTAMPTZ, last_error VARCHAR(2000),
  CONSTRAINT ck_outbox_status CHECK (event_status IN ('PENDING','PROCESSING','PUBLISHED','FAILED'))
);
CREATE INDEX ix_outbox_pending ON outbox_events(event_status, next_attempt_at, occurred_at);

INSERT INTO roles (id, code, display_name) VALUES
('10000000-0000-0000-0000-000000000001','CUSTOMER','Customer'),
('10000000-0000-0000-0000-000000000002','DRIVER','Driver'),
('10000000-0000-0000-0000-000000000003','FLEET_MANAGER','Fleet Manager'),
('10000000-0000-0000-0000-000000000004','DISPATCHER','Dispatcher'),
('10000000-0000-0000-0000-000000000005','ADMIN','Administrator'),
('10000000-0000-0000-0000-000000000006','SUPER_ADMIN','Super Administrator');

INSERT INTO vehicle_categories
(id, code, display_name, description, min_passengers, max_passengers, luggage_pieces, luggage_description, display_order)
VALUES
('20000000-0000-0000-0000-000000000001','EXECUTIVE_SEDAN','Executive Sedan','Airport transfers and economical city travel.',4,4,2,'2 standard bags',10),
('20000000-0000-0000-0000-000000000002','PREMIUM_EXECUTIVE_SUV','Premium Executive SUV','Family, outstation and corporate journeys.',6,7,4,'4 standard bags',20),
('20000000-0000-0000-0000-000000000003','SUSTAINABLE_LUXURY_HYBRID','Sustainable Luxury Hybrid','Premium hybrid travel with a quiet ride.',6,7,4,'4 standard bags',30),
('20000000-0000-0000-0000-000000000004','LUXURY_GROUP_TRANSIT','Luxury Group Transit','Luxury van with reclining seats and dedicated boot.',9,17,8,'Confirm actual fit-out',40),
('20000000-0000-0000-0000-000000000005','MEGA_GROUP_COACH','Mega Group Coaches','Premium coaches with undercarriage luggage.',32,45,45,'Confirm actual fit-out',50);

INSERT INTO vehicle_models
(id, category_id, model_code, manufacturer, model_name, fuel_type, min_passengers, max_passengers, luggage_pieces, luggage_description, hybrid)
VALUES
('30000000-0000-0000-0000-000000000001','20000000-0000-0000-0000-000000000001','MARUTI_DZIRE','Maruti Suzuki','Dzire','PETROL',4,4,2,'2 standard bags',FALSE),
('30000000-0000-0000-0000-000000000002','20000000-0000-0000-0000-000000000001','TATA_AURA','Tata Motors','Aura','PETROL',4,4,2,'2 standard bags',FALSE),
('30000000-0000-0000-0000-000000000003','20000000-0000-0000-0000-000000000002','TOYOTA_INNOVA','Toyota','Innova','DIESEL',6,7,4,'4 standard bags',FALSE),
('30000000-0000-0000-0000-000000000004','20000000-0000-0000-0000-000000000002','TOYOTA_INNOVA_CRYSTA','Toyota','Innova Crysta','DIESEL',6,7,4,'4 standard bags',FALSE),
('30000000-0000-0000-0000-000000000005','20000000-0000-0000-0000-000000000003','TOYOTA_INNOVA_HYCROSS_HYBRID','Toyota','Innova Hycross Hybrid','HYBRID',6,7,4,'4 standard bags',TRUE),
('30000000-0000-0000-0000-000000000006','20000000-0000-0000-0000-000000000004','FORCE_URBANIA_9','Force Motors','Urbania 9 Seater','DIESEL',9,9,8,'Dedicated boot; confirm fit-out',FALSE),
('30000000-0000-0000-0000-000000000007','20000000-0000-0000-0000-000000000004','FORCE_URBANIA_17','Force Motors','Urbania 17 Seater','DIESEL',17,17,8,'Dedicated boot; confirm fit-out',FALSE),
('30000000-0000-0000-0000-000000000008','20000000-0000-0000-0000-000000000005','PREMIUM_COACH_32','Coach Operator','Premium 32 Seater Coach','DIESEL',32,32,32,'Undercarriage luggage; verify configuration',FALSE),
('30000000-0000-0000-0000-000000000009','20000000-0000-0000-0000-000000000005','PREMIUM_COACH_45','Coach Operator','Premium 45 Seater Coach','DIESEL',45,45,45,'Undercarriage luggage; verify configuration',FALSE);
