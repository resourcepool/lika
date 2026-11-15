# Lika API, built from the sources: no JDK or prebuilt jar needed on the host (e.g. Dokploy).
#   docker build -t lika-api api                         # SQLite (default)
#   docker build -t lika-api --build-arg DB=mongo api    # MongoDB
#
# Runtime configuration: environment variables, see docs/CONFIGURATION.md.
# SQLite stores its file in /data: mount a volume there.

FROM eclipse-temurin:21-jdk AS build
# sqlite, mongo or memory (data lost on restart)
ARG DB=sqlite
WORKDIR /src
COPY . .
RUN --mount=type=cache,target=/root/.gradle \
    case "$DB" in \
      sqlite) FLAG=-PdbSqlite ;; \
      mongo) FLAG=-PdbMongo ;; \
      memory) FLAG= ;; \
      *) echo "DB must be sqlite, mongo or memory" >&2; exit 1 ;; \
    esac \
    && chmod +x gradlew \
    && ./gradlew --no-daemon :api:bootJar $FLAG \
    && cp api/build/libs/api.jar /app.jar

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S lika && adduser -S lika -G lika \
    && mkdir /data && chown lika:lika /data
COPY --from=build /app.jar /app/app.jar
USER lika
ENV DB_SQLITE_PATH=/data/lika.db \
    JAVA_OPTS="-XX:MaxRAMPercentage=75"
VOLUME /data
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -qO- http://127.0.0.1:8080/actuator/health/liveness || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
