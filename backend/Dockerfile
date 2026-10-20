FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml ./
RUN mvn -q -B dependency:go-offline
COPY src ./src
# Os testes rodam na CI (mvn test); a imagem só empacota.
RUN mvn -q -B package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080

# Ajustado para hosts gratuitos de 512 MB / 0,1 vCPU: heap limitada a ~60% da RAM do container,
# GC serial (menos threads e memória) e compilação JIT leve. Sobrescreva com a variável JAVA_OPTS.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=60 -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Xss512k"
CMD ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
