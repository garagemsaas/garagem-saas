FROM eclipse-temurin:25-jdk AS build

RUN apt-get update \
    && apt-get install -y --no-install-recommends unzip \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /build
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -ntp dependency:go-offline
COPY src src
RUN ./mvnw -B -ntp -DskipTests package

FROM eclipse-temurin:25-jre
WORKDIR /app
# curl entra só para o healthcheck do contêiner: a imagem base não traz cliente HTTP.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
RUN groupadd --system garagem && useradd --system --gid garagem garagem
COPY --from=build /build/target/garagem-api-0.1.0-SNAPSHOT.jar app.jar
USER garagem
EXPOSE 8080
# /actuator/health agrega banco e storage: o contêiner só fica healthy quando serve de verdade.
HEALTHCHECK --interval=10s --timeout=3s --start-period=60s --retries=6 \
    CMD curl -fsS http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
