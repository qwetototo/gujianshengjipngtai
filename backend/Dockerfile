FROM maven:3.9.11-eclipse-temurin-17 AS build
ENV MAVEN_CONFIG=""
WORKDIR /workspace
COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .
RUN chmod +x mvnw && ./mvnw -B -DskipTests dependency:go-offline
COPY src src
RUN ./mvnw -B -DskipTests package

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=build /workspace/target/ota-console-1.0.0.jar /app/app.jar
RUN mkdir -p /var/lib/ota/firmware /tmp/ota-upload && chown -R 10001:10001 /app /var/lib/ota /tmp/ota-upload
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-Djava.io.tmpdir=/tmp", "-jar", "/app/app.jar"]
