FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY src src
RUN mvn -B verify

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S activation && adduser -S activation -G activation
WORKDIR /app
COPY --from=build --chown=activation:activation /build/target/product-activation-1.0.0.jar app.jar
USER activation
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=30s CMD wget -q -O /dev/null http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
