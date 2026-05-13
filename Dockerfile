FROM container-registry.oracle.com/graalvm/jdk:25
WORKDIR /app

COPY build/libs/mozu-0.0.1-SNAPSHOT.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]