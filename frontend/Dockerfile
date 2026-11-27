FROM node:24-alpine AS build
WORKDIR /app
RUN mkdir -p /app/caddy-data /app/caddy-config
RUN npm install --global npm@12.0.2
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM caddyserver/caddy:2-alpine AS caddy

FROM scratch
COPY --from=caddy /usr/bin/caddy /caddy
COPY Caddyfile /etc/caddy/Caddyfile
COPY --from=build /app/caddy-data /data
COPY --from=build /app/caddy-config /config
COPY --from=build /app/dist /srv
EXPOSE 80
ENTRYPOINT ["/caddy", "run", "--config", "/etc/caddy/Caddyfile", "--adapter", "caddyfile"]
