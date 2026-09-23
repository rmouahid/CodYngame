# Sandbox image used to compile and run user submissions (see utils.Sandbox).
#
#   docker build -t codyngame-sandbox:latest sandbox/
#
# It only provides the toolchains of the supported languages. Isolation
# (no network, read-only filesystem, no capabilities, non-root user,
# resource limits) is enforced by the `docker run` options, not by the image.
FROM alpine:3.22

RUN apk add --no-cache \
        gcc musl-dev \
        openjdk21-jdk \
        python3 \
        nodejs \
        php84 \
    && ln -s /usr/bin/php84 /usr/local/bin/php

ENV JAVA_HOME=/usr/lib/jvm/java-21-openjdk \
    PATH="/usr/lib/jvm/java-21-openjdk/bin:${PATH}" \
    PYTHONDONTWRITEBYTECODE=1

USER 65534:65534
WORKDIR /sandbox
