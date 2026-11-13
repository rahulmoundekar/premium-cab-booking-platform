CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE pricing_rate_cards (
  id UUID PRIMARY KEY,
  code VARCHAR(80) NOT NULL,
  version INTEGER NOT NULL,
  service_type VARCHAR(40) NOT NULL,
  operating_city VARCHAR(20) NOT NULL,
  vehicle_category_id UUID NOT NULL REFERENCES vehicle_categories(id) ON DELETE RESTRICT,
  currency CHAR(3) NOT NULL DEFAULT 'INR',
  base_fare NUMERIC(14,2) NOT NULL,
  minimum_fare NUMERIC(14,2) NOT NULL,
  per_km_rate NUMERIC(14,4) NOT NULL,
  per_hour_rate NUMERIC(14,4) NOT NULL,
  per_day_rate NUMERIC(14,2) NOT NULL,
  included_kilometers NUMERIC(12,3) NOT NULL DEFAULT 0,
  included_hours NUMERIC(10,2) NOT NULL DEFAULT 0,
  driver_allowance_per_day NUMERIC(14,2) NOT NULL DEFAULT 0,
  effective_from TIMESTAMPTZ NOT NULL,
  effective_until TIMESTAMPTZ,
  active BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by VARCHAR(120),
  updated_by VARCHAR(120),
  CONSTRAINT ux_pricing_rate_card_code_version UNIQUE (code, version),
  CONSTRAINT ux_pricing_rate_card_scope_version
    UNIQUE (service_type, operating_city, vehicle_category_id, version),
  CONSTRAINT ck_pricing_rate_card_version CHECK (version > 0),
  CONSTRAINT ck_pricing_rate_card_service
    CHECK (service_type IN ('AIRPORT_TRANSFER','LOCAL_RENTAL','OUTSTATION')),
  CONSTRAINT ck_pricing_rate_card_city CHECK (operating_city IN ('PUNE','MUMBAI')),
  CONSTRAINT ck_pricing_rate_card_currency CHECK (currency ~ '^[A-Z]{3}$'),
  CONSTRAINT ck_pricing_rate_card_amounts CHECK (
    base_fare >= 0 AND minimum_fare >= 0 AND per_km_rate >= 0
    AND per_hour_rate >= 0 AND per_day_rate >= 0
    AND included_kilometers >= 0 AND included_hours >= 0
    AND driver_allowance_per_day >= 0
  ),
  CONSTRAINT ck_pricing_rate_card_effective_range
    CHECK (effective_until IS NULL OR effective_until > effective_from)
);

CREATE INDEX ix_pricing_rate_cards_lookup
  ON pricing_rate_cards(service_type, operating_city, vehicle_category_id, active, effective_from, effective_until);

CREATE TABLE pricing_rate_card_components (
  id UUID PRIMARY KEY,
  rate_card_id UUID NOT NULL REFERENCES pricing_rate_cards(id) ON DELETE CASCADE,
  component_code VARCHAR(60) NOT NULL,
  display_name VARCHAR(120) NOT NULL,
  component_type VARCHAR(20) NOT NULL,
  calculation_type VARCHAR(20) NOT NULL,
  amount NUMERIC(14,4) NOT NULL,
  taxable BOOLEAN NOT NULL DEFAULT FALSE,
  display_order INTEGER NOT NULL DEFAULT 0,
  CONSTRAINT ux_pricing_rate_card_component_code UNIQUE (rate_card_id, component_code),
  CONSTRAINT ck_pricing_component_type CHECK (component_type IN ('SURCHARGE','TAX')),
  CONSTRAINT ck_pricing_component_calculation CHECK (calculation_type IN ('FIXED','PERCENTAGE')),
  CONSTRAINT ck_pricing_component_amount CHECK (amount >= 0),
  CONSTRAINT ck_pricing_component_taxable CHECK (component_type = 'SURCHARGE' OR taxable = FALSE)
);

CREATE INDEX ix_pricing_rate_card_components_order
  ON pricing_rate_card_components(rate_card_id, display_order, component_code);

-- No commercial rate cards are seeded. Rates must be supplied and approved by the business.

-- Protect activation against concurrent overlapping rate-card writes as well as service-level checks.
ALTER TABLE pricing_rate_cards
  ADD CONSTRAINT ex_pricing_rate_cards_active_period
  EXCLUDE USING GIST (
    service_type WITH =,
    operating_city WITH =,
    vehicle_category_id WITH =,
    tstzrange(effective_from, effective_until, '[)') WITH &&
  )
  WHERE (active);
