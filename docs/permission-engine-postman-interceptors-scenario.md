# Permission Engine - Postman Scenario with Interceptors

This scenario tests the `Permission Engine` in the mode that is closest to production:

- `enable-interceptors: true`
- permissions loaded from the token
- real FHIR REST requests
- search rewriting
- response filtering
- rejection of unauthorized operations

In this mode:

- the main tests no longer call `$authorize-request` or `$filter-response`;
- you call FHIR endpoints directly such as `POST /Patient`, `GET /Patient`, `GET /Patient/{id}`, and `PUT /Patient/{id}`;
- the token is passed in the actual HTTP `Authorization` header.

## 1. Profile Configuration

In `profiles/application-permission.yml`, use this configuration:

```yaml
hapi:
  fhir:
    permission:
      enabled: true
      expose-operations: true
      enable-interceptors: true
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

## 3. Useful Files

- [generate-permission-token.ps1](examples/common/generate-permission-token.ps1)
- [generated-token.txt](examples/common/generated-token.txt)
- [permission-patient-crus-and-filter-r5.json](examples/interceptors/permission-patient-crus-and-filter-r5.json)
- [permission-patient-search-only-r5.json](examples/interceptors/permission-patient-search-only-r5.json)
- [patient-create-toto-r4.json](examples/interceptors/patient-create-toto-r4.json)
- [patient-create-bob-r4.json](examples/interceptors/patient-create-bob-r4.json)
- [patient-update-toto-r4.json](examples/interceptors/patient-update-toto-r4.json)
- [patient-update-toto-r4-template.json](examples/interceptors/patient-update-toto-r4-template.json)

## 4. Generate Tokens

### Main Token

This token authorizes:

- `create`
- `read`
- `update`
- `search`

It also applies:

- a search restriction: `name=Toto`
- filtering of `Patient.telecom`

Important:

- the `deny` filtering rule used here does not include an `activity`;
- in the current engine, adding `read` or `search` to that rule would reject the whole request with `403` instead of only filtering the response.

Command:

```powershell
Set-ExecutionPolicy -Scope Process Bypass
& .\docs\examples\common\generate-permission-token.ps1 -PermissionFile "..\interceptors\permission-patient-crus-and-filter-r5.json" -OutputFile "generated-token.txt"
```

The token is written to:

- [generated-token.txt](examples/common/generated-token.txt)

### Restricted Token

This token only authorizes `search`.

Command:

```powershell
Set-ExecutionPolicy -Scope Process Bypass
& .\docs\examples\common\generate-permission-token.ps1 -PermissionFile "..\interceptors\permission-patient-search-only-r5.json" -OutputFile "generated-search-only-token.txt"
```

## 5. Prepare Postman

Create a Postman variable, for example:

- `permissionToken`

Paste the content of `docs/examples/common/generated-token.txt` into it.

Create a second variable if needed:

- `permissionSearchOnlyToken`

Paste the content of `docs/examples/common/generated-search-only-token.txt` into it.

Also create an empty Postman variable:

- `patientTotoId`

In the requests below, use this header:

```text
Authorization: Bearer {{permissionToken}}
```

Or, for the negative test:

```text
Authorization: Bearer {{permissionSearchOnlyToken}}
```

## 6. Test 1 - Create the Authorized Patient

### Request

- Method: `POST`
- URL: `http://localhost:8080/fhir/Patient`
- Headers:
  - `Content-Type: application/fhir+json`
  - `Authorization: Bearer {{permissionToken}}`

Body:

- [patient-create-toto-r4.json](examples/interceptors/patient-create-toto-r4.json)

### Expected Result

- HTTP `201` in the standard HAPI case
- the resource is created with a server-generated identifier, for example `Patient/1000`
- no authorization rejection

Important:

- in HAPI, `POST /Patient` does not necessarily use the `id` present in the body;
- you must capture the actual identifier created by the server for the rest of the scenario.

## 7. Test 2 - Create a Second Patient to Verify Search Restriction

### Request

- Method: `POST`
- URL: `http://localhost:8080/fhir/Patient`
- Headers:
  - `Content-Type: application/fhir+json`
  - `Authorization: Bearer {{permissionToken}}`

Body:

- [patient-create-bob-r4.json](examples/interceptors/patient-create-bob-r4.json)

### Expected Result

- HTTP `201`
- the `Patient/{serverId}` resource is created

## 8. Test 3 - Search Patients

### Request

- Method: `GET`
- URL: `http://localhost:8080/fhir/Patient`
- Headers:
  - `Authorization: Bearer {{permissionToken}}`

### Expected Result

- HTTP `200`
- the engine implicitly rewrites the search with `name=Toto`
- the returned `Bundle` only contains `example-toto`
- the `telecom` field does not appear in the response

In other words:

- `example-bob` must not appear;
- `example-toto` must appear;
- `telecom` must be absent.

Postman action:

- capture `entry[0].resource.id` from the response;
- store that value in the `patientTotoId` variable.

With your example, the correct value is:

```text
1000
```

## 9. Test 4 - Read the Authorized Patient

### Request

- Method: `GET`
- URL: `http://localhost:8080/fhir/Patient/{{patientTotoId}}`
- Headers:
  - `Authorization: Bearer {{permissionToken}}`

### Expected Result

- HTTP `200`
- the `Patient` is returned
- the `telecom` field is removed by filtering
- the name remains visible

## 10. Test 5 - Update the Authorized Patient

### Request

- Method: `PUT`
- URL: `http://localhost:8080/fhir/Patient/{{patientTotoId}}`
- Headers:
  - `Content-Type: application/fhir+json`
  - `Authorization: Bearer {{permissionToken}}`

Body:

- [patient-update-toto-r4-template.json](examples/interceptors/patient-update-toto-r4-template.json)

Important:

- the `PUT` body must use the same `id` as the URL;
- the provided template uses `{{patientTotoId}}` to stay consistent with the identifier created by HAPI.

### Expected Result

- HTTP `200`
- the update is accepted
- the patient version is incremented

## 11. Test 6 - Read the Updated Patient Again

### Request

- Method: `GET`
- URL: `http://localhost:8080/fhir/Patient/{{patientTotoId}}`
- Headers:
  - `Authorization: Bearer {{permissionToken}}`

### Expected Result

- HTTP `200`
- the family name is `Allowed-Updated`
- `telecom` is still absent

## 12. Test 7 - Reject an Update with an Insufficient Token

### Request

- Method: `PUT`
- URL: `http://localhost:8080/fhir/Patient/{{patientTotoId}}`
- Headers:
  - `Content-Type: application/fhir+json`
  - `Authorization: Bearer {{permissionSearchOnlyToken}}`

Body:

- [patient-update-toto-r4-template.json](examples/interceptors/patient-update-toto-r4-template.json)

### Expected Result

- HTTP `403`
- `OperationOutcome` resource
- a message close to `Request is not authorized`

## 13. Test 8 - Reject a Request Without a Token

### Request

- Method: `GET`
- URL: `http://localhost:8080/fhir/Patient/{{patientTotoId}}`
- no `Authorization` header

### Expected Result

- HTTP `401`
- `OperationOutcome` resource

## 14. Test 9 - Reject an Invalid Token

### Request

- Method: `GET`
- URL: `http://localhost:8080/fhir/Patient/{{patientTotoId}}`
- Header:
  - `Authorization: Bearer {{permissionToken}}`

Then intentionally modify one character in the token.

### Expected Result

- HTTP `401`
- `OperationOutcome` resource

## 15. Validation Criteria

The scenario is successful if:

- `POST /Patient` works with the main token;
- `GET /Patient` only returns `Toto`;
- `GET /Patient/{{patientTotoId}}` hides `telecom`;
- `PUT /Patient/{{patientTotoId}}` works with the main token;
- the same `PUT` fails with the restricted token;
- missing token returns `401`;
- an invalid token returns `401`.

## 16. Important Note

In this scenario, search restriction and response filtering are invisible to the client as separate steps:

- the incoming interceptor rewrites the search;
- the outgoing interceptor filters the response;
- the Postman client only sees the final result.

This is exactly what makes this scenario close to the expected platform behavior.
