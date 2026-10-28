# Stage 1 — Build
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B
COPY src ./src
RUN ./mvnw clean package -DskipTests -B

# Stage 2 — Runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S padilla && adduser -S padilla -G padilla \n    && mkdir -p /app/files && chown padilla:padilla /app/files
# PRO-046: adjuntos en /app/files (montar un volumen en produccion)
VOLUME /app/files
COPY --from=build /app/target/*.jar app.jar
RUN chown padilla:padilla app.jar
USER padilla
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
