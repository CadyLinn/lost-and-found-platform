# ---- Build stage: compile the React app into static files ----
FROM node:24-alpine AS build
WORKDIR /app

# Install dependencies first (cached unless package files change).
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund

COPY . .
RUN npm run build

# ---- Runtime stage: nginx serves the files and forwards /api to the backend ----
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
