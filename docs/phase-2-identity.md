# Phase 2 — Customer identity and profile

This increment introduces customer email/password registration, session-based login/logout, CSRF protection, and customer profile read/update APIs.

## API

| Method | Endpoint | Access | Purpose |
|---|---|---|---|
| GET | /api/v1/auth/csrf | Public | Obtain the CSRF request token and XSRF-TOKEN cookie |
| POST | /api/v1/auth/register | Public + CSRF | Create a customer account |
| POST | /api/v1/auth/login | Public + CSRF | Authenticate and create a server-side HTTP session |
| GET | /api/v1/auth/me | Authenticated | Read the current signed-in identity |
| POST | /api/v1/auth/logout | Authenticated + CSRF | Invalidate the session |
| GET | /api/v1/customer/profile | Customer | Read customer profile |
| PATCH | /api/v1/customer/profile | Customer + CSRF | Update full name and E.164 phone number |

## Security decisions

- Passwords are stored as BCrypt hashes with work factor 12. Plain-text passwords are never stored or returned.
- Registration requires a password of at least 12 characters; BCrypt input is also limited to 72 UTF-8 bytes.
- Login uses a server-side HTTP session; the session ID is changed after successful authentication.
- The CSRF cookie is not HttpOnly so the browser application can read the request token. Mutating requests send the token in the X-XSRF-TOKEN header.
- Session and CSRF cookies are SameSite=Lax. Secure is controlled by the existing environment setting. Local HTTP development keeps Secure=false; production must use HTTPS and Secure=true.
- Customer endpoints require ROLE_CUSTOMER. Registered accounts are active but emailVerified=false; email verification is a follow-on step and must be connected to a real mail/OTP provider before production.
- The current session repository is the servlet container HTTP session. Shared Redis-backed sessions, rate limiting, account recovery, email verification and MFA are not part of this increment.

## Registration example

Fetch the CSRF token first and retain the cookies returned by the API. Send the returned token in the X-XSRF-TOKEN header on registration, login, profile updates and logout. The customer web application implements this using browser credentials.

Example request:

{
  "email": "customer@example.com",
  "fullName": "Sample Customer",
  "phoneE164": "+919876543210",
  "password": "Use-A-Unique-12-Char-Password"
}

Do not use real credentials in screenshots, logs, or shared test transcripts.
