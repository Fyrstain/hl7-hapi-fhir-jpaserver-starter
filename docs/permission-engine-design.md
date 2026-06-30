# Permission Engine Microservice - Design

## 1. Purpose

This document defines the target transformation of the current `permission-module` into a standalone FHIR access-control microservice, hereafter called the `Permission Engine`.

The goal is to reuse as much of the existing core as possible in order to provide a deployable service able to:

- evaluate a FHIR request based on a token and an HTTP context;
- allow, restrict, or reject the request;
- enrich or rewrite search parameters when access is partial;
- filter the FHIR response before it is returned to the client;
- integrate easily behind a gateway or reverse proxy.

## 2. Scope

### Included

- exposing the existing engine as an HTTP microservice;
- extracting and interpreting authorization data from the token or upstream context;
- pre-request evaluation;
- search-parameter rewriting;
- post-response filtering;
- externalized configuration;
- unit, regression, and integration testing;
- deployment and integration documentation.

### Excluded

- a full rewrite of the authorization model;
- replacing Keycloak or the authentication provider;
- a full API Gateway implementation;
- redefining the business permission model if it already exists in `Permission` resources.

## 3. Current-State Analysis

### 3.1 Current Structure

The original repository currently contains a Java library core without a microservice layer:

- `com.fyrstain.fhir.security.core.FhirAuthorizationEngine`
  - main engine entry point
- `PermissionService`
  - abstraction used to retrieve `Permission` resources associated with the user
- `PermissionEvaluator`
  - abstraction for permission compilation, request evaluation, and response filtering
- `SimplePermissionEvaluator`
  - generic implementation for rule compilation, `allow/deny` decision making, and search-parameter merging
- `SimpleR4PermissionEvaluator` and `SimpleR5PermissionEvaluator`
  - FHIR-version-specific response-filtering support
- `FhirRequest`, `FhirResponse`, `PermissionContext`, `RequestEvaluationResult`
  - engine input and output model
- `adapter.iris.JavaPermissionFacade`
  - static technical facade, not directly usable as a microservice API

### 3.2 Responsibilities Already Covered

The module already covers an important part of the need:

- translation of a functional context into `PermissionContext`;
- permission evaluation on a FHIR operation;
- search-parameter enrichment from `PERMIT` rules using `application/x-fhir-query`;
- response filtering from `DENY` rules using `FHIRPath` expressions;
- support for filtering both single resources and `Bundle` responses;
- a unit-test foundation for rule compilation, parameter rewriting, and filtering.

### 3.3 Current Engine Behavior

#### Request Evaluation

`FhirAuthorizationEngine.evaluateRequest(...)`:

1. asks `PermissionService` for the list of `Permission` resources matching the context;
2. compiles those resources into `PermissionRule`;
3. checks whether the requested operation is authorized;
4. calculates additional search parameters imposed by permissions;
5. returns a `RequestEvaluationResult`.

#### Response Filtering

`FhirAuthorizationEngine.filterResponse(...)`:

1. reloads permissions for the same context;
2. compiles the rules;
3. applies blacklist expressions on the resource or on each `Bundle` entry.

### 3.4 Strengths of the Current Implementation

- the decision core is already separated into clear abstractions;
- the main logic is framework-agnostic;
- the code already supports R4 and R5 for filtering;
- the Maven packaging and Java 17 baseline are compatible with rapid industrialization;
- regression tests already exist for core behaviors.

### 3.5 Gaps Compared with the Target

The current implementation does not yet meet the criteria of an operational microservice:

- no HTTP API is exposed;
- no token validation is implemented;
- `PermissionService` has no real implementation connected to the platform;
- the `JavaPermissionFacade` uses a static `MockPermissionService`;
- the output contract is too limited for a gateway:
  - only `allowed` and `modifiedSearchParameters`
  - no HTTP status
  - no denial reason
  - no explicit distinction between `ALLOW` and `ALLOW_WITH_RESTRICTIONS`
- the permission-combination strategy is simplified:
  - no full support for `deny-overrides`, `permit-overrides`, and similar semantics
- filtering mainly covers field removal, not advanced policies such as full-entry removal or more complex masking;
- there is no externalized configuration;
- there are no HTTP integration tests;
- there is no health endpoint or local deployment scenario.

### 3.6 Functional Capabilities Already Observed

The following behaviors are already visible in the code and tests:

- a default policy close to `deny by default` when no rule applies;
- extraction of operations such as `read`, `search`, `create`, `update`, `delete`, `patch`, `metadata`, and `custom`;
- support for `application/x-fhir-query` search expressions used to restrict searches;
- support for `FHIRPath` expressions used to remove response elements;
- support for filtering both `Bundle` responses and single resources;
- support for carrying the FHIR body for some write operations in the request model.

### 3.7 Reusable Code Versus Code to Refactor

#### Reuse As-Is or with Minimal Change

- `FhirAuthorizationEngine`
- `PermissionEvaluator`
- `SimplePermissionEvaluator`
- `SimpleR4PermissionEvaluator`
- `SimpleR5PermissionEvaluator`
- R4/R5 `FilterUtils`
- the base business models, which should evolve rather than be replaced

#### Refactor

- `RequestEvaluationResult`
  - enrich the decision contract
- `PermissionContext`
  - add user/client claims useful for integration
- `JavaPermissionFacade`
  - replace it with real REST or HAPI endpoints
- `PermissionService`
  - provide a real implementation connected to the actual permission source
- error handling
  - introduce an explicit mapping layer to HTTP status codes

## 4. Integration Assumptions

### 4.1 Recommended Assumption

The `Permission Engine` is called by the gateway or reverse proxy in two phases:

1. before calling the FHIR repository, for decision making and URL rewriting;
2. after receiving the response from the FHIR repository, to filter the content.

This approach keeps the service:

- stateless;
- independent from the gateway;
- reusable from multiple entry points;
- easy to test locally.

### 4.2 Responsibility Split

#### Authentication Layer

- authenticates the client;
- verifies the token signature, expiration, and issuer;
- forwards the raw token or claims to the gateway.

#### API Gateway / Reverse Proxy

- receives the client request;
- calls the `Permission Engine` before the FHIR backend;
- applies URL rewriting or blocks the request;
- calls the FHIR backend;
- calls the `Permission Engine` to filter the response;
- returns the final response to the client.

#### Permission Engine

- interprets the received authorization context;
- retrieves applicable permissions;
- decides whether the operation is allowed;
- produces a restricted version of query parameters if needed;
- filters the FHIR response according to permissions.

#### Target FHIR Repository

- remains responsible for business FHIR processing;
- does not embed the `Permission Engine` filtering logic.

## 5. Target Architecture

### 5.1 Guiding Principle

Keep the existing core as the decision domain and add a clear, minimally intrusive microservice layer around it.

Three integration modes should be distinguished from the start:

- a `dedicated standalone service` mode, deployed separately with its own image and configuration;
- a `standalone service based on HAPI FHIR` mode, inspired by the extension pattern already used for the mapping engine;
- an `embedded in the business FHIR backend` mode, which is more tightly coupled to the target server.

### 5.2 Recommended Style

Java 17 REST microservice based on Spring Boot 3.x.

Why:

- fast addition of an HTTP API;
- strong support for external configuration and profiles;
- simple integration testing;
- easy health and observability endpoints;
- natural compatibility with future JWT validation or introspection.

### 5.3 Logical Architecture

```text
Client / Gateway
    |
    v
Permission Engine REST API
    |
    +-- Request Controller
    |     |
    |     +-- Token Context Extractor
    |     +-- Permission Context Mapper
    |     +-- Authorization Service
    |             |
    |             +-- PermissionService implementation
    |             +-- FhirAuthorizationEngine
    |
    +-- Response Filter Controller
          |
          +-- FHIR Parser Service
          +-- Authorization Service
                  |
                  +-- FhirAuthorizationEngine
```

### 5.4 Proposed Technical Breakdown

#### API Layer

- `AuthorizationController`
  - pre-authorization endpoint
- `ResponseFilterController`
  - post-filtering endpoint
- `HealthController` or Spring Boot actuator

#### Application Layer

- `PermissionDecisionService`
  - orchestrates the `authorize` and `filter` use cases
- `TokenContextExtractor`
  - extracts claims from the token or forwarded headers
- `UrlRewriteService`
  - rebuilds query parameters and possibly the final URL

#### Domain Layer

- `FhirAuthorizationEngine`
- `PermissionEvaluator` and its implementations
- permission and result models

#### Infrastructure Layer

- `PermissionService` implementation
  - access to the real permission source
- `JwtDecoder` or an introspection client if the service must validate the token
- Spring configuration
- FHIR serialization/deserialization

## 5.5 HAPI FHIR Starter Integration Variant

### Principle

A realistic variant is to integrate the `Permission Engine` directly into the `hl7-hapi-fhir-jpaserver-starter` project on a dedicated branch created from `master`, following the same extension pattern as the mapping engine.

This approach does not contradict component autonomy if the final result is:

- deployed separately from the business FHIR repository;
- packaged in its own Docker image;
- configured independently;
- called by Mirth, a gateway, or another orchestration component.

The pattern already observed on the mapping side relies on:

- property-based conditional activation;
- dedicated Spring configuration;
- module-specific properties;
- optional registration in `RestfulServer`.

This pattern can be reused for the permission engine.

### Recommended Branch Shape

A dedicated branch such as `permission-engine` can be created from `master` with a technical module structured like this:

- `ca.uhn.fhir.jpa.starter.permission.PermissionConfigCondition`
- `ca.uhn.fhir.jpa.starter.permission.PermissionConfig`
- `ca.uhn.fhir.jpa.starter.permission.PermissionProperties`
- `ca.uhn.fhir.jpa.starter.permission.interceptor.PermissionRequestInterceptor`
- `ca.uhn.fhir.jpa.starter.permission.interceptor.PermissionResponseInterceptor`
- an adapter between HAPI `RequestDetails` and `FhirRequest`
- a real implementation of `PermissionService`
- a dedicated configuration profile, for example `application-permission.yml`

### Technical Feasibility

This integration is feasible.

Strong points:

- the HAPI starter already has a clean modular extension mechanism, visible with the mapping engine;
- the permission module already has a reusable business core;
- HAPI supports request and response interception;
- HAPI context objects allow request-parameter reading and modification.

More specifically, on the HAPI side, request context already supports:

- reading the request type;
- reading headers, including authorization;
- reading and modifying parameters;
- accessing the resource type, operation, and URL.

### Recommended Technical Integration Mode in HAPI

Unlike the mapping engine, which mainly exposes a `$transform` operation, the permission engine should not be designed primarily as an operation provider.

The right anchor point in HAPI is instead:

- a request pre-processing interceptor to:
  - extract the token;
  - build the `PermissionContext`;
  - evaluate the request;
  - reject with `401` or `403`;
  - rewrite search parameters when needed;
- a response post-processing interceptor to:
  - retrieve the returned resource or `Bundle`;
  - apply content filtering before returning it to the client.

This mode is especially well suited to a dedicated HAPI-based `Permission Engine`, exposed as a standalone service on the platform and then called from a Mirth channel or an equivalent orchestration layer.

### Limits of This Variant

This approach is relevant for HAPI integration, but two cases must be distinguished.

#### Case 1 - HAPI Server Dedicated to the Permission Engine

In this case, autonomy remains compatible with the need:

- the component has its own lifecycle;
- it can be delivered as a dedicated Docker image;
- it can be deployed separately on the platform;
- it can be called by Mirth or a gateway to evaluate and then filter.

#### Case 2 - Module Embedded in the Business FHIR Backend

In this case, the following limits appear:

- the logic remains embedded in the target FHIR server;
- the component is less easy to reuse across multiple backends;
- coupling to the HAPI runtime is stronger;
- the responsibility split between the FHIR repository and the permission engine is less clear.

Conclusion: the HAPI variant is fully compatible with the autonomy goal if it is deployed as a separate service. What would really reduce autonomy is embedding it directly into the business FHIR backend.

### Technical Gaps to Address Before HAPI Integration

Before porting the module into the HAPI starter, several gaps must be addressed:

- no real permission source behind `PermissionService`;
- no token validation and extraction;
- a result contract that is still too limited in `RequestEvaluationResult`;
- the need to replace the existing technical facade with a native HAPI adapter;
- the need to align dependencies and Java level across the two projects.

### Observed Technical Compatibility

The analysis of the two projects shows a difference in technical baseline:

- `permission-module`
  - Java 17
  - HAPI FHIR `8.4.0`
- `hl7-hapi-fhir-jpaserver-starter`
  - Java 21
  - HAPI FHIR `8.8.0`

This difference does not block feasibility, but it requires:

- alignment of HAPI dependencies;
- revalidation of imports and used APIs;
- regression testing after the port.

### Design Recommendation

Two paths are possible:

- Path A
  - first build a dedicated standalone service, then provide an adapter or calling mode from HAPI
- Path B
  - directly build a standalone `Permission Engine` on top of HAPI, on a dedicated branch, then deploy it separately on the platform

Both paths are compatible with the need. Path A maximizes technical separation from the beginning. Path B is especially relevant if the goal is to reproduce the mapping-engine model with a similar Docker deployment on the healthcare platform.

## 6. Proposed Input / Output Contract

The input/output contract depends on the chosen exposure mode.

Two shapes are possible:

- a simple technical HTTP contract, suitable for Mirth, gateway, or reverse-proxy orchestration;
- a contract exposed on top of HAPI FHIR, preferably as system operations.

The transported business model remains the same in both cases:

- authorization context;
- request HTTP / FHIR context;
- decision;
- rewritten parameters;
- filtered FHIR content.

### 6.1 Option A - Simple Technical HTTP Contract

This option remains suitable if the service is standalone and called by Mirth or a gateway as a specialized technical component.

#### Pre-Authorization Endpoint

`POST /api/v1/authorize`

#### Input

```json
{
  "requestId": "2c5f2bd6-2a9e-4abc-b54e-c5d2408b32f5",
  "fhirVersion": "R4",
  "authorization": {
    "token": "Bearer eyJ...",
    "userId": "Practitioner/123",
    "clientId": "gateway",
    "roles": ["ROLE_FHIR_READ"],
    "organizationId": "Organization/45"
  },
  "http": {
    "method": "GET",
    "url": "/fhir/Patient?name=Doe",
    "resourceType": "Patient",
    "resourceId": null,
    "operationName": null,
    "queryParameters": {
      "name": ["Doe"]
    }
  }
}
```

#### Output

```json
{
  "decision": "ALLOW_WITH_REWRITE",
  "allowed": true,
  "httpStatus": 200,
  "reasonCode": "SEARCH_SCOPE_RESTRICTED",
  "rewrittenQueryParameters": {
    "name": ["Doe"],
    "organization": ["Organization/45"]
  },
  "rewrittenUrl": "/fhir/Patient?name=Doe&organization=Organization%2F45",
  "warnings": []
}
```

#### Possible Decisions

- `ALLOW`
  - the request may continue without modification
- `ALLOW_WITH_REWRITE`
  - the request is allowed but must be restricted
- `DENY`
  - the request must not be forwarded to the backend

#### Response-Filtering Endpoint

`POST /api/v1/filter-response`

#### Input

```json
{
  "requestId": "2c5f2bd6-2a9e-4abc-b54e-c5d2408b32f5",
  "fhirVersion": "R4",
  "authorization": {
    "token": "Bearer eyJ...",
    "userId": "Practitioner/123",
    "clientId": "gateway",
    "roles": ["ROLE_FHIR_READ"],
    "organizationId": "Organization/45"
  },
  "http": {
    "method": "GET",
    "resourceType": "Patient",
    "resourceId": null,
    "operationName": null,
    "queryParameters": {
      "name": ["Doe"],
      "organization": ["Organization/45"]
    }
  },
  "response": {
    "statusCode": 200,
    "contentType": "application/fhir+json",
    "body": "{...}"
  }
}
```

#### Output

```json
{
  "httpStatus": 200,
  "contentType": "application/fhir+json",
  "filtered": true,
  "body": "{...}"
}
```

### 6.2 Option B - Contract Exposed on Top of HAPI FHIR

If the `Permission Engine` is implemented on a dedicated HAPI server, it is preferable to expose a contract compatible with HAPI / FHIR mechanisms instead of artificially duplicating classic MVC endpoints.

In this option, the recommendation is to expose system operations such as:

- `POST [base]/$authorize-request`
- `POST [base]/$filter-response`

Input and output can be carried by a `Parameters` resource, which offers several advantages:

- consistency with HAPI patterns;
- native FHIR serialization;
- better functional traceability;
- simpler extension if the contract evolves.

#### Example HAPI Authorization Input

```json
{
  "resourceType": "Parameters",
  "parameter": [
    { "name": "requestId", "valueString": "2c5f2bd6-2a9e-4abc-b54e-c5d2408b32f5" },
    { "name": "fhirVersion", "valueCode": "R4" },
    { "name": "method", "valueCode": "GET" },
    { "name": "url", "valueUrl": "/fhir/Patient?name=Doe" },
    { "name": "resourceType", "valueCode": "Patient" },
    { "name": "authorization", "valueString": "Bearer eyJ..." },
    {
      "name": "queryParameters",
      "part": [
        { "name": "name", "valueString": "Doe" }
      ]
    }
  ]
}
```

#### Example HAPI Authorization Output

```json
{
  "resourceType": "Parameters",
  "parameter": [
    { "name": "decision", "valueCode": "ALLOW_WITH_REWRITE" },
    { "name": "allowed", "valueBoolean": true },
    { "name": "httpStatus", "valueInteger": 200 },
    { "name": "reasonCode", "valueCode": "SEARCH_SCOPE_RESTRICTED" },
    {
      "name": "rewrittenQueryParameters",
      "part": [
        { "name": "name", "valueString": "Doe" },
        { "name": "organization", "valueString": "Organization/45" }
      ]
    },
    {
      "name": "rewrittenUrl",
      "valueUrl": "/fhir/Patient?name=Doe&organization=Organization%2F45"
    }
  ]
}
```

#### Example HAPI Filtering Input

```json
{
  "resourceType": "Parameters",
  "parameter": [
    { "name": "requestId", "valueString": "2c5f2bd6-2a9e-4abc-b54e-c5d2408b32f5" },
    { "name": "fhirVersion", "valueCode": "R4" },
    { "name": "authorization", "valueString": "Bearer eyJ..." },
    { "name": "statusCode", "valueInteger": 200 },
    { "name": "responseBody", "resource": { "resourceType": "Bundle" } }
  ]
}
```

#### Example HAPI Filtering Output

```json
{
  "resourceType": "Parameters",
  "parameter": [
    { "name": "httpStatus", "valueInteger": 200 },
    { "name": "filtered", "valueBoolean": true },
    { "name": "responseBody", "resource": { "resourceType": "Bundle" } }
  ]
}
```

### 6.3 Contract Recommendation

If the service is built on top of HAPI FHIR, the recommendation is:

- use `Parameters` and system operations for the public HAPI interface;
- keep a clear internal application model equivalent to `PermissionContext`, `FhirRequest`, `FhirResponse`, and `RequestEvaluationResult`;
- reserve simple technical REST endpoints for the case where a non-FHIR facade is explicitly chosen.

In other words:

- the external contract may become FHIR-native;
- the business core must not depend directly on the `Parameters` format.

### 6.4 Points to Watch for HAPI Input / Output

- define precisely how repeated query parameters are carried in `Parameters`;
- decide whether the filtered response body is returned as a native FHIR resource or in serialized form;
- define the error strategy:
  - native HAPI exception
  - or explicit business response inside `Parameters`
- choose whether Mirth calls the service as a simple HTTP endpoint or as a FHIR operation endpoint.

### 6.5 Technical Endpoints

- `GET /actuator/health`
- `GET /actuator/info`

## 7. Error-Code Strategy

### 7.1 Authentication Errors

Return `401 Unauthorized` when:

- the authorization header is missing;
- the token is invalid;
- the token is expired;
- the token cannot be decoded or does not contain the required minimum data.

### 7.2 Authorization Errors

Return `403 Forbidden` when:

- the token is valid but the user does not have the required rights;
- no applicable permission allows the operation;
- the request targets a resource or operation that is explicitly forbidden.

### 7.3 Contract Errors

Return:

- `400 Bad Request` for a malformed payload;
- `422 Unprocessable Entity` if the filtering request contains an unusable FHIR resource;
- `500 Internal Server Error` for an unhandled internal error;
- `503 Service Unavailable` if the permission source is unavailable.

## 8. Target Filtering Rules

### 8.1 Expected Minimum Support

- `Bundle` responses coming from searches;
- single-resource responses.

### 8.2 Functional Rules

- blacklist `FHIRPath` expressions remove forbidden elements;
- the service must preserve a valid FHIR response;
- filtering must be applied deterministically to all resources in the `Bundle`.

### 8.3 Known Current Limits to Address or Document

- field removal is better covered than full-entry removal;
- there is no explicit trace of removed elements;
- rule-combination behavior is still simplified.

## 9. Target Configuration

External configuration through environment variables or `application.yml`.

### 9.1 Application Configuration

- `SERVER_PORT`
- `PERMISSION_ENGINE_FHIR_VERSION_DEFAULT`
- `PERMISSION_ENGINE_LOG_LEVEL`

### 9.2 Security Configuration

- `PERMISSION_ENGINE_TOKEN_MODE`
  - `trusted-forwarded-claims`, `jwt-validate`, `introspection`
- `PERMISSION_ENGINE_JWT_ISSUER_URI`
- `PERMISSION_ENGINE_JWT_JWKS_URI`
- `PERMISSION_ENGINE_REQUIRED_AUDIENCE`

### 9.3 Permission Source Configuration

- `PERMISSION_SOURCE_MODE`
  - `mock`, `fhir`, `http`, `in-memory`
- `PERMISSION_SOURCE_BASE_URL`
- `PERMISSION_SOURCE_TIMEOUT_MS`
- `PERMISSION_SOURCE_CACHE_TTL_SECONDS`

### 9.4 Gateway / Mirth Integration Configuration

- `PERMISSION_ENGINE_TRUST_FORWARDED_USER_CONTEXT`
- `PERMISSION_ENGINE_RETURN_REWRITTEN_URL`
- `PERMISSION_ENGINE_FAIL_CLOSED`

## 10. Target Deployment Mode

### 10.1 Packaging

- Spring Boot application executable as a JAR;
- container-friendly image for platform integration.

### 10.1 bis Standalone HAPI Variant

In the standalone HAPI variant, the `Permission Engine` remains a separate service, but is built on top of a HAPI FHIR runtime enriched with a permission module enabled by configuration.

In that case:

- the `Permission Engine` is delivered as a standalone artifact;
- it has its own Docker image;
- activation is driven by a Spring property;
- behavior is integrated into the dedicated HAPI server native request/response lifecycle;
- call orchestration can be handled by Mirth, a gateway, or a reverse proxy.

### 10.1 ter HAPI Variant Embedded in the Target Backend

Another option is to embed the permission module directly into the target business FHIR server.

This approach is technically feasible, but less recommended if the goal is to preserve:

- deployment autonomy;
- engine reuse;
- separation of responsibilities.

### 10.2 Runtime Dependencies

- Java 17;
- network access to the permission source if external;
- possible access to JWKS or an introspection service if token validation is local.

### 10.3 Minimal Local Scenario

1. start the service with a mock or in-memory `PermissionService`;
2. call `/api/v1/authorize` with a token and a FHIR request;
3. call `/api/v1/filter-response` with a test FHIR response;
4. verify URL rewriting and returned filtering.

## 11. Proposed Refactoring

### 11.1 Principle

Do not break the existing core. First add an orchestration layer around it.

### 11.2 Recommended Steps

1. introduce Spring Boot without changing the core logic;
2. move the existing engine into an explicit domain layer;
3. enrich `RequestEvaluationResult` with:
   - `decision`
   - `httpStatus`
   - `reasonCode`
   - `rewrittenUrl`
   - `warnings`
4. introduce `PermissionDecisionService`;
5. implement a real permission source;
6. add token parsing and validation according to the chosen mode;
7. expose the REST or HAPI endpoints;
8. add integration tests;
9. document the gateway / Mirth integration flow.

## 12. Testing Strategy

### 12.1 Regression Tests

Keep and extend the existing tests for:

- permission compilation;
- `allow/deny` evaluation;
- search-parameter merging;
- R4/R5 filtering.

### 12.2 Additional Unit Tests

- mapping a token to `PermissionContext`;
- mapping an HTTP request to `FhirRequest`;
- mapping `RequestEvaluationResult` to the REST response;
- mapping HAPI `Parameters` to the business model if the HAPI option is selected;
- mapping the business model back to HAPI `Parameters` if the HAPI option is selected;
- handling `401`, `403`, `400`, and `503` errors.

### 12.3 Integration Tests

- `/authorize` call with missing token;
- `/authorize` call with invalid token;
- `/authorize` call with full authorization;
- `/authorize` call with partial authorization and a rewritten URL;
- `/authorize` call with `403` denial;
- `/filter-response` call on a single resource;
- `/filter-response` call on a `Bundle`;
- verification of fail-closed mode;
- HAPI `$authorize-request` operation call if the HAPI option is selected;
- HAPI `$filter-response` operation call if the HAPI option is selected;
- validation of HAPI interceptors on a dedicated server if that option is selected.

## 13. Proposed Technical Backlog

The backlog should be adjusted slightly if the HAPI option is selected as the service foundation, because part of the work then moves from classic REST controllers to HAPI exposure, FHIR operations, and interceptors.

### Epic 1 - Foundation Alignment and Contract Framing

- formally choose the exposure mode:
  - technical REST facade
  - or HAPI / FHIR operations
- align Java and HAPI versions between the permission module and the target foundation;
- freeze the target input/output contract;
- define the expected call mode from Mirth or the gateway.

### Epic 2 - Permission-Core Industrialization

- cleanly isolate the reusable business core;
- enrich `RequestEvaluationResult`;
- stabilize the `PermissionContext`, `FhirRequest`, and `FhirResponse` models;
- externalize configuration.

### Epic 3 - Service Exposure Integration

#### Technical REST Variant

- implement the `/authorize` endpoint;
- implement the `/filter-response` endpoint;
- create REST DTOs;
- expose health endpoints.

#### HAPI / FHIR Variant

- implement dedicated HAPI system operations;
- map `Parameters` to the business model;
- map business results back to `Parameters`;
- document the FHIR calling contract.

### Epic 4 - Pre-Authorization and Rewriting

- implement request evaluation;
- map the incoming context to `FhirRequest`;
- enrich the evaluation result;
- handle HTTP status codes and denial reasons.

### Epic 5 - Response Filtering

- parse the incoming FHIR response;
- apply R4/R5 filtering;
- serialize the filtered response.

### Epic 6 - Security Integration

- define the trust model for the token;
- implement claim extraction;
- add local validation or introspection if required.

### Epic 7 - Permission Source

- connect the real `PermissionService` implementation;
- add cache and timeouts;
- handle source unavailability.

### Epic 8 - HAPI Runtime Integration

This epic is mainly needed if the HAPI option is selected.

- implement request and response interceptors;
- connect the `RequestDetails` / `ResponseDetails` adapter to the business core;
- register the module through conditional configuration;
- validate behavior on a dedicated HAPI server.

### Epic 9 - Quality and Documentation

- integration tests;
- API documentation;
- local deployment guide;
- gateway / Mirth integration guide;
- HAPI-contract-specific documentation if that option is selected.

## 14. Recommended Architectural Decisions

### Recommendations

- keep the service stateless;
- keep the business core framework-independent;
- allow orchestration of the pre/post double call by Mirth, a gateway, or a reverse proxy;
- prefer configurable token validation:
  - either trust claims validated upstream
  - or perform local JWT validation if the platform requires it
- start with nominal `R4` support while preserving structural `R5` compatibility;
- consider HAPI an acceptable technical foundation for a standalone service, as long as deployment stays separate from the business FHIR backend.

### Points to Validate During Detailed Design

- real source of `Permission` resources;
- exact token-validation mode;
- list of required claims;
- exact rewrite shape expected by Mirth or the gateway;
- permission-combination policy;
- caching strategy;
- expected filtering depth on Bundles.

## 15. Conclusion

The current repository already contains a reusable FHIR authorization core. The main need is not a functional rewrite, but the addition of a robust microservice layer around that core.

The recommended target is a stateless service, deployed separately, called before and after the FHIR backend to evaluate permissions and then filter responses. This service can be built either as a dedicated Spring Boot REST microservice or on top of a dedicated HAPI FHIR runtime comparable to the mapping engine, as long as it keeps its own Docker image, configuration, and deployment lifecycle. This approach limits risk, maximizes reuse of existing code, and directly covers the acceptance criteria: request evaluation, URL rewriting, `401/403` rejection, response filtering, external configuration, tests, and integration documentation.
