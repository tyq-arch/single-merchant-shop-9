# ===== 第一阶段：编译打包 =====
# 用官方 Maven + JDK8 镜像，把源码打成 jar，本机不需要装 Maven/JDK
FROM maven:3.9-eclipse-temurin-8 AS build
WORKDIR /build

# 先只拷 pom，单独下载依赖，这样改代码时能复用这一层缓存
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

# 再拷源码并打包（跳过测试，测试在本地 mvn test 里跑）
COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ===== 第二阶段：运行 =====
# 只放 jar 的精简运行时镜像，体积小很多
FROM eclipse-temurin:8-jre
WORKDIR /app

COPY --from=build /build/target/shop-backend-1.0.0.jar /app/app.jar

# 数据目录与上传目录（docker-compose 里会用卷挂载覆盖）
RUN mkdir -p /app/data /app/uploads
ENV SHOP_DB_PATH=/app/data/shop-v2.db \
    SHOP_UPLOAD_DIR=/app/uploads \
    TZ=Asia/Shanghai

EXPOSE 8000
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
