FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY backend/pom.xml ./pom.xml
RUN mvn -B dependency:go-offline
COPY backend/src ./src
RUN mvn -B package

FROM eclipse-temurin:17-jre-jammy
RUN groupadd --system careerforge && useradd --system --gid careerforge careerforge
WORKDIR /app
COPY --from=build --chown=careerforge:careerforge /build/target/careerforge-2.0.0.jar app.jar
RUN mkdir data && chown careerforge:careerforge data
USER careerforge
ENV SPRING_PROFILES_ACTIVE=public
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=65.0"
EXPOSE 8090
ENTRYPOINT ["java", "-jar", "app.jar"]
