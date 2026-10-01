# Lika web: Next.js standalone server, talking to the Lika API on behalf of the browser.
#
# Runtime configuration (read at startup, not at build time):
#   LIKA_API_URL         URL of the Lika API, as reachable from this container (required)
#   LIKA_SESSION_SECRET  random string of 32+ characters encrypting session cookies (required)
#   PORT                 listening port, 80 by default

FROM node:24-alpine AS deps
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci

FROM node:24-alpine AS build
WORKDIR /app
ENV NEXT_TELEMETRY_DISABLED=1
COPY --from=deps /app/node_modules ./node_modules
COPY . .
RUN npm run build

FROM node:24-alpine
WORKDIR /app
ENV NODE_ENV=production \
    NEXT_TELEMETRY_DISABLED=1 \
    HOSTNAME=0.0.0.0 \
    PORT=80
# Keep port 80, like the former nginx image, while running as the unprivileged node user
RUN apk add --no-cache libcap \
    && setcap cap_net_bind_service=+ep /usr/local/bin/node \
    && apk del libcap
COPY --from=build --chown=node:node /app/.next/standalone ./
COPY --from=build --chown=node:node /app/.next/static ./.next/static
USER node
EXPOSE 80
HEALTHCHECK --interval=30s --timeout=3s CMD wget -qO- "http://127.0.0.1:${PORT}/health" || exit 1
CMD ["node", "server.js"]
