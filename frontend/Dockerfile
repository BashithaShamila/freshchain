# syntax=docker/dockerfile:1
# Node exists only to build; the shipped image is nginx plus static files.

FROM node:22-alpine AS build
WORKDIR /app
# Dependencies are installed from the lockfile alone, so a source-only change
# reuses this layer instead of reinstalling everything — and the build fails
# rather than silently floating versions if the lockfile is ever missing.
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY tsconfig.json vite.config.ts index.html ./
COPY public ./public
COPY src ./src
RUN npm run build

FROM nginx:1.27-alpine AS runtime
RUN rm /etc/nginx/conf.d/default.conf
COPY nginx.conf /etc/nginx/conf.d/freshchain.conf
COPY --from=build /app/dist /usr/share/nginx/html
COPY docker-entrypoint.sh /docker-entrypoint-freshchain.sh
RUN chmod +x /docker-entrypoint-freshchain.sh
EXPOSE 8080
ENTRYPOINT ["/docker-entrypoint-freshchain.sh"]
CMD ["nginx", "-g", "daemon off;"]
