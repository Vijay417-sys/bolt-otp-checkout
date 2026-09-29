# syntax=docker/dockerfile:1

# ---------------------------------------------------------------------------
# Stage 1 - build the Vite bundle
# ---------------------------------------------------------------------------
FROM node:22-alpine AS build
WORKDIR /app

# VITE_* values are inlined at build time, so this ARG must be supplied.
ARG VITE_API_BASE_URL
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL

# Install dependencies first so the layer is cached across code changes.
COPY package.json package-lock.json* ./
RUN npm ci || npm install

COPY . .
RUN npm run build

# ---------------------------------------------------------------------------
# Stage 2 - serve the static bundle with nginx
# ---------------------------------------------------------------------------
FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
# nginx.conf `include`s this path, so the file has to exist in the image or the
# server refuses to start.
COPY security-headers.conf /etc/nginx/snippets/security-headers.conf

# Fail the build rather than the deploy if the config is malformed or the include
# path is wrong - a broken `include` is otherwise only discovered at container start.
RUN nginx -t

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
