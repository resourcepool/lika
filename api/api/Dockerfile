FROM eclipse-temurin:21-jre-alpine
COPY build/libs/*.jar app.jar
ENTRYPOINT exec java $JAVA_OPTS -jar /app.jar
