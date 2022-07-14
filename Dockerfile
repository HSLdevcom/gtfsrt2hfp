FROM openjdk:11-jre-slim
ADD build/libs/gtfsrt2hfp.jar /usr/app/gtfsrt2hfp.jar
ENTRYPOINT ["java", "-XX:InitialRAMPercentage=10.0", "-XX:MaxRAMPercentage=95.0", "-jar", "/usr/app/gtfsrt2hfp.jar"]