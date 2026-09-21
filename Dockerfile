FROM maven:3.9-eclipse-temurin-8 AS builder
WORKDIR /build
COPY pom.xml .
# 先预下载依赖(利用Docker层缓存, 代码变更时不必重新下载)
RUN mvn -B dependency:go-offline -DskipTests || true
COPY src ./src
RUN mvn -B clean package -DskipTests

FROM eclipse-temurin:8-jre
WORKDIR /app
COPY --from=builder /build/target/seckill-1.0.0.jar app.jar
EXPOSE 8080
ENV JAVA_OPTS="-Xms256m -Xmx512m"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
