# 單階段：先在 IntelliJ Maven package 出 jar，再 docker compose build
# base image 請對齊你其它服務（furnace-service）的版本
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/*.jar app.jar
EXPOSE 8093
ENTRYPOINT ["java", "-jar", "app.jar"]
