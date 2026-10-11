# ---------- Stage 1: build ----------
FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace

# Copy wrapper + build files trước để cache dependency
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew
RUN ./gradlew dependencies --no-daemon > /dev/null 2>&1 || true

# Copy source và build
COPY src ./src
RUN ./gradlew bootJar -x test --no-daemon \
    && cp "$(ls build/libs/*.jar | grep -v plain | head -n 1)" app.jar

# ---------- Stage 2: runtime ----------
FROM eclipse-temurin:17-jre
WORKDIR /app

RUN groupadd --system spring && useradd --system --gid spring spring
USER spring:spring

COPY --from=build /workspace/app.jar app.jar

EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]