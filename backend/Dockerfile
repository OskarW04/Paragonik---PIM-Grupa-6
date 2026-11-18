FROM eclipse-temurin:21-jdk AS build
WORKDIR /src

COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew \
    && ./gradlew --no-daemon --quiet dependencies > /dev/null

COPY src ./src
RUN ./gradlew --no-daemon -Pkotlin.compiler.execution.strategy=in-process installDist -x test \
    && chmod -R a+rX build/install

FROM eclipse-temurin:21-jre
RUN groupadd --system app && useradd --system --gid app --home-dir /app app
WORKDIR /app
COPY --from=build /src/build/install/paragonik-backend/ ./
USER app

ENV PORT=8080 \
    JAVA_OPTS="-Xmx256m -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
ENTRYPOINT ["/app/bin/paragonik-backend"]
