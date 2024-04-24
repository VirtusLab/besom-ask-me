FROM ghcr.io/graalvm/jdk-community:21

COPY app.main /app/main

ENTRYPOINT java -jar /app/main
