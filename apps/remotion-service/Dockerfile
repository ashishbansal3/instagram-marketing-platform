FROM node:22-alpine
WORKDIR /app
COPY apps/remotion-service/package.json ./
RUN npm install --omit=dev
COPY apps/remotion-service/ ./
EXPOSE 3100
CMD ["npm", "start"]
