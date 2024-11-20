# Eduhub Validator Service

A web service for validating Eduhub OOAPI endpoints

## API

### Check endpoint.

Perform basic checks on whether the endpoint is up.

Calls the endpoint with `endpointId` through the Eduhub gateway and
reports if a successful response is received.

On success, responds with a `200 OK` status

On error, responds with a `502 Bad Gateway` status.

`GET /endpoints/{endpointId}/config`

### Validate endpoint

Use the validator to validate the endpoint and generate a report.

`GET /endpoints/{endpointId}/paths`

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

The service is configured using environment variables:

```
GATEWAY_URL                         https://gateway.test.surfeduhub.nl/
GATEWAY_BASIC_AUTH_USER             Username for gateway
GATEWAY_BASIC_AUTH_PASS             Password for gateway
SURF_CONEXT_CLIENT_ID               SurfCONEXT client id for validation service
SURF_CONEXT_CLIENT_SECRET           SurfCONEXT client secret for validation service
SURF_CONEXT_INTROSPECTION_ENDPOINT  SurfCONEXT introspection endpoint
ALLOWED_CLIENT_IDS                  Comma separated list of allowed SurfCONEXT client ids. 
MAX_TOTAL_REQUESTS                  Maximum number of requests that validator is allowed to make before raising an error
OOAPI_VERSION                       Ooapi version to pass through to gateway
SERVER_PORT                         Starts the app server on this port
REDIS_URI                           URI to redis
JOB_STATUS_EXPIRY_SECONDS           Number of seconds before job status in Redis expires
SPIDER_TIMEOUT_MILLIS               Maximum number of milliseconds before spider timeout.
VALIDATOR_SERVICE_ROOT_URL          The root url of the web endpoint, used to generate a url to a status view. This url is included in the json output after starting a validation job as "web-url".
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
curl -v 'http://localhost:3002/endpoints/demo04.test.surfeduhub.nl/config'
curl -v 'http://localhost:3002/endpoints/demo04.test.surfeduhub.nl/paths'
```

## Run locally

```bash
make
java -jar target/eduhub-validator-service.jar
# Extract ACCESS_TOKEN
curl -s -X POST https://connect.test.surfconext.nl/oidc/token -u client01.registry.validator.dev.surfeduhub.nl:$SURF_CONEXT_PASSWORD -d "grant_type=client_credentials"
curl -v -X POST 'http://localhost:3002/endpoints/demo04.test.surfeduhub.nl/paths?profile=rio' -H "Authorization: Bearer $ACCESS_TOKEN" 
```

## Notes

Relevant repos:

https://github.com/SURFnet/eduhub-validator

https://github.com/SURFnet/apie
