FROM maven:3.9-eclipse-temurin-21 AS build

# Copy risk-domain source and POM from build context
COPY risk-domain/pom.xml /risk-domain/pom.xml
COPY risk-domain/src /risk-domain/src

# Build and install risk-domain into local Maven repository
RUN mvn -f /risk-domain/pom.xml install -DskipTests

# Copy trading-core source
COPY trading-core/pom.xml /trading-core/pom.xml
COPY trading-core/src /trading-core/src

# Build trading-core with reactor (includes risk-domain from local repo)
RUN mvn -f /trading-core/pom.xml -am clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY --from=build /risk-domain/target/risk-domain-0.0.1-SNAPSHOT.jar /opt/risk-domain/risk-domain-0.0.1-SNAPSHOT.jar

COPY --from=build /trading-core/target/trading-core-0.0.1-SNAPSHOT.jar app.jar



ENTRYPOINT ["java", "-jar", "app.jar"]
