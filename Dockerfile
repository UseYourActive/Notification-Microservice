### STAGE 1: BUILD ###
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /usr/src/app

# Cache dependencies first (layer caching)
COPY pom.xml .
RUN mvn dependency:go-offline

COPY ./src ./src
RUN mvn package -DskipTests -Dquarkus.container-image.build=false

### STAGE 2: RUN ###
FROM registry.access.redhat.com/ubi9/openjdk-21:1.23

# Copy the Quarkus fast-jar layout from the build stage
COPY --chown=185 --from=build /usr/src/app/target/quarkus-app/lib/     /deployments/lib/
COPY --chown=185 --from=build /usr/src/app/target/quarkus-app/*.jar    /deployments/
COPY --chown=185 --from=build /usr/src/app/target/quarkus-app/app/     /deployments/app/
COPY --chown=185 --from=build /usr/src/app/target/quarkus-app/quarkus/ /deployments/quarkus/

EXPOSE 8080
USER 185

ENV JAVA_OPTS_APPEND="-Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager"
ENV JAVA_APP_JAR="/deployments/quarkus-run.jar"

ENTRYPOINT [ "/opt/jboss/container/java/run/run-java.sh" ]