# Build stage
FROM node:22-alpine AS builder
WORKDIR /app
COPY sg-transport/package.json sg-transport/package-lock.json ./
RUN node --version && npm --version && npm ci
COPY sg-transport/ ./
RUN npm run build -- --configuration production

# Serve stage
FROM nginx:alpine
COPY --from=builder /app/dist/sg-transport/browser /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
