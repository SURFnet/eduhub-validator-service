# SPDX-FileCopyrightText: 2024 SURF B.V.
# SPDX-FileContributor: Joost Diepenmaat
# SPDX-FileContributor: Remco van 't Veer
#
# SPDX-License-Identifier: Apache-2.0

DOCKER=docker

.PHONY: lint test check clean outdated

default: target/eduhub-validator-service.jar

classes/nl/surf/eduhub/validator/service/main.class: src/nl/surf/eduhub/validator/service/main.clj
	mkdir -p classes
	clj -M -e "(compile 'nl.surf.eduhub.validator.service.main)"

target/eduhub-validator-service.jar: classes/nl/surf/eduhub/validator/service/main.class
	clojure -M:uberjar --main-class nl.surf.eduhub.validator.service.main --target $@

prep-lint:
	mkdir -p ./.clj-kondo
	clojure -M:lint --lint $$(clojure -Spath)  --copy-configs --dependencies --skip-lint

lint: prep-lint
	clojure -M:lint

test:
	clojure -M:test

outdated:
	clojure -M:outdated

check: lint test outdated

clean:
	rm -rf classes target

opentelemetry-javaagent.jar:
	curl -L https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/latest/download/opentelemetry-javaagent.jar -o $@

.PHONY: docker-build test lint check

docker-build: Dockerfile docker-compose.yml opentelemetry-javaagent.jar
	$(DOCKER) compose build
