# Serveur Spring Boot (Railway ou tout hébergeur de conteneurs). Contexte de construction : racine du dépôt.
# Les tests tournent dans la CI (./mvnw verify) ; l'image ne fait que compiler et empaqueter.
FROM eclipse-temurin:25-jdk AS construction
WORKDIR /depot/backend
COPY backend/.mvn .mvn
COPY backend/mvnw backend/pom.xml ./
RUN ./mvnw -q -B dependency:go-offline
COPY backend/config ./config
COPY backend/src ./src
RUN ./mvnw -q -B package -DskipTests -Dcheckstyle.skip && cp "$(ls target/*.jar | grep -v plain | head -1)" /application.jar

FROM eclipse-temurin:25-jre
RUN useradd --system --uid 10001 ambawbio
USER ambawbio
COPY --from=construction /application.jar /application.jar
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/application.jar"]
