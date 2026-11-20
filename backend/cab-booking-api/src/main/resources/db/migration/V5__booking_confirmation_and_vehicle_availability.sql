CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE bookings
  ADD COLUMN decision_reason VARCHAR(500);

ALTER TABLE bookings
  ADD CONSTRAINT ck_bookings_decision_reason
  CHECK (
    (status = 'REJECTED' AND decision_reason IS NOT NULL AND length(btrim(decision_reason)) >= 5)
    OR (status <> 'REJECTED' AND decision_reason IS NULL)
  );

CREATE TABLE booking_vehicle_allocations (
  id UUID PRIMARY KEY,
  booking_id UUID NOT NULL REFERENCES bookings(id) ON DELETE RESTRICT,
  vehicle_id UUID NOT NULL REFERENCES vehicles(id) ON DELETE RESTRICT,
  reserved_from_at TIMESTAMPTZ NOT NULL,
  reserved_until_at TIMESTAMPTZ NOT NULL,
  allocation_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  allocated_by UUID NOT NULL REFERENCES app_users(id) ON DELETE RESTRICT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  released_at TIMESTAMPTZ,
  CONSTRAINT ck_booking_vehicle_allocation_window
    CHECK (reserved_until_at > reserved_from_at),
  CONSTRAINT ck_booking_vehicle_allocation_status
    CHECK (allocation_status IN ('ACTIVE','RELEASED')),
  CONSTRAINT ck_booking_vehicle_allocation_release
    CHECK (
      (allocation_status = 'ACTIVE' AND released_at IS NULL)
      OR (allocation_status = 'RELEASED' AND released_at IS NOT NULL)
    )
);

CREATE UNIQUE INDEX ux_booking_vehicle_allocations_active_booking
  ON booking_vehicle_allocations(booking_id)
  WHERE allocation_status = 'ACTIVE';

CREATE INDEX ix_booking_vehicle_allocations_booking
  ON booking_vehicle_allocations(booking_id, created_at DESC);

CREATE INDEX ix_booking_vehicle_allocations_vehicle_window
  ON booking_vehicle_allocations(vehicle_id, reserved_from_at, reserved_until_at);

ALTER TABLE booking_vehicle_allocations
  ADD CONSTRAINT ex_booking_vehicle_allocations_no_overlap
  EXCLUDE USING gist (
    vehicle_id WITH =,
    tstzrange(reserved_from_at, reserved_until_at, '[)') WITH &&
  )
  WHERE (allocation_status = 'ACTIVE');

COMMENT ON TABLE booking_vehicle_allocations IS
  'Active vehicle reservations for confirmed bookings. The GiST exclusion constraint prevents overlapping reservations for the same vehicle.';
