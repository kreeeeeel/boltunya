FROM eclipse-temurin:21-jdk AS contract
WORKDIR /workspace
COPY gradle gradle
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY backend backend
RUN chmod +x gradlew && ./gradlew :backend:contract:client:typescript:generateClient --no-daemon

FROM node:24-bookworm-slim AS build
WORKDIR /workspace
COPY --from=contract /workspace/backend/contract/client/typescript/build/generated/openapi/src backend/contract/client/typescript/build/generated/openapi/src
COPY web/package*.json web/
RUN npm ci --prefix web
COPY web web
RUN npm run build:prod --prefix web
FROM node:24-bookworm-slim
WORKDIR /workspace
COPY --from=build /workspace/web/out ./out
COPY --from=build /workspace/web/node_modules/serve ./node_modules/serve
COPY --from=build /workspace/web/node_modules ./node_modules
EXPOSE 3000
CMD ["node", "node_modules/serve/build/main.js", "out", "-l", "3000", "--no-clipboard"]
