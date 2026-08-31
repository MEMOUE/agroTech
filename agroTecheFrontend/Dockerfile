# Étape 1 : Build
FROM node:22-slim AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

# Étape 2 : Exécution (serveur SSR Express)
FROM node:22-slim
WORKDIR /app
ENV PORT=4200
EXPOSE 4200
COPY --from=build /app/dist/agroTecheFrontend ./dist/agroTecheFrontend
ENTRYPOINT ["node", "dist/agroTecheFrontend/server/server.mjs"]
