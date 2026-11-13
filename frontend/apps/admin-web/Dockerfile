FROM node:22-bookworm-slim AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN node --version \
 && npm --version \
 && npm ci --no-audit --no-fund \
      --fetch-retries=5 \
      --fetch-retry-mintimeout=20000 \
      --fetch-retry-maxtimeout=120000 \
      --fetch-timeout=300000
COPY . .
ARG VITE_API_BASE_URL=http://localhost:58080/api/v1
ARG VITE_BACKEND=false
ARG VITE_DEMO_MODE=true
ARG VITE_DEMO_EMAIL=admin@suabhi.local
ARG VITE_DEMO_PASSWORD
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL VITE_BACKEND=$VITE_BACKEND VITE_DEMO_MODE=$VITE_DEMO_MODE VITE_DEMO_EMAIL=$VITE_DEMO_EMAIL VITE_DEMO_PASSWORD=$VITE_DEMO_PASSWORD
RUN npm run build

FROM nginx:1.29-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/build /usr/share/nginx/html
EXPOSE 80
HEALTHCHECK --interval=20s --timeout=5s --start-period=10s --retries=5 CMD wget -q -O /dev/null http://127.0.0.1/ || exit 1
