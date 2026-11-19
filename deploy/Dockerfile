FROM eclipse-temurin:17-jre
WORKDIR /app
COPY backend/target/billiard-server-0.1.0.jar /app/server.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/server.jar"]
