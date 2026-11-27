FROM node:24-alpine AS build
WORKDIR /app
RUN npm install --global npm@12.0.2
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:stable
RUN apt-get update \
    && apt-get upgrade -y \
    && rm -rf /var/lib/apt/lists/*
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
