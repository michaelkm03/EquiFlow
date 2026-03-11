FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/ledger-service-1.0.0-SNAPSHOT.jar app.jar
EXPOSE 8085
ENTRYPOINT ["java", "--enable-preview", "-jar", "app.jar"]
