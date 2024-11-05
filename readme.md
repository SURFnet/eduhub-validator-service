# Eduhub Validator Service

A web service for validating Eduhub OOAPI endpoints

## API

`GET /endpoints/{endpointId}/config`

Calls the endpoint with `endpointId` through the Eduhub gateway and
reports if a successful response is received.

On success, responds with a `200 OK` status

On error, responds with a `502 Bad Gateway` status.

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
```

## Notes

Relevant repos:

https://github.com/SURFnet/eduhub-validator

https://github.com/SURFnet/apie
