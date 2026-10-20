# Build frontend and backend together so a fresh clone has current UI, not
# stale vendor-committed dist files from upstream.
FROM node:22-alpine AS admin-builder
WORKDIR /workspace/admin
COPY admin/package*.json ./
RUN npm ci
COPY admin/ ./
RUN npm run build

FROM maven:3.9-eclipse-temurin-21-alpine AS backend-builder
WORKDIR /workspace
COPY backend/ backend/
RUN rm -rf backend/runningerrands-server/src/main/resources/static
COPY --from=admin-builder /workspace/admin/dist/ backend/runningerrands-server/src/main/resources/static/
RUN mvn -B -ntp -f backend/pom.xml -DskipTests -pl runningerrands-server -am package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY --from=backend-builder /workspace/backend/runningerrands-server/target/*.jar /app/app.jar
USER app
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 CMD wget -qO- http://127.0.0.1:8080/api/actuator/health >/dev/null || exit 1
ENTRYPOINT ["java","-jar","/app/app.jar"]
