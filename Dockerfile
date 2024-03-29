FROM eclipse-temurin:11-alpine AS jre-build
ADD build/libs/gtfsrt2hfp.jar /app/gtfsrt2hfp.jar
RUN unzip /app/gtfsrt2hfp.jar -d /app/unpacked
RUN jdeps --print-module-deps --ignore-missing-deps -q --recursive --multi-release 11 --class-path="/app/unpacked/BOOT-INF/lib/*" --module-path="/app/unpacked/BOOT-INF/lib/*" /app/gtfsrt2hfp.jar > /java-modules.txt
RUN apk add --no-cache binutils
RUN jlink --verbose --bind-services --add-modules $(cat /java-modules.txt) --strip-debug --no-man-pages --no-header-files --compress=2 --output /customjre

FROM alpine:3
ENV JAVA_HOME=/jre
ENV PATH="${JAVA_HOME}/bin:${PATH}"

COPY --from=jre-build /customjre $JAVA_HOME

RUN apk add --no-cache java-cacerts && rm -rf ${JAVA_HOME}/lib/security/cacerts && ln -s /etc/ssl/certs/java/cacerts ${JAVA_HOME}/lib/security/cacerts

ADD build/libs/gtfsrt2hfp.jar /usr/app/gtfsrt2hfp.jar
ENTRYPOINT ["java", "-XX:InitialRAMPercentage=10.0", "-XX:MaxRAMPercentage=95.0", "-jar", "/usr/app/gtfsrt2hfp.jar"]
