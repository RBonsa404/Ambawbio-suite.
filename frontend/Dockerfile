# Application Angular servie par nginx (Railway ou tout hébergeur de conteneurs). Contexte de construction : racine du dépôt.
FROM node:24-alpine AS construction
WORKDIR /depot
COPY docs/design ./docs/design
COPY frontend/package.json frontend/package-lock.json ./frontend/
WORKDIR /depot/frontend
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npm run build

FROM nginx:1.29-alpine
COPY frontend/docker/nginx.conf.template /etc/nginx/templates/default.conf.template
COPY frontend/docker/40-configuration.sh /docker-entrypoint.d/40-configuration.sh
RUN chmod +x /docker-entrypoint.d/40-configuration.sh
COPY --from=construction /depot/frontend/dist/frontend/browser /usr/share/nginx/html
ENV PORT=8080
EXPOSE 8080
