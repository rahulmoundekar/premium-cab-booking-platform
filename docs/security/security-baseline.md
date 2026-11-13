# Security baseline

## Implemented in the current Phase 1 + Phase 2 branch

- Session-based customer authentication; no browser local-storage bearer-token design.
- BCrypt password hashing (work factor 12).
- Session identifier rotation on successful login.
- CSRF protection for unsafe HTTP methods using the `SUABHI-XSRF-TOKEN` cookie and `X-XSRF-TOKEN` request header.
- Customer endpoints require `ROLE_CUSTOMER`.
- CORS uses an explicit allowed-origin list and credentialed requests.
- HSTS is environment-controlled; frame protection, content-type protection and a restrictive referrer policy are enabled.
- Audit, outbox and idempotency tables are part of the database foundation.
- Admin demo login is local-preview functionality only, not a security boundary. The demo credential is bundled into the Vite JavaScript.

## Not yet implemented

- Email verification and account recovery.
- Login and registration rate limiting / lockout controls.
- MFA.
- Redis-backed shared sessions.
- Admin authentication, role-based admin authorization and API-backed admin workflows.
- Booking, payment and driver-tracking authorization rules.

## Production requirements

Before production: use HTTPS; set session and CSRF cookie Secure flags and HSTS appropriately; configure exact production CORS origins; source credentials from a secret manager; disable admin demo mode; implement real admin authentication and authorization; add endpoint rate limits; restrict backend access to trusted ingress; run SAST, dependency/container scanning, DAST and authorization/IDOR tests; and verify backup restoration.

The local manual validation performed for Phase 2 confirmed registration, duplicate-email rejection, login, session persistence, profile read/update, logout, protected-profile rejection and missing-CSRF rejection. Automated CI results have not been reported for this branch; run the Jenkins pipeline or Maven verification before production.
