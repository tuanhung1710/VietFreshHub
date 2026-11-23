# Giai đoạn 1: build ứng dụng
FROM eclipse-temurin:21-jdk-jammy AS build

WORKDIR /workspace

COPY mvnw pom.xml ./
COPY .mvn/ .mvn/

RUN sed -i 's/\r$//' mvnw

COPY src/ src/

RUN sh ./mvnw -B -ntp package

# Giai đoạn 2: chạy ứng dụng
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

COPY --from=build /workspace/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]