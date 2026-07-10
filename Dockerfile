FROM docker.io/library/maven:3.9.12-eclipse-temurin-17 AS build-hapi
WORKDIR /tmp/hapi-fhir-jpaserver-starter

ARG OPENTELEMETRY_JAVA_AGENT_VERSION=2.24.0
RUN curl -LSsO https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v${OPENTELEMETRY_JAVA_AGENT_VERSION}/opentelemetry-javaagent.jar

COPY pom.xml .
COPY server.xml .
RUN mvn -ntp dependency:go-offline

COPY src/ /tmp/hapi-fhir-jpaserver-starter/src/
RUN mvn clean install -DskipTests -Djdk.lang.Process.launchMechanism=vfork

FROM build-hapi AS build-distroless
RUN mvn package -DskipTests spring-boot:repackage -Pboot
RUN mkdir /app && cp /tmp/hapi-fhir-jpaserver-starter/target/ROOT.war /app/main.war


########### Use the official Tomcat image as base image for the Tomcat variant
########### it can be built using eg. `docker build --target tomcat .`
FROM docker.io/library/tomcat:10-jre21-temurin-noble AS tomcat

USER root
RUN rm -rf /usr/local/tomcat/webapps/ROOT && \
    mkdir -p /usr/local/tomcat/data/hapi/lucenefiles && \
    chown -R 65532:65532 /usr/local/tomcat/data/hapi/lucenefiles && \
    chmod 775 /usr/local/tomcat/data/hapi/lucenefiles

RUN mkdir -p /target && chown -R 65532:65532 /target
USER 65532

COPY --chown=65532:65532 catalina.properties /usr/local/tomcat/conf/catalina.properties
COPY --chown=65532:65532 server.xml /usr/local/tomcat/conf/server.xml
COPY --from=build-hapi --chown=65532:65532 /tmp/hapi-fhir-jpaserver-starter/target/ROOT.war /usr/local/tomcat/webapps/ROOT.war
COPY --from=build-hapi --chown=65532:65532 /tmp/hapi-fhir-jpaserver-starter/opentelemetry-javaagent.jar /app

########### distroless brings focus on security and runs on plain spring boot
FROM gcr.io/distroless/java21-debian13:nonroot AS distroless
# 65532 is the nonroot user's uid
# used here instead of the name to allow Kubernetes to easily detect that the container
# is running as a non-root (uid != 0) user.
USER 65532:65532
WORKDIR /app

COPY --chown=nonroot:nonroot --from=build-distroless /app /app
COPY --chown=nonroot:nonroot --from=build-hapi /tmp/hapi-fhir-jpaserver-starter/opentelemetry-javaagent.jar /app

ENTRYPOINT ["java", "--class-path", "/app/main.war", "-Dloader.path=main.war!/WEB-INF/classes/,main.war!/WEB-INF/,/app/extra-classes", "org.springframework.boot.loader.PropertiesLauncher"]

########### default runtime keeps Maven available so Dolus-based $generate can execute inside the container
FROM docker.io/library/maven:3.9.12-eclipse-temurin-21 AS default
WORKDIR /app

RUN mkdir -p /opt/generation/realms /opt/generation/work /root/.m2

ENV HAPI_FHIR_GENERATION_ENABLED=true \
    HAPI_FHIR_GENERATION_JAR_PATH=/opt/generation/dolus.jar \
    HAPI_FHIR_GENERATION_INPUT_BASE_DIR=/opt/generation/realms \
    HAPI_FHIR_GENERATION_WORK_DIR=/opt/generation/work \
    HAPI_FHIR_GENERATION_MAVEN_PATH=mvn

COPY docker/m2 /root/.m2
COPY --from=build-distroless /app /app
COPY --from=build-hapi /tmp/hapi-fhir-jpaserver-starter/opentelemetry-javaagent.jar /app

ENTRYPOINT ["java", "--class-path", "/app/main.war", "-Dloader.path=main.war!/WEB-INF/classes/,main.war!/WEB-INF/,/app/extra-classes", "org.springframework.boot.loader.PropertiesLauncher"]
