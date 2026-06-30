# Permission Engine - Postman Scenario

This scenario provides a simple way to test the `Permission Engine` in HAPI FHIR using these operations:

- `$authorize-request`
- `$filter-response`

The goal is to start with a simple local mode:

- without real token validation;
- without an external FHIR repository;
- without server interceptors;
- with inline `permissionJson`.

## 1. Profile Configuration

In `profiles/application-permission.yml`, use this configuration:

```yaml
hapi:
  fhir:
    permission:
      enabled: true
      expose-operations: true
      enable-interceptors: false
      allow-inline-permissions: true
      require-token: false
      fail-closed: true
      default-fhir-version: R4
      token-validation:
        enabled: false
      token-source:
        enabled: false
      repository:
        enabled: false
```

## 2. Start the Application

From the `hl7-hapi-fhir-jpaserver-starter` repository:

```powershell
mvn -Pboot spring-boot:run "-Dspring-boot.run.arguments=--spring.config.additional-location=file:./profiles/application-permission.yml"
```

Expected base URL:

```text
http://localhost:8080/fhir
```

## 3. Test 1 - Authorize a Search with Rewriting

### Postman Request

- Method: `POST`
- URL: `http://localhost:8080/fhir/$authorize-request`
- Header: `Content-Type: application/fhir+json`

Body:

```json
{
  "resourceType": "Parameters",
  "parameter": [
    {
      "name": "fhirVersion",
      "valueCode": "R4"
    },
    {
      "name": "method",
      "valueCode": "GET"
    },
    {
      "name": "url",
      "valueString": "/fhir/Patient"
    },
    {
      "name": "resourceType",
      "valueCode": "Patient"
    },
    {
      "name": "permissionJson",
      "valueString": "{\"resourceType\":\"Permission\",\"status\":\"active\",\"rule\":[{\"type\":\"permit\",\"activity\":[{\"action\":[{\"coding\":[{\"code\":\"search\"}]}]}],\"data\":[{\"resource\":[{\"meaning\":\"instance\",\"reference\":{\"display\":\"Patient\"}}],\"expression\":{\"language\":\"application/x-fhir-query\",\"expression\":\"name=Toto\"}}]}]}"
    }
  ]
}
```

### Expected Result

- HTTP `200`
- `decision = ALLOW_WITH_REWRITE`
- `allowed = true`
- `rewrittenQueryString = name=Toto`

## 4. Test 2 - Authorize an Update

### Postman Request

- Method: `POST`
- URL: `http://localhost:8080/fhir/$authorize-request`
- Header: `Content-Type: application/fhir+json`

Body:

```json
{
  "resourceType": "Parameters",
  "parameter": [
    {
      "name": "fhirVersion",
      "valueCode": "R4"
    },
    {
      "name": "method",
      "valueCode": "PUT"
    },
    {
      "name": "url",
      "valueString": "/fhir/Patient/example-toto"
    },
    {
      "name": "resourceType",
      "valueCode": "Patient"
    },
    {
      "name": "resourceId",
      "valueString": "example-toto"
    },
    {
      "name": "permissionJson",
      "valueString": "{\"resourceType\":\"Permission\",\"status\":\"active\",\"rule\":[{\"type\":\"permit\",\"activity\":[{\"action\":[{\"coding\":[{\"code\":\"update\"}]}]}],\"data\":[{\"resource\":[{\"meaning\":\"instance\",\"reference\":{\"display\":\"Patient\"}}]}]}]}"
    }
  ]
}
```

### Expected Result

- HTTP `200`
- `decision = ALLOW`
- `allowed = true`
- empty `rewrittenQueryString`

## 5. Test 3 - Reject an Unauthorized Update

### Postman Request

- Method: `POST`
- URL: `http://localhost:8080/fhir/$authorize-request`
- Header: `Content-Type: application/fhir+json`

Body:

```json
{
  "resourceType": "Parameters",
  "parameter": [
    {
      "name": "fhirVersion",
      "valueCode": "R4"
    },
    {
      "name": "method",
      "valueCode": "PUT"
    },
    {
      "name": "url",
      "valueString": "/fhir/Patient/example-toto"
    },
    {
      "name": "resourceType",
      "valueCode": "Patient"
    },
    {
      "name": "resourceId",
      "valueString": "example-toto"
    },
    {
      "name": "permissionJson",
      "valueString": "{\"resourceType\":\"Permission\",\"status\":\"active\",\"rule\":[{\"type\":\"permit\",\"activity\":[{\"action\":[{\"coding\":[{\"code\":\"search\"}]}]}],\"data\":[{\"resource\":[{\"meaning\":\"instance\",\"reference\":{\"display\":\"Patient\"}}]}]}]}"
    }
  ]
}
```

### Expected Result

- HTTP `403`
- `OperationOutcome` resource
- a message close to `Request is not authorized`

## 6. Test 4 - Filter a Patient Response

### Postman Request

- Method: `POST`
- URL: `http://localhost:8080/fhir/$filter-response`
- Header: `Content-Type: application/fhir+json`

Body:

```json
{
  "resourceType": "Parameters",
  "parameter": [
    {
      "name": "fhirVersion",
      "valueCode": "R4"
    },
    {
      "name": "statusCode",
      "valueInteger": 200
    },
    {
      "name": "responseBodyJson",
      "valueString": "{\"resourceType\":\"Patient\",\"id\":\"example-toto\",\"name\":[{\"family\":\"Allowed\",\"given\":[\"Toto\"]}],\"telecom\":[{\"system\":\"phone\",\"value\":\"0102030405\"}]}"
    },
    {
      "name": "permissionJson",
      "valueString": "{\"resourceType\":\"Permission\",\"status\":\"active\",\"rule\":[{\"type\":\"deny\",\"activity\":[{\"action\":[{\"coding\":[{\"code\":\"read\"}]}]}],\"data\":[{\"resource\":[{\"meaning\":\"instance\",\"reference\":{\"display\":\"Patient\"}}],\"expression\":{\"language\":\"text/fhirpath\",\"expression\":\"Patient.telecom\"}}]}]}"
    }
  ]
}
```

### Expected Result

- HTTP `200`
- `filtered = true`
- in `responseBodyJson`, the `Patient` no longer contains the `telecom` field

## 7. Validation Criteria

The test is successful if:

- a `GET /Patient` search with `search` permission returns `ALLOW_WITH_REWRITE`;
- a `PUT /Patient/example-toto` with `update` permission returns `ALLOW`;
- a `PUT` with an inappropriate permission returns `403`;
- filtering correctly removes `Patient.telecom`.

## 8. Recommended Next Step

Once this scenario is validated, the next step is to move to a setup closer to production:

- enable interceptors;
- load permissions from the token or from an external FHIR repository;
- then test real REST requests such as `POST /Patient`, `PUT /Patient/{id}`, and `GET /Patient/{id}`.
