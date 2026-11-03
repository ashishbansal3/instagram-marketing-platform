FROM node:22-alpine
WORKDIR /app
RUN apk add --no-cache ffmpeg wget
COPY apps/remotion-service/package.json apps/remotion-service/package-lock.json ./
RUN npm ci --omit=dev
COPY apps/remotion-service/ ./
EXPOSE 3100
CMD ["npm", "start"]
