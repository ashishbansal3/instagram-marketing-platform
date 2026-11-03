FROM node:22-alpine AS build
WORKDIR /app
RUN apk add --no-cache ffmpeg
COPY apps/remotion-service/package.json apps/remotion-service/package-lock.json ./
# Need devDeps (typescript/tsx) for compile; prune later in runtime image.
RUN npm ci
COPY apps/remotion-service/ ./
RUN npm run build

FROM node:22-alpine
WORKDIR /app
RUN apk add --no-cache ffmpeg wget
COPY apps/remotion-service/package.json apps/remotion-service/package-lock.json ./
RUN npm ci --omit=dev
COPY --from=build /app/dist ./dist
# Remotion bundler may fall back to TS entry; fonts served from /public.
COPY apps/remotion-service/src ./src
COPY apps/remotion-service/public ./public
COPY apps/remotion-service/tsconfig.json ./tsconfig.json
EXPOSE 3100
CMD ["node", "dist/server.js"]
