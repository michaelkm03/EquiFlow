FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/surge-simulator-1.0.0-SNAPSHOT.jar app.jar
EXPOSE 8089
ENTRYPOINT ["java", "--enable-preview", "-jar", "app.jar"]
