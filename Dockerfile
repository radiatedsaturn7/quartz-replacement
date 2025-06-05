FROM eclipse-temurin:17-jre-alpine
COPY app.jar /app.jar
ENTRYPOINT ["java", "-cp", "/app.jar", "com.quartzkube.runner.JobRunner"]
