# Permission Engine - Testing and Deployment

## 1. Important Note About the `Permission` Resource

The FHIR `Permission` resource only exists in **R5**.

This changes how the engine should be reasoned about:

- **permissions** are always expressed as **R5** `Permission` resources;
- the **requests and responses** evaluated by the engine can still remain in **R4**;
- the `Permission Engine` can therefore protect an R4 FHIR server, as long as the permission source provides R5 `Permission` resources.

In practice:

- if you use `permissionJson`, the provided JSON must represent an R5 `Permission` resource;
- if you use token claims, embedded permissions must be serialized R5 `Permission` resources;
- if you use a FHIR repository, that repository must return either an R5 `Permission` resource or an R5 `Bundle` containing `Permission` resources.

Important consequence:

- the "look up permissions from the local server itself" mode is only consistent if the local instance actually exposes R5 `Permission` resources;
- for a HAPI server configured as pure R4, it is usually preferable to use one of these options instead:
  - permissions in the token;
  - an external FHIR repository dedicated to `Permission`;
  - an upstream service that transforms business rules into R5 `Permission` resources.

## 2. Supported Modes

The finalized `Permission Engine` now supports three ways to obtain permissions:

1. **Inline `permissionJson`**
   - useful for local tests and debugging
2. **Permissions in the token**
   - through one or more configurable claims
3. **Permissions loaded from a FHIR repository**
   - external, or local if that makes sense in the target architecture

These modes can be combined.

## 3. Where the Implementation Lives

The implementation is located in this repository.

Main locations:

- Spring configuration: `src/main/java/ca/uhn/fhir/jpa/starter/permission/`
- HAPI operations: `PermissionOperationProvider`
- HAPI interceptors: `src/main/java/ca/uhn/fhir/jpa/starter/permission/interceptor/`
- dedicated profile: `profiles/application-permission.yml`

## 4. Run Locally

### Development Mode

From the `hl7-hapi-fhir-jpaserver-starter` repository:

```powershell
mvn -Pboot spring-boot:run "-Dspring-boot.run.arguments=--spring.config.additional-location=file:./profiles/application-permission.yml"
```

### Build Then Run

```powershell
mvn clean package
java -jar target\hapi-fhir-jpaserver-starter-*.war --spring.config.additional-location=file:./profiles/application-permission.yml
```

### Typical Base URL

Locally, the FHIR base URL is usually:

```text
http://localhost:8080/fhir
```

## 5. Configuration

### Base Profile

The current profile is located at:

- `profiles/application-permission.yml`

Important blocks:

- `hapi.fhir.permission.require-token`
- `hapi.fhir.permission.token-validation.*`
- `hapi.fhir.permission.token-source.*`
- `hapi.fhir.permission.repository.*`

### Example 1 - Simple Local Test with `permissionJson`

```yaml
hapi:
  fhir:
    permission:
      enabled: true
      expose-operations: true
      enable-interceptors: true
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

### Example 2 - Permissions in the Token with HMAC Validation

```yaml
hapi:
  fhir:
    permission:
      enabled: true
      require-token: true
      allow-inline-permissions: false
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
          - permission
          - permission_json
      repository:
        enabled: false
```

### Example 3 - External FHIR Repository for Permissions

```yaml
hapi:
  fhir:
    permission:
      enabled: true
      require-token: true
      allow-inline-permissions: false
      token-validation:
        enabled: true
        mode: jwk-set-uri
        jwk-set-uri: https://my-iam.example.org/realms/care/protocol/openid-connect/certs
        issuer: https://my-iam.example.org/realms/care
        audience: permission-engine
      token-source:
        enabled: false
      repository:
        enabled: true
        base-url: https://fhir-permissions.example.org/fhir
        query-template: Permission?status=active&user=${userId}
        reuse-incoming-token: true
        timeout-seconds: 10
```

## 6. Available Operations

The engine exposes two system operations:

1. `$authorize-request`
2. `$filter-response`

## 7. Ready-to-Test Examples

### Example Files

Ready-to-use examples are available in:

- [authorize-inline-r4.json](examples/inline/authorize-inline-r4.json)
- [authorize-update-inline-r4.json](examples/inline/authorize-update-inline-r4.json)
- [filter-inline-r4.json](examples/inline/filter-inline-r4.json)
- [authorize-repository-r4.json](examples/repository/authorize-repository-r4.json)
- [patient-create-toto-r4.json](examples/interceptors/patient-create-toto-r4.json)
- [patient-update-toto-r4.json](examples/interceptors/patient-update-toto-r4.json)
- [permission-patient-crus-r5.json](examples/common/permission-patient-crus-r5.json)
- [permission-filter-telecom-r5.json](examples/common/permission-filter-telecom-r5.json)

### Example 1 - Authorization with Rewriting Through `permissionJson`

Prerequisites:

- `allow-inline-permissions: true`
- `require-token: false`, or a valid token

Call:

```powershell
curl.exe -X POST "http://localhost:8080/fhir/\$authorize-request" ^
  -H "Content-Type: application/fhir+json" ^
  --data-binary "@docs/examples/inline/authorize-inline-r4.json"
```

Expected result:

- `decision = ALLOW_WITH_REWRITE`
- `httpStatus = 200`
- `rewrittenQueryString = name=Toto`

If this example returns `403 Request is not authorized`, the most likely causes are:

- the running HAPI application is not the latest `permission-engine` branch;
- the server was not restarted after the latest changes;
- the loaded configuration profile is not the expected one;
- the `permissionJson` payload was altered in Postman.

Important point:

- with the current code, this test payload is covered by `PermissionOperationTest` and must return `ALLOW_WITH_REWRITE`;
- if `hapi.fhir.permission.require-token=true` is active and no token is provided, the expected behavior is `401`, not `403`.

To remove any doubt in Postman, you can add this parameter:

```json
{
  "name": "authorization",
  "valueString": "Bearer test-token"
}
```

If you still get `403` with the exact example JSON, that points more to a version or loaded-configuration issue than to a payload issue.

### Example 2 - Response Filtering Through `permissionJson`

Prerequisite:

- `allow-inline-permissions: true`

Call:

```powershell
curl.exe -X POST "http://localhost:8080/fhir/\$filter-response" ^
  -H "Content-Type: application/fhir+json" ^
  --data-binary "@docs/examples/inline/filter-inline-r4.json"
```

Expected result:

- `filtered = true`
- the `Patient.telecom` field is removed from the returned response

### Example 3 - Authorization with a FHIR Repository

Prerequisites:

- `repository.enabled: true`
- `repository.base-url` configured
- `repository.query-template` configured

Call:

```powershell
curl.exe -X POST "http://localhost:8080/fhir/\$authorize-request" ^
  -H "Content-Type: application/fhir+json" ^
  --data-binary "@docs/examples/repository/authorize-repository-r4.json"
```

Expected result:

- the engine calls the permission repository;
- if a suitable R5 `Permission` is found, the request is authorized or rewritten;
- otherwise:
  - `403` if the refusal is functional;
  - `502` if the repository response is invalid;
  - `503` if the repository is unavailable.

## 8. Resources to Push When Testing Real REST Operations

### Important

To test a real `POST /Patient`, `PUT /Patient/{id}`, or `GET /Patient/{id}` with interceptors:

- `permissionJson` is not used;
- permissions must come from the token, the context, or a FHIR permission repository;
- if the local server is pure R4, do not try to create the R5 `Permission` resource directly on it.

### R4 Patient Resource to Create

Use:

- [patient-create-toto-r4.json](examples/interceptors/patient-create-toto-r4.json)

Example:

```powershell
curl.exe -X POST "http://localhost:8080/fhir/Patient" ^
  -H "Content-Type: application/fhir+json" ^
  -H "X-User-Id: permission-user" ^
  --data-binary "@docs/examples/interceptors/patient-create-toto-r4.json"
```

### R4 Patient Resource to Update

Use:

- [patient-update-toto-r4.json](examples/interceptors/patient-update-toto-r4.json)

Example:

```powershell
curl.exe -X PUT "http://localhost:8080/fhir/Patient/example-toto" ^
  -H "Content-Type: application/fhir+json" ^
  -H "X-User-Id: permission-user" ^
  --data-binary "@docs/examples/interceptors/patient-update-toto-r4.json"
```

### R5 Permission that Allows `create`, `read`, `update`, `search`

Use:

- [permission-patient-crus-r5.json](examples/common/permission-patient-crus-r5.json)

This resource is intended to:

- be injected into a token claim;
- be served by an external R5 FHIR repository;
- or be reused as-is inside `permissionJson` during `$authorize-request` tests.

### R5 Permission that Filters `Patient.telecom`

Use:

- [permission-filter-telecom-r5.json](examples/common/permission-filter-telecom-r5.json)

This resource is used to test response filtering on a `Patient`.

### Verify Authorization for a `PUT` Before Sending the Real Request

Use:

- [authorize-update-inline-r4.json](examples/inline/authorize-update-inline-r4.json)

Example:

```powershell
curl.exe -X POST "http://localhost:8080/fhir/\$authorize-request" ^
  -H "Content-Type: application/fhir+json" ^
  --data-binary "@docs/examples/inline/authorize-update-inline-r4.json"
```

Expected result:

- `decision = ALLOW`
- `httpStatus = 200`

### Recommended Test Sequence

1. First test `$authorize-request` with `authorize-inline-r4.json`, then `authorize-update-inline-r4.json`.
2. Then verify that the real permission source returns a permission equivalent to [permission-patient-crus-r5.json](examples/common/permission-patient-crus-r5.json).
3. Create the patient with [patient-create-toto-r4.json](examples/interceptors/patient-create-toto-r4.json).
4. Read or search the patient.
5. Update the patient with [patient-update-toto-r4.json](examples/interceptors/patient-update-toto-r4.json).
6. Finally test response filtering with [permission-filter-telecom-r5.json](examples/common/permission-filter-telecom-r5.json).

## 9. Example JWT Claim Structure

The engine can read permissions from a claim such as `permissions`.

The claim can contain:

- a list of serialized JSON strings for R5 `Permission` resources;
- or a list of JSON objects that directly represent R5 `Permission` resources.

Conceptual example:

```json
{
  "sub": "permission-user",
  "preferred_username": "permission-user",
  "permissions": [
    "{\"resourceType\":\"Permission\",\"status\":\"active\",\"rule\":[{\"type\":\"permit\",\"activity\":[{\"action\":[{\"coding\":[{\"code\":\"search\"}]}]}],\"data\":[{\"resource\":[{\"meaning\":\"instance\",\"reference\":{\"display\":\"Patient\"}}],\"expression\":{\"language\":\"application/x-fhir-query\",\"expression\":\"name=Toto\"}}]}]}"
  ]
}
```

## 10. Return Codes

### `$authorize-request`

- `200`
  - access granted, with or without rewriting
- `401`
  - missing token when required
  - invalid token
- `403`
  - authenticated user but not authorized
- `502`
  - permission repository reachable but returned an invalid response
- `503`
  - permission repository unavailable or timed out

### `$filter-response`

- `200`
  - response filtered or unchanged
- `401`
  - token required but missing or invalid
- `502` / `503`
  - permission repository issue if that source is enabled

## 11. Automated Tests Already Available

Useful tests in the HAPI branch:

- `PermissionOperationTest`
  - HAPI operations with inline `permissionJson`
- `PermissionInterceptorTest`
  - interceptor mode in the HAPI pipeline
- `PermissionJwtOperationTest`
  - validation of a real HMAC JWT and loading permissions from claims
- `PermissionRepositoryOperationTest`
  - loading permissions from a mocked FHIR repository

Verification command:

```powershell
mvn "-Dtest=PermissionOperationTest,PermissionInterceptorTest,PermissionJwtOperationTest,PermissionRepositoryOperationTest" test
```

## 12. Architecture Recommendation

Since `Permission` is an R5-only resource, the most robust architecture is usually:

1. a protected business FHIR server in R4 if needed;
2. a `Permission Engine` integrated into HAPI;
3. permissions provided:
   - either by the token;
   - or by an external FHIR repository dedicated to R5 `Permission`;
4. Mirth, a gateway, or a reverse proxy that calls the engine or relies on the interceptors.

For a first simple deployment:

- use **HAPI operations** for testing;
- then enable **interceptors** for server integration;
- and prefer **token claims** or an **external FHIR repository** rather than local lookup on a pure R4 server.
