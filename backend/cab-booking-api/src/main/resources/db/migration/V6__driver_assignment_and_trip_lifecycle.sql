ALTER TABLE driver_profiles
  ADD COLUMN operating_city VARCHAR(100);

CREATE TABLE booking_driver_assignments (
  id UUID PRIMARY KEY,
  booking_id UUID NOT NULL UNIQUE REFERENCES bookings(id) ON DELETE RESTRICT,
  driver_profile_id UUID NOT NULL REFERENCES driver_profiles(id) ON DELETE RESTRICT,
  assigned_by UUID NOT NULL REFERENCES app_users(id) ON DELETE RESTRICT,
  reserved_from_at TIMESTAMPTZ NOT NULL,
  reserved_until_at TIMESTAMPTZ NOT NULL,
  assigned_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  trip_started_at TIMESTAMPTZ,
  trip_completed_at TIMESTAMPTZ,
  CONSTRAINT ck_booking_driver_assignment_window
    CHECK (reserved_until_at > reserved_from_at),
  CONSTRAINT ck_booking_driver_trip_timestamps
    CHECK (
      (trip_completed_at IS NULL OR trip_started_at IS NOT NULL)
      AND (trip_started_at IS NULL OR trip_started_at >= assigned_at)
      AND (trip_completed_at IS NULL OR trip_completed_at >= trip_started_at)
    )
);

CREATE INDEX ix_booking_driver_assignments_driver_window
  ON booking_driver_assignments(driver_profile_id, reserved_from_at, reserved_until_at);

CREATE INDEX ix_booking_driver_assignments_booking
  ON booking_driver_assignments(booking_id, assigned_at DESC);

ALTER TABLE booking_driver_assignments
  ADD CONSTRAINT ex_booking_driver_assignments_no_overlap
  EXCLUDE USING gist (
    driver_profile_id WITH =,
    tstzrange(reserved_from_at, reserved_until_at, '[)') WITH &&
  );

CREATE INDEX ix_driver_profiles_dispatch_eligibility
  ON driver_profiles(operating_city, verification_status, availability_status, license_expiry);

COMMENT ON TABLE booking_driver_assignments IS
  'Driver assignment and trip timestamps. The scheduled interval remains reserved after completion to prevent premature overlapping dispatch.';
