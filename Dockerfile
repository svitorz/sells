# syntax=docker/dockerfile:1.7

# Compile in an isolated JDK image. The Maven Wrapper pins the Maven version
# used by the project, regardless of what is installed on the host.
FROM eclipse-temurin:21-jdk-jammy AS build

WORKDIR /workspace

# Copy only build metadata first so dependency resolution can be cached.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw \
    && ./mvnw --batch-mode --no-transfer-progress dependency:go-offline

COPY src/ src/
RUN ./mvnw --batch-mode --no-transfer-progress clean package -DskipTests

# Keep the runtime image small and free of the JDK, Maven and source code.
# This is a desktop application, so it intentionally does not expose ports.
FROM eclipse-temurin:21-jre-jammy AS runtime

WORKDIR /app

RUN groupadd --system spring \
    && useradd --system --gid spring --no-create-home spring

COPY --from=build /workspace/target/*.jar app.jar

USER spring:spring

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
