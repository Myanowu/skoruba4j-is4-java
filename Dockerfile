# syntax=docker/dockerfile:1
# Public demo image: skoruba4j-sts and skoruba4j-admin on bundled SQLite.
# No production hosts, client secrets, or keystores. See docs/demo.md.

FROM eclipse-temurin:21.0.12_8-jdk-jammy AS build
WORKDIR /src
RUN --mount=type=cache,target=/var/cache/apt,sharing=locked \
    --mount=type=cache,target=/var/lib/apt,sharing=locked \
    apt-get update \
    && apt-get install -y --no-install-recommends curl unzip ca-certificates git
COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY skoruba4j-domain/pom.xml skoruba4j-domain/pom.xml
COPY skoruba4j-protocol/pom.xml skoruba4j-protocol/pom.xml
COPY skoruba4j-sts/pom.xml skoruba4j-sts/pom.xml
COPY skoruba4j-admin/pom.xml skoruba4j-admin/pom.xml
COPY skoruba4j-admin-api/pom.xml skoruba4j-admin-api/pom.xml
COPY skoruba4j-console/pom.xml skoruba4j-console/pom.xml
# Public snapshot. Not on Maven Central. Pin matches docs/demo.md.
ARG JSQLITE_REV=90f7ede35801a60cd3ea9e206a03d5cf68b35c14
RUN --mount=type=cache,target=/root/.m2 \
    chmod +x mvnw \
    && git init /tmp/jsqlite \
    && git -C /tmp/jsqlite remote add origin https://github.com/Myanowu/jsqlite.git \
    && git -C /tmp/jsqlite fetch --depth 1 origin ${JSQLITE_REV} \
    && git -C /tmp/jsqlite checkout --detach FETCH_HEAD \
    && ./mvnw -f /tmp/jsqlite/pom.xml -B -DskipTests install \
    && rm -rf /tmp/jsqlite \
    && ./mvnw -B -pl skoruba4j-sts,skoruba4j-admin -am dependency:go-offline
COPY skoruba4j-domain skoruba4j-domain
COPY skoruba4j-protocol skoruba4j-protocol
COPY skoruba4j-sts skoruba4j-sts
COPY skoruba4j-admin skoruba4j-admin
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -pl skoruba4j-sts,skoruba4j-admin -am -DskipTests package \
    && mkdir -p /out \
    && cp skoruba4j-sts/target/skoruba4j-sts-*.jar /out/skoruba4j-sts.jar \
    && cp skoruba4j-admin/target/skoruba4j-admin-*.jar /out/skoruba4j-admin.jar \
    && rm -f /out/*.original

FROM eclipse-temurin:21.0.12_8-jre-jammy AS runtime
RUN --mount=type=cache,target=/var/cache/apt,sharing=locked \
    --mount=type=cache,target=/var/lib/apt,sharing=locked \
    apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && groupadd --system --gid 1001 skoruba4j \
    && useradd --system --uid 1001 --gid skoruba4j --home-dir /var/lib/skoruba4j --create-home --shell /usr/sbin/nologin skoruba4j \
    && mkdir -p /var/lib/skoruba4j/data /var/lib/skoruba4j/secrets /opt/skoruba4j \
    && chown -R 1001:1001 /var/lib/skoruba4j /opt/skoruba4j
WORKDIR /opt/skoruba4j
COPY --from=build --link --chown=1001:1001 /out/skoruba4j-sts.jar /opt/skoruba4j/skoruba4j-sts.jar
COPY --from=build --link --chown=1001:1001 /out/skoruba4j-admin.jar /opt/skoruba4j/skoruba4j-admin.jar
COPY --link --chown=1001:1001 demo/idserver-demo.yml /opt/skoruba4j/idserver-demo.yml
ENV IDSERVER_HOME=/var/lib/skoruba4j
USER 1001:1001
EXPOSE 5050 6060
ENTRYPOINT ["java", "-Xmx512m", "-Dspring.profiles.active=oss", "-Dserver.ssl.enabled=false", "-Dserver.address=0.0.0.0", "-DIDSERVER_HOME=/var/lib/skoruba4j", "-Dspring.config.additional-location=file:/opt/skoruba4j/idserver-demo.yml"]
CMD ["-jar", "/opt/skoruba4j/skoruba4j-sts.jar"]
