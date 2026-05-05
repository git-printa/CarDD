FROM maven:3.9.9-eclipse-temurin-11 AS build
WORKDIR /app

COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:11-jre
WORKDIR /app

ENV SERVER_PORT=8080 \
    UPLOAD_DIR=/data/uploads \
    AI_SERVICE_URL=http://ai-service:8000

COPY --from=build /app/target/backend-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

CMD ["java", "-jar", "/app/app.jar"]
