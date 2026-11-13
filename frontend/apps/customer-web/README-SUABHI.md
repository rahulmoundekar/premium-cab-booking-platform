# Suabhi customer website

The customer site now uses an original Suabhi-owned Next.js interface. It does not ship the copied Rentaly / Designesia demo pages, vendor JavaScript, demo imagery, font files or template CSS.

## Phase 2 integration

- Registration: POST /api/v1/auth/register
- Login: POST /api/v1/auth/login
- Current identity: GET /api/v1/auth/me
- Logout: POST /api/v1/auth/logout
- Profile: GET and PATCH /api/v1/customer/profile

Browser writes first request a CSRF token, then send it in X-XSRF-TOKEN with credentials included. The API base URL is compiled into the client bundle through NEXT_PUBLIC_API_BASE_URL at build time. API_INTERNAL_URL is used only for server-side fleet catalogue fetching.

The fleet catalogue shown on the home page comes from the public backend API. Booking, fare calculation, payment and email verification are not yet enabled.

## Original implementation and assets

The current site uses system fonts, CSS-generated decorative elements and text content created for Suabhi. No images, fonts, JavaScript bundles, HTML pages or styles from the supplied HTTrack mirror are included.
