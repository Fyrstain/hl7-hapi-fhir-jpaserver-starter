# Permission Engine - Postman Scenario with Token-Based Permissions

This scenario tests the `Permission Engine` with permissions loaded from an `HS256`-signed JWT.

The goal is to validate:

- the presence of a token;
- token validation;
- reading permissions from the `permissions` claim;
- authorization of a search;
- authorization of an update;
- rejection of an invalid token.

## 1. Profile Configuration

In `profiles/application-permission.yml`, use this configuration:

```yaml
hapi:
  fhir:
    permission:
      enabled: true
      expose-operations: true
      enable-interceptors: false
      allow-inline-permissions: false
      require-token: true
      fail-closed: true
      default-fhir-version: R4
      token-validation:
        enabled: true
        mode: hmac
        shared-secret: permission-secret-1234567890-abcdef
        issuer: permission-test
        audience: permission-engine
        principal-claim: sub
        display-name-claim: preferred_username
        organization-claim: organization_id
        role-claims:
          - realm_access.roles
          - roles
          - scope
          - scp
      token-source:
        enabled: true
        claim-names:
          - permissions
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

## 3. Files Used

Useful files:

- [permission-patient-crus-r5.json](examples/common/permission-patient-crus-r5.json)
- [generate-permission-token.ps1](examples/common/generate-permission-token.ps1)
- [authorize-token-search-r4-template.json](examples/token/authorize-token-search-r4-template.json)
- [authorize-token-update-r4-template.json](examples/token/authorize-token-update-r4-template.json)
- [filter-token-r4-template.json](examples/token/filter-token-r4-template.json)

## 4. Generate a Test Token

From the `hl7-hapi-fhir-jpaserver-starter` repository, run:

```powershell
Set-ExecutionPolicy -Scope Process Bypass
& .\docs\examples\common\generate-permission-token.ps1
```

This script:

- reads [permission-patient-crus-r5.json](examples/common/permission-patient-crus-r5.json);
- places its content into the `permissions` claim;
- signs the JWT with `HS256` using the secret `permission-secret-1234567890-abcdef`;
- prints the result to the console with the `TOKEN:` prefix;
- also writes the token automatically to:
  - [generated-token.txt](examples/common/generated-token.txt)

Expected result:

- console output in the form `TOKEN: eyJ...`;
- a `docs/examples/common/generated-token.txt` file containing only the JWT;
- a JWT string ready to copy into Postman.

## 5. Important Note for Postman

For `$authorize-request` and `$filter-response`, the engine reads the token from the `Parameters` body through:

- `authorization`
- `token`

In this scenario, the token must be placed in the JSON body, not only in the HTTP `Authorization` header.

## 6. Test 1 - Authorize a Search from the Token

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
      "name": "authorization",
      "valueString": "Bearer <PASTE_THE_TOKEN_HERE>"
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
    }
  ]
}
```

### Expected Result

- HTTP `200`
- `decision = ALLOW_WITH_REWRITE`
- `allowed = true`
- `rewrittenQueryString = name=Toto`

## 7. Test 2 - Authorize an Update from the Token

The token generated from [permission-patient-crus-r5.json](examples/common/permission-patient-crus-r5.json) also authorizes `update`.

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
      "name": "authorization",
      "valueString": "Bearer <PASTE_THE_TOKEN_HERE>"
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
    }
  ]
}
```

### Expected Result

- HTTP `200`
- `decision = ALLOW`
- `allowed = true`

## 8. Test 3 - Verify Rejection of an Invalid Token

Repeat the previous test, but either:

- modify one character in the token;
- or regenerate a token with a different secret.

### Expected Result

- HTTP `401`
- `OperationOutcome` resource
- an authentication or token-validation error

## 9. Test 4 - Verify Rejection When the Token Is Missing

Repeat a `$authorize-request` call without the `authorization` parameter.

### Expected Result

- HTTP `401`
- `OperationOutcome` resource

## 10. Variant - Test Filtering with a Token

For this test, generate a second token from a filtering permission.

Command:

```powershell
Set-ExecutionPolicy -Scope Process Bypass
& .\docs\examples\common\generate-permission-token.ps1 -PermissionFile "permission-filter-telecom-r5.json" -OutputFile "generated-filter-token.txt"
```

Then send:

```json
{
  "resourceType": "Parameters",
  "parameter": [
    {
      "name": "fhirVersion",
      "valueCode": "R4"
    },
    {
      "name": "authorization",
      "valueString": "Bearer <PASTE_THE_FILTER_TOKEN_HERE>"
    },
    {
      "name": "statusCode",
      "valueInteger": 200
    },
    {
      "name": "responseBodyJson",
      "valueString": "{\"resourceType\":\"Patient\",\"id\":\"example-toto\",\"name\":[{\"family\":\"Allowed\",\"given\":[\"Toto\"]}],\"telecom\":[{\"system\":\"phone\",\"value\":\"0102030405\"}]}"
    }
  ]
}
```

URL:

- `http://localhost:8080/fhir/$filter-response`

Expected result:

- HTTP `200`
- `filtered = true`
- `responseBodyJson` no longer contains `telecom`

Important:

- the filtering permission used here is intentionally a `deny` rule without `activity`;
- in the current engine, adding `read` or `search` to that rule may reject the request with `403` instead of simply filtering it.

## 11. Validation Criteria

The scenario is successful if:

- the valid token authorizes the search;
- the valid token authorizes the update;
- the invalid token returns `401`;
- a missing token returns `401`;
- the filtering token correctly removes `Patient.telecom`.

## 12. Recommended Next Step

Once this scenario is validated, the most useful next step is:

- enable `enable-interceptors: true`;
- keep `require-token: true`;
- then test real REST requests with the HTTP header `Authorization: Bearer ...`.
