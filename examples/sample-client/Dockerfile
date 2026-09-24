# Test runner image for CronJobs / pipelines: dependencies are resolved at build time, so runs
# start in seconds and work offline (mvn -o).
FROM maven:3.9.11-eclipse-temurin-21
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B -q dependency:go-offline -DexcludeReactor=false || true
COPY src ./src
RUN mvn -B -q -o test-compile
ENTRYPOINT []
CMD ["mvn", "-B", "-o", "test"]
