FROM maven:3-jdk-8-alpine AS build

# selectively add the POM file
COPY pom.xml /

# get all the downloads out of the way
RUN mvn verify clean --fail-never

# Build
COPY src /src
RUN mvn -Dassembly.appendAssemblyId=false package

FROM openjdk:8-jdk-alpine
COPY --from=build /target/*jar-with-dependencies.jar /

ENV IPFS_TIKA_LISTEN_HOST=0.0.0.0

EXPOSE 8081
CMD ["sh", "-c", "java -jar *.jar"]
