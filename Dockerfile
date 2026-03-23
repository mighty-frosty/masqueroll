FROM maven:3.9.11-eclipse-temurin-25 AS build
WORKDIR /app

COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B package

FROM eclipse-temurin:25-jre
WORKDIR /app

COPY --from=build /app/target/masqueroll.jar /app/masqueroll.jar

ENTRYPOINT ["java", "-jar", "/app/masqueroll.jar"]
