FROM eclipse-temurin:21-jdk AS construction
WORKDIR /construction
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY src/ src/
# Les tests avec Testcontainers tournent dans la CI, avant la publication.
RUN ./mvnw -B package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 10001 --user-group croustyquizz
COPY --from=construction --chown=croustyquizz:croustyquizz /construction/target/*.jar application.jar
USER croustyquizz
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "application.jar"]
