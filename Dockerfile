FROM maven:3.8.4-openjdk-17 AS build

WORKDIR /app

COPY pom.xml .

RUN --mount=type=cache,target=/root/.m2 \
  mvn dependency:go-offline -B

COPY src/ ./src/

RUN --mount=type=cache,target=/root/.m2 \
  mvn -f /app/pom.xml clean package -DskipTests

FROM gcr.io/distroless/java21-debian12:nonroot AS runtime

WORKDIR /app

EXPOSE 443

COPY --from=build /app/target/*.jar /app/app.jar

ENTRYPOINT ["java","-jar","/app/app.jar"]


CMD []
