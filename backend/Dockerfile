FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -DskipTests package

FROM eclipse-temurin:21-jre-jammy
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl fontconfig \
 && rm -rf /var/lib/apt/lists/* \
 && useradd --system --uid 10001 --create-home framelab
WORKDIR /app
COPY --from=build /src/target/framelab-api.jar /app/app.jar
RUN mkdir -p /app/storage && chown -R framelab:framelab /app
USER framelab
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
