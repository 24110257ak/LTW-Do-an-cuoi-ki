# Giai đoạn 1: Đóng gói mã nguồn (Build) bằng Maven và Java 17
FROM maven:3.9.4-eclipse-temurin-17 AS build
WORKDIR /app

# Copy cấu hình thư viện và toàn bộ thư mục src vào container
COPY pom.xml .
COPY src ./src

# Tiến hành tải thư viện và build ra file .jar (bỏ qua bước test để tiết kiệm thời gian)
RUN mvn clean package -DskipTests

# Giai đoạn 2: Khởi chạy ứng dụng bằng môi trường Java siêu nhẹ (Alpine)
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Copy file .jar đã build xong từ Giai đoạn 1 sang Giai đoạn 2
COPY --from=build /app/target/*.jar app.jar

# Khai báo cổng chạy ứng dụng
EXPOSE 8080

# Lệnh khởi động Spring Boot
ENTRYPOINT ["java", "-jar", "app.jar"]