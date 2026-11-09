# Security baseline

- Server-side session authentication foundation; no browser local-storage token design.
- CSRF protection remains enabled for unsafe methods.
- Session identifier rotates on authentication.
- CORS is allow-list based and credentials are enabled only for configured first-party origins.
- HSTS is environment-controlled for trusted HTTPS ingress.
- Frame protection, content-type protection and a restrictive referrer policy are enabled.
- No default administrator account is seeded.
- Audit, outbox and idempotency tables are part of the baseline.

Before production: set secure-cookie and HSTS flags true; configure exact CORS origins; use secret-manager-sourced credentials; restrict backend access to trusted ingress; add endpoint rate limits; perform SAST, dependency/container scanning, DAST and authorization/IDOR testing; verify backup restoration.

Phase 1 does not yet implement login, registration, payment, booking or driver-tracking endpoints.
