FROM golang:1.27-alpine AS server-build
WORKDIR /src
COPY frontend/server*.go .
RUN go test server.go server_test.go
RUN CGO_ENABLED=0 go build -trimpath -ldflags="-s -w" -o /server server.go

FROM node:24-alpine AS build
WORKDIR /app
COPY frontend/package*.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/index.html frontend/vite.config.ts frontend/tsconfig.json frontend/components.json frontend/beautiful-ui.sources.json ./
COPY frontend/src ./src
COPY frontend/public ./public
COPY frontend/scripts ./scripts
COPY frontend/tokens ./tokens
RUN npm run build

FROM scratch
COPY --from=server-build /server /server
COPY --from=build /app/dist /srv
COPY LICENSE /srv/licenses/LICENSE
EXPOSE 80
ENTRYPOINT ["/server"]
