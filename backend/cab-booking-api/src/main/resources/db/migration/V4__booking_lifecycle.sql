CREATE TABLE bookings (
  id UUID PRIMARY KEY,
  booking_reference VARCHAR(40) NOT NULL UNIQUE,
  customer_id UUID NOT NULL REFERENCES app_users(id) ON DELETE RESTRICT,
  quote_id UUID NOT NULL UNIQUE REFERENCES fare_quotes(id) ON DELETE RESTRICT,
  idempotency_key VARCHAR(160) NOT NULL,
  request_hash VARCHAR(128) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING_CONFIRMATION',
  service_type VARCHAR(40) NOT NULL,
  operating_city VARCHAR(20) NOT NULL,
  vehicle_category_code VARCHAR(60) NOT NULL,
  departure_at TIMESTAMPTZ NOT NULL,
  currency CHAR(3) NOT NULL,
  total_amount NUMERIC(14,2) NOT NULL,
  quote_snapshot JSONB NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  cancelled_at TIMESTAMPTZ,
  CONSTRAINT ux_bookings_customer_idempotency UNIQUE (customer_id, idempotency_key),
  CONSTRAINT ck_bookings_status CHECK (
    status IN ('PENDING_CONFIRMATION','CONFIRMED','REJECTED','CANCELLED',
               'ASSIGNED','IN_PROGRESS','COMPLETED')
  ),
  CONSTRAINT ck_bookings_service CHECK (
    service_type IN ('AIRPORT_TRANSFER','LOCAL_RENTAL','OUTSTATION')
  ),
  CONSTRAINT ck_bookings_city CHECK (operating_city IN ('PUNE','MUMBAI')),
  CONSTRAINT ck_bookings_amount CHECK (total_amount >= 0),
  CONSTRAINT ck_bookings_cancelled_timestamp CHECK (
    (status = 'CANCELLED' AND cancelled_at IS NOT NULL)
    OR (status <> 'CANCELLED' AND cancelled_at IS NULL)
  )
);

CREATE INDEX ix_bookings_customer_created
  ON bookings(customer_id, created_at DESC);

CREATE INDEX ix_bookings_status_departure
  ON bookings(status, departure_at);

CREATE INDEX ix_bookings_quote
  ON bookings(quote_id);

CREATE TABLE booking_events (
  id UUID PRIMARY KEY,
  booking_id UUID NOT NULL REFERENCES bookings(id) ON DELETE RESTRICT,
  event_type VARCHAR(80) NOT NULL,
  from_status VARCHAR(32),
  to_status VARCHAR(32) NOT NULL,
  actor_user_id UUID REFERENCES app_users(id) ON DELETE SET NULL,
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ck_booking_event_to_status CHECK (
    to_status IN ('PENDING_CONFIRMATION','CONFIRMED','REJECTED','CANCELLED',
                  'ASSIGNED','IN_PROGRESS','COMPLETED')
  ),
  CONSTRAINT ck_booking_event_from_status CHECK (
    from_status IS NULL OR from_status IN (
      'PENDING_CONFIRMATION','CONFIRMED','REJECTED','CANCELLED',
      'ASSIGNED','IN_PROGRESS','COMPLETED'
    )
  )
);

CREATE INDEX ix_booking_events_booking_time
  ON booking_events(booking_id, occurred_at, id);

COMMENT ON TABLE bookings IS
  'Customer bookings created only from owned, unexpired fare quotes. Quote snapshot is immutable.';
COMMENT ON TABLE booking_events IS
  'Append-only booking lifecycle events written in the same transaction as each state change.';
