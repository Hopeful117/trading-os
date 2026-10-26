FROM maven:3.9.9-eclipse-temurin-21 AS builder

WORKDIR /app
COPY secret-crypto/pom.xml secret-crypto/pom.xml
COPY secret-crypto/src secret-crypto/src
RUN mvn -f secret-crypto/pom.xml clean install -DskipTests

COPY ctrader-openapi-technical/pom.xml ctrader-openapi-technical/pom.xml
COPY ctrader-openapi-technical/src ctrader-openapi-technical/src
RUN mvn -f ctrader-openapi-technical/pom.xml clean install -DskipTests

COPY broker-service/pom.xml broker-service/pom.xml
COPY broker-service/src broker-service/src
RUN mvn -f broker-service/pom.xml clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/broker-service/target/broker-service-0.0.1-SNAPSHOT-exec.jar app.jar



ENTRYPOINT ["java", "-jar", "app.jar"]
