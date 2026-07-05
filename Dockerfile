FROM eclipse-temurin:21-jdk AS build

WORKDIR /app

COPY . .

RUN ./mvnw clean package -DskipTests


FROM eclipse-temurin:21-jre

WORKDIR /app

ADD https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/latest/download/opentelemetry-javaagent.jar /otel/opentelemetry-javaagent.jar

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8081

# if container runs: Java starts, OpenTelemetry Java Agent starts, Spring Boot starts
# bash: java -javaagent:/otel/opentelemetry-javaagent.jar -jar app.jar
ENTRYPOINT ["java", "-javaagent:/otel/opentelemetry-javaagent.jar", "-jar", "app.jar"]