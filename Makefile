# SPDX-FileCopyrightText: 2024, 2025 SURF B.V.
# SPDX-License-Identifier: AGPL-3.0-or-later
# SPDX-FileContributor: Joost Diepenmaat
# SPDX-FileContributor: Michiel de Mare
# SPDX-FileContributor: Remco van 't Veer

.PHONY: lint lint-clj lint-license test check clean

default: target/eduhub-validator-service.jar

classes/nl/surf/eduhub/validator/service/main.class: src/nl/surf/eduhub/validator/service/main.clj
	mkdir -p classes
	clj -M -e "(compile 'nl.surf.eduhub.validator.service.main)"

target/eduhub-validator-service.jar: classes/nl/surf/eduhub/validator/service/main.class
	clojure -M:uberjar --main-class nl.surf.eduhub.validator.service.main --target $@

prep-lint:
	mkdir -p ./.clj-kondo
	clojure -M:lint --lint $$(clojure -Spath)  --copy-configs --dependencies --skip-lint

lint-clj: prep-lint
	clojure -M:lint

lint-license:
	reuse lint

lint: lint-clj lint-license

test:
	clojure -M:test

check: lint test

clean:
	rm -rf classes target

opentelemetry-javaagent.jar:
	curl -L https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/latest/download/opentelemetry-javaagent.jar -o $@

.PHONY: docker-build test lint check

docker-build: Dockerfile docker-compose.yml opentelemetry-javaagent.jar
	docker compose build
