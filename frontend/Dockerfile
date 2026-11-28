FROM golang:1.27-alpine AS server-build
WORKDIR /src
COPY frontend/server*.go .
RUN go test server.go server_test.go
RUN CGO_ENABLED=0 go build -trimpath -ldflags="-s -w" -o /server server.go

FROM node:24-alpine AS build
WORKDIR /app
RUN npm install --global npm@12.0.2
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/. .
RUN npm run build

FROM scratch
COPY --from=server-build /server /server
COPY --from=build /app/dist /srv
COPY LICENSE /srv/licenses/LICENSE
EXPOSE 80
ENTRYPOINT ["/server"]
