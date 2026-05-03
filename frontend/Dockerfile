# syntax=docker/dockerfile:1.6

# ---- Build stage ----
FROM node:20-alpine AS build
WORKDIR /workspace

# Install pnpm matching packageManager in package.json
RUN corepack enable && corepack prepare pnpm@9.4.0 --activate

# Copy lockfile first to cache install layer
COPY package.json pnpm-lock.yaml .npmrc ./
RUN pnpm install --frozen-lockfile

# Copy sources and build
COPY . .
RUN pnpm build

# ---- Runtime stage ----
FROM nginx:1.27-alpine
COPY --from=build /workspace/dist/ /usr/share/nginx/html/
COPY nginx-frontend.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

HEALTHCHECK --interval=15s --timeout=3s --start-period=5s --retries=3 \
    CMD wget -qO- http://localhost/ > /dev/null || exit 1
