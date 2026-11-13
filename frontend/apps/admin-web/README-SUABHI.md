# Suabhi Cab Booking — Operations Console

The supplied React Material Admin template is the primary admin UI foundation. It remains a React 19 + Vite + Material UI + React Router app, preserving the template design system, components, charts, calendar, tables, maps and form components.

The sidebar is now organized around cab operations: dashboard, bookings, dispatch/trips, fleet management, drivers, customers, pricing, payments/refunds, corporate accounts, service areas, reports, system settings and profile.

Run with Docker Compose from the repository root. Local URL: http://localhost:53001. The local preview login is `admin@suabhi.local` / `local-preview-only`. This credential is compiled into the Vite bundle and is not a secret; only use this demo with the service bound to loopback.

Real authentication, role-based API authorization and data-backed operations are not yet implemented. Disable demo mode and integrate the identity API before exposing the console to a network. The original upstream MIT license remains in `LICENSE.txt`.
