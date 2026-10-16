FROM node:24-bookworm-slim AS build
WORKDIR /workspace
COPY backend/contract backend/contract
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
