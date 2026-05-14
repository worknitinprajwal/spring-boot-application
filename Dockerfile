FROM eclipse-temurin:21-jre
WORKDIR /app

# Copy pre-built jar from Maven build
COPY target/fitness-tracker-1.0.0.jar app.jar

# Expose port
EXPOSE 8080

# Run application
ENTRYPOINT ["java", "-jar", "app.jar"]
