ARG MAVEN_IMAGE=docker.m.daocloud.io/library/maven:3.9.9-eclipse-temurin-17
ARG JAVA_RUNTIME_IMAGE=docker.m.daocloud.io/library/eclipse-temurin:17-jre-alpine
FROM ${MAVEN_IMAGE} AS builder
ARG MAVEN_REPO_URL=https://maven.aliyun.com/repository/public
WORKDIR /workspace
COPY backend/java-report-core/pom.xml backend/java-report-core/pom.xml
COPY backend/java-report-core/src backend/java-report-core/src
WORKDIR /workspace/backend/java-report-core
RUN mvn -B -DskipTests -Dmaven.repo.local=/tmp/.m2 -DremoteRepositories=central::default::${MAVEN_REPO_URL} package

ARG JAVA_RUNTIME_IMAGE=docker.m.daocloud.io/library/eclipse-temurin:17-jre-alpine
FROM ${JAVA_RUNTIME_IMAGE}
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY --from=builder /workspace/backend/java-report-core/target/*.jar /app/app.jar
USER app
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 CMD wget -qO- http://127.0.0.1:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
