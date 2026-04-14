# ============================================================
# Frontend Dockerfile - Vue 3 + Vite (Multi-stage build)
# ============================================================

# Stage 1: Build with Node.js
FROM node:20-alpine AS build
WORKDIR /app

# Copy package files first to cache dependencies
COPY package*.json ./
RUN npm ci --prefer-offline

# Copy source and build
COPY . .
RUN npm run build

# Stage 2: Output the build artifacts
# The dist files are copied to a shared volume by docker-compose
FROM alpine:3.19
WORKDIR /build-output

# Copy built files from build stage
COPY --from=build /app/dist .

# Keep container alive briefly so volume mount works
CMD ["sh", "-c", "echo 'Frontend build complete.' && ls -la /build-output/"]