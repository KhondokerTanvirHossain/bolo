# The jar is built outside Docker (./gradlew bootJar): bytecode is platform-neutral, so the amd64 image needs no
# emulated compile on an arm64 laptop. scripts/deploy.sh does both steps.
FROM eclipse-temurin:21-jre
# Azure Speech's Linux native library needs OpenSSL 3 and ALSA (it loads libasound even with no sound device).
RUN apt-get update && apt-get install -y --no-install-recommends libssl3t64 libasound2t64 ca-certificates \
    && rm -rf /var/lib/apt/lists/*
RUN useradd --system --uid 10001 bolo && mkdir -p /data/media && chown bolo /data/media
WORKDIR /app
COPY build/libs/bolo-api.jar /app/bolo-api.jar
USER bolo
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "-jar", "/app/bolo-api.jar"]
