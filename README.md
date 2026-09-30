# Eduhub Validator Service

A web service for validating Eduhub OOAPI endpoints

## API

### Check endpoint.

Perform basic checks on whether the endpoint is up.

Calls the endpoint with `endpointId` through the Eduhub gateway and
reports if a successful response is received.

The `oeapi-version` parameter is optional, default is "5".

The gateway path defaults to `/courses` but can be customised with the
`CHECK_ENDPOINT_PATH` environment variable. Provide a `path`
query parameter to temporarily override the default, e.g.
`GET /configstatus/{endpointId}?path=/programs&oeapi-version=6`.

On success, responds with a `200 OK` status

On error, responds with a `502 Bad Gateway` status.

`GET /configstatus/{endpointId}?oeapi-version=5`

### Validate endpoint

Use the validator to validate the endpoint and generate a report.

`POST /jobs/paths/{endpointId}?profile=ooapi`

The oeapi version is part of the specified profile.

### Fetch Status

Load the current status as json. Fields include job-status (pending, finished or failed), endpoint-id, profile,
pending-at and finished-at, with ISO-8601 timestamp format.

`GET /status/{uuid}`

# HTML Endpoints

## View status

View the status in the browser. If the status is finished, the report can be viewed, downloaded or deleted from this page.

`GET /view/status/{uuid}`

## View report

View the validation report in the browser.

`GET /view/report/{uuid}`

## Download Report

Download report as a HTML file.

`GET /download/report/{uuid}`

# Delete report

Delete the report and the associated status data from the Redis database.

`POST /delete/report/{uuid}`

## Configuring

### Environment vars

The service can be fully configured using environment variables:

```
ALLOWED_CLIENT_IDS                  Comma separated list of allowed SurfCONEXT client ids.
CHECK_ENDPOINT_PATH                 Default path used for config status checks
GATEWAY_BASIC_AUTH_PASS             Basic auth password of gateway
GATEWAY_BASIC_AUTH_USER             Basic auth username of gateway
GATEWAY_URL                         URL of gateway
JOB_STATUS_EXPIRY_SECONDS           Number of seconds before job status in Redis expires
MAX_TOTAL_REQUESTS                  Maximum number of requests that validator is allowed to make before raising an error
REDIS_URI                           URI to redis
SERVER_PORT                         Starts the app server on this port
SPIDER_TIMEOUT_MILLIS               Maximum number of milliseconds before spider timeout.
SURF_CONEXT_CLIENT_ID               SurfCONEXT client id for validation service
SURF_CONEXT_CLIENT_SECRET           SurfCONEXT client secret for validation service
SURF_CONEXT_INTROSPECTION_ENDPOINT  SurfCONEXT introspection endpoint
VALIDATOR_INSTANCE_NAME             Unique name for the validator service instance
VALIDATOR_SERVICE_ROOT_URL          Root url for the web view; does not include path
```

### Secret files

If secrets need to be kept out of the environment, for all variables
`VAR_NAME` it is possible to provide a `VAR_NAME_FILE` variable
instead, which provides a path to a file containing the corresponding
value.

For instance, if REDIS_URI would contain a password, create a file
`/var/secrets/redis_uri` containing
`redis://someuser:apassword@redis.example.com`, and provide a
`REDIS_URI_FILE` variable containing `/var/secrets/redis_uri`.

## Debugging configuration

In order to see the configuration as used by the service, run the
following in the environment:

```
java -cp /path/to/eduhub-validator-service.jar clojure.main -e "(do (require '[nl.surf.eduhub.validator.service.config :as config]) (prn (config/validate-and-load-config environ.core/env)))"
```

## Build

```
make
java -jar target/eduhub-validator-service.jar
# To test:
curl -v 'http://localhost:3002/endpoints/demo04.test.surfeduhub.nl/config'
```


## Run in Docker

```
make docker-build
docker compose up
# To test:
curl -v 'http://localhost:3002/configstatus/demo04.test.surfeduhub.nl'
curl -v -X POST 'http://localhost:3002/jobs/paths/demo04.test.surfeduhub.nl'
```

The image build is *nonroot* variant of *distroless* Debian which runs as:

- *user*: `nonroot` (uid=65532)
- *group*: `nonroot` (guid=65532)

## Run locally

```bash
make
java -jar target/eduhub-validator-service.jar
# Extract ACCESS_TOKEN
curl -s -X POST https://connect.test.surfconext.nl/oidc/token -u :$SURF_CONEXT_CLIENT_SECRET -d "grant_type=client_credentials"
curl -v -X POST 'http://localhost:3002/jobs/paths/demo04.test.surfeduhub.nl?profile=rio' -H "Authorization: Bearer $ACCESS_TOKEN"
```

## Notes

Relevant repos:

https://github.com/SURFnet/eduhub-validator

https://github.com/SURFnet/apie
