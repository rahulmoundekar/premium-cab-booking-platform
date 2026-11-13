CREATE TABLE fare_quotes (
  id UUID PRIMARY KEY,
  customer_id UUID NOT NULL REFERENCES app_users(id) ON DELETE RESTRICT,
  rate_card_id UUID NOT NULL REFERENCES pricing_rate_cards(id) ON DELETE RESTRICT,
  rate_card_code VARCHAR(80) NOT NULL,
  rate_card_version INTEGER NOT NULL,
  service_type VARCHAR(40) NOT NULL,
  operating_city VARCHAR(20) NOT NULL,
  vehicle_category_id UUID NOT NULL REFERENCES vehicle_categories(id) ON DELETE RESTRICT,
  vehicle_category_code VARCHAR(60) NOT NULL,
  departure_at TIMESTAMPTZ NOT NULL,
  rental_hours INTEGER,
  currency CHAR(3) NOT NULL,
  distance_meters BIGINT NOT NULL,
  duration_seconds BIGINT NOT NULL,
  total_amount NUMERIC(14,2) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL,
  response_snapshot JSONB NOT NULL,
  CONSTRAINT ck_fare_quote_rate_version CHECK (rate_card_version > 0),
  CONSTRAINT ck_fare_quote_service
    CHECK (service_type IN ('AIRPORT_TRANSFER','LOCAL_RENTAL','OUTSTATION')),
  CONSTRAINT ck_fare_quote_city CHECK (operating_city IN ('PUNE','MUMBAI')),
  CONSTRAINT ck_fare_quote_currency CHECK (currency ~ '^[A-Z]{3}$'),
  CONSTRAINT ck_fare_quote_route_metrics CHECK (distance_meters > 0 AND duration_seconds > 0),
  CONSTRAINT ck_fare_quote_rental_hours CHECK (
    (service_type = 'LOCAL_RENTAL' AND rental_hours > 0)
    OR (service_type <> 'LOCAL_RENTAL' AND rental_hours IS NULL)
  ),
  CONSTRAINT ck_fare_quote_total CHECK (total_amount >= 0),
  CONSTRAINT ck_fare_quote_expiry CHECK (expires_at > created_at)
);

CREATE INDEX ix_fare_quotes_customer_created
  ON fare_quotes(customer_id, created_at DESC);

CREATE INDEX ix_fare_quotes_expiry
  ON fare_quotes(expires_at);

CREATE INDEX ix_fare_quotes_rate_card
  ON fare_quotes(rate_card_id, created_at DESC);

COMMENT ON TABLE fare_quotes IS
  'Immutable customer fare quote snapshots. response_snapshot preserves journey, tariff and fare details.';
