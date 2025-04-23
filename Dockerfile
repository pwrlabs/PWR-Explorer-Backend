Step 1: Build stage using the latest Maven and OpenJDK
FROM maven:latest as build

Set the working directory in the container
WORKDIR /app

Copy the pom.xml and install dependencies
COPY pom.xml /app
RUN mvn dependency:go-offline

Copy the rest of the application source code
COPY src /app/src

Build the application
RUN mvn clean package -DskipTests

Step 2: Runtime stage using a Debian-based OpenJDK image
FROM openjdk:21-jdk-slim

Set the working directory in the container
WORKDIR /app

Copy the built JAR from the build stage here we should put the new jar name
COPY --from=build /app/target/PWR-Explorer-1.0-SNAPSHOT.jar /app/Explorer-Backend-dev.jar

Expose the port that the server will listen on
EXPOSE 8081

Default command to run the JAR file
CMD ["java", "-jar", "/app/Explorer-Backend-dev.jar"]