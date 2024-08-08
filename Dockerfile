FROM maven:3.8.4-openjdk-17 AS build

WORKDIR /app

COPY pom.xml .

RUN mvn dependency:go-offline -B

COPY src/ ./src/

RUN mvn -f /app/pom.xml clean package -DskipTests

# Run the app

FROM openjdk:17-jdk-slim

EXPOSE 443

COPY --from=build /app/target/*.jar /app/app.jar

ENTRYPOINT ["java","-jar","/app/app.jar"]