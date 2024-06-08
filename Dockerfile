# Use the openjdk:19-jdk-alpine base image
FROM openjdk:19-jdk-alpine

# Set the working directory inside the container
WORKDIR /app

# Copy the JAR file to the container
COPY goose-standalone.jar .

# Set the entry point to run the JAR file
ENTRYPOINT ["java", "-jar", "goose-standalone.jar"]
