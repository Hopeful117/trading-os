FROM maven:3.9.9-eclipse-temurin-21 AS builder

WORKDIR /app
COPY secret-crypto/pom.xml secret-crypto/pom.xml
COPY secret-crypto/src secret-crypto/src
RUN mvn -f secret-crypto/pom.xml clean install -DskipTests

COPY ctrader-openapi-technical/pom.xml ctrader-openapi-technical/pom.xml
COPY ctrader-openapi-technical/src ctrader-openapi-technical/src
RUN mvn -f ctrader-openapi-technical/pom.xml clean install -DskipTests

COPY market-data/pom.xml market-data/pom.xml
COPY market-data/src market-data/src
RUN mvn -f market-data/pom.xml clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/market-data/target/market-data-*.jar app.jar

ARG GIT_SHA
LABEL org.opencontainers.image.revision="${GIT_SHA}"



ENTRYPOINT ["java", "-jar", "app.jar"]
