# 多阶段构建：Maven 构建 fat jar → JRE 运行
# 镜像与配置分离：所有连接串/密钥通过环境变量或 .env 注入，参见 application.yml 的 ${VAR:default}

# ---------- 构建阶段 ----------
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build

# 先复制 pom，利用 Docker 层缓存加速依赖下载
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

# 复制源码并构建（跳过测试，测试在 CI 阶段执行）
COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ---------- 运行阶段 ----------
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# 从构建阶段复制 fat jar
COPY --from=builder /build/target/*.jar app.jar

# 容器内默认 dev profile；docker-compose 通过 SPRING_PROFILES_ACTIVE 覆盖
ENV SPRING_PROFILES_ACTIVE=dev \
    JAVA_OPTS="-Xms256m -Xmx768m"

EXPOSE 8080

# 通过 SPRING_PROFILES_ACTIVE、DB_URL、REDIS_URL、MINIO_ENDPOINT 等环境变量覆盖默认值
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
