# kervan-core image (SPEC §11). Dependencies are fetched in a layer that sees only pom.xml;
# the sources are compiled with no network. Tests run in CI, not here.

FROM maven:3.9-eclipse-temurin-21 AS deps
WORKDIR /build
COPY pom.xml .
# Packaging the empty source tree resolves every plugin the real build uses; go-offline adds the rest.
RUN mvn -B -q -DskipTests package dependency:go-offline && rm -rf target

FROM deps AS build
COPY src ./src
RUN --network=none mvn -B -o -q -DskipTests package

FROM eclipse-temurin:21-jre
RUN groupadd --gid 10001 kervan \
    && useradd --uid 10001 --gid kervan --no-create-home --shell /usr/sbin/nologin kervan \
    && mkdir -p /var/log/kervan \
    && chown kervan:kervan /var/log/kervan
WORKDIR /app
COPY --from=build /build/target/kervan-core.jar /app/kervan-core.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/kervan-core.jar"]
