# Permission Engine - Existing Capabilities, HAPI Port, and Remaining Work

## 1. Goal

This document summarizes:

- what already existed in `permission-module`;
- what was ported into the HAPI FHIR starter `permission-engine` branch;
- what still needs to be completed to obtain a production-ready standalone `Permission Engine`.

## 2. Summary Table

| Topic | In `permission-module` | In HAPI `permission-engine` branch | Status |
| --- | --- | --- | --- |
| Core evaluation engine | Existing `FhirAuthorizationEngine` | Reused in HAPI | Done |
| Input and output model | `FhirRequest`, `FhirResponse`, `PermissionContext`, `RequestEvaluationResult` | Reused in HAPI | Done |
| Compilation of `Permission` resources into executable rules | `PermissionEvaluator`, `SimplePermissionEvaluator` | Reused in HAPI | Done |
| Authorization evaluation for FHIR requests | Existing | Reused in HAPI | Done |
| Search parameter rewriting | Existing | Reused in HAPI | Done |
| FHIR response filtering | Existing in R4 and R5 | Reused in HAPI, used in R4 | Done |
| Technical invocation facade | `JavaPermissionFacade` on the IRIS side | Replaced by HAPI operations and interceptors | Done |
| Exposure as a microservice / server component | No | Yes, in the HAPI starter | Done |
| Explicitly callable operations | No, Java facade only | Yes, `$authorize-request` and `$filter-response` | Done |
| Built-in FHIR flow interception | No | Yes, through HAPI interceptors | Done |
| Dedicated configuration profile | No | Yes, `profiles/application-permission.yml` | Done |
| Engine regression tests | Yes | Reused in HAPI | Done |
| Operation integration tests | No | Yes | Done |
| Interceptor integration tests | No | Yes | Done |
| Real permission retrieval from token / request context | No | Yes | Done |
| Real loading of `Permission` resources from a FHIR repository or third-party service | No | Yes | Done |
| Real token validation | No | Yes | Done |
| Complete business error handling by rejection cause | Partial | Yes | Done |
| Docker packaging ready for the target deployment | No | Started through HAPI integration, still to be finalized for the platform | To do |

## 3. What Already Existed in `permission-module`

The `permission-module` repository already contained the business core of the engine:

- an engine able to evaluate a FHIR request;
- an engine able to enrich a search with permission-imposed parameters;
- an engine able to filter a FHIR response;
- a clean Java model representing context, request, response, and result;
- a set of unit tests covering internal behavior.

However, that repository was not yet a standalone service ready to run on its own in production.

The main limitation was that permission retrieval was not connected to a real business source:

- `FhirAuthorizationEngine` already depended on `PermissionService` to retrieve permissions;
- but in `JavaPermissionFacade`, the engine was instantiated with a `MockPermissionService`;
- that `MockPermissionService` returned an empty list.

In other words, the engine existed, but not the business adapter able to say:

"given this token or this user, here are the real `Permission` resources to apply."

## 4. What Was Ported into HAPI

In the HAPI `permission-engine` branch, this core was turned into a usable server-side component:

- the engine core code was reused;
- dedicated Spring configuration was added;
- a `permission` profile was added;
- the engine was exposed through HAPI operations;
- HAPI interceptors were added for an integrated server-pipeline mode;
- integration tests were added to prove:
  - authorization;
  - rejection;
  - search parameter rewriting;
  - response filtering.

This already enables two usage modes:

1. an "operations" mode, for example called from Mirth or another external component;
2. an "interceptors" mode, directly wired into the HAPI FHIR pipeline.

Both modes can coexist.

Important point:

- the `Permission` resource only exists in **R5**;
- the engine can still protect **R4** flows;
- this simply means the permission source must provide R5 `Permission` resources.

## 5. What Has Been Finalized in HAPI

The HAPI branch now contains:

- real token validation;
- user-context resolution from the token and HTTP context;
- loading permissions from token claims;
- loading permissions from a configurable FHIR repository;
- differentiated error handling:
  - `401` for authentication;
  - `403` for business authorization refusal;
  - `502` for an invalid response from the permission repository;
  - `503` for an unavailable or timed-out permission repository.

The `NoopPermissionService` remains only as a technical fallback when no source is enabled.

## 6. Possible Runtime Strategies

### Option A - Permissions Derived Directly from the Token

`PermissionService` reads token claims and builds the applicable permissions.

Advantages:

- no need to call an external repository;
- lower latency;
- a good fit when the authorization model is already carried by the token.

Limitations:

- less flexible if permissions are large or frequently changing;
- strongly dependent on the claim structure.

### Option B - Load from a FHIR Repository

`PermissionService` extracts identity from the token and reads `Permission` resources from HAPI FHIR or another FHIR repository.

Advantages:

- aligned with the FHIR model;
- more flexible for evolving rules;
- more consistent with the current engine design.

Limitations:

- requires access to the repository;
- adds an I/O call during resolution.

### Option C - Hybrid Approach

The token identifies the user and context, then the engine loads permissions from a repository or dedicated service.

Advantages:

- often the best compromise;
- compatible with Keycloak and a business permission source;
- keeps the engine independent from the authentication provider.

Limitations:

- requires slightly more integration work.

## 7. Related Documentation

See also:

- [permission-engine-design.md](permission-engine-design.md)
- [permission-engine-testing-and-deployment.md](permission-engine-testing-and-deployment.md)

## 8. Conclusion

`permission-module` already contained a real authorization engine, but not yet a complete standalone business service.

The HAPI branch made an important step forward:

- the engine is now integrable as a server component;
- it can be exposed explicitly;
- it can be plugged directly into the FHIR flow;
- it is tested end to end.

The next structural step is no longer the evaluation logic itself, but the connection to a real permission source through a business implementation of `PermissionService`.
