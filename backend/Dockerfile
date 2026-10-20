# 多阶段构建：Maven 编译 → JRE 运行
FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /app

# 先复制父 POM 和模块 POM，利用 Docker 缓存层
COPY pom.xml .
COPY runningerrands-common/pom.xml runningerrands-common/
COPY runningerrands-model/pom.xml runningerrands-model/
COPY runningerrands-server/pom.xml runningerrands-server/

# 下载依赖（此层仅 POM 变化时重建）
RUN mvn dependency:go-offline -q -DskipTests 2>/dev/null || true

# 复制源码
COPY runningerrands-common/src runningerrands-common/src
COPY runningerrands-model/src runningerrands-model/src
COPY runningerrands-server/src runningerrands-server/src

# 编译，跳过测试
RUN mvn package -q -DskipTests -pl runningerrands-server -am

# 运行阶段
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# 非 root 用户
RUN addgroup -S app && adduser -S app -G app
USER app

COPY --from=build /app/runningerrands-server/target/*.jar app.jar

EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -qO- http://localhost:8080/api/actuator/health 2>/dev/null || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
