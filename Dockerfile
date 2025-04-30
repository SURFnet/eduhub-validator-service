# SPDX-FileCopyrightText: 2024 SURF B.V.
# SPDX-FileContributor: Joost Diepenmaat
#
# SPDX-License-Identifier: Apache-2.0

FROM clojure:temurin-21-tools-deps-1.11.1.1435 AS builder
RUN apt-get -y update
RUN apt-get install -y curl

RUN mkdir /app
WORKDIR /app
COPY . /app/
RUN make
RUN make opentelemetry-javaagent.jar

FROM gcr.io/distroless/java21-debian12:nonroot
COPY --from=builder /app/target/eduhub-validator-service.jar /eduhub-validator-service.jar
COPY --from=builder /app/opentelemetry-javaagent.jar /opentelemetry-javaagent.jar

WORKDIR /
ENTRYPOINT ["java", "-jar", "eduhub-validator-service.jar"]
