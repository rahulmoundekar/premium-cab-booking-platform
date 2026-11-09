# Customer website

The uploaded Rentaly dark rental theme is retained as the primary customer-facing design system, including its CSS, JavaScript, fonts, images and static page templates. Next.js serves the site bundle under `/rentaly`; `/` redirects to the dark homepage. The theme's original HTML pages remain available at `/rentaly/*.html` until each journey is migrated into React components and connected to backend APIs.

The supplied template included a Google Maps JavaScript API URL with an embedded key on booking pages. That key reference was removed. Configure a new provider key through a proper secret/environment strategy when Maps integration is implemented.

Booking and account forms are currently design templates only; they do not create real bookings or process payment.
