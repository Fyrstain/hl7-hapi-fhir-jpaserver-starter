# Examples by Scenario

Organization of the `Permission Engine` test files:

- `common/`
  - resources shared by several scenarios
  - token-generation script
  - reusable permissions
- `inline/`
  - examples for scenarios using inline `permissionJson`
- `token/`
  - templates for scenarios where permissions come from the token
- `repository/`
  - examples for scenarios using an external FHIR repository
- `interceptors/`
  - resources and permissions for scenarios with interceptors enabled

## Main Content

### `common/`

- `generate-permission-token.ps1`
- `generated-token.txt`
- `permission-patient-crus-r5.json`
- `permission-filter-telecom-r5.json`

### `inline/`

- `authorize-inline-r4.json`
- `authorize-update-inline-r4.json`
- `filter-inline-r4.json`

### `token/`

- `authorize-token-search-r4-template.json`
- `authorize-token-update-r4-template.json`
- `filter-token-r4-template.json`

### `repository/`

- `authorize-repository-r4.json`

### `interceptors/`

- `patient-create-toto-r4.json`
- `patient-create-bob-r4.json`
- `patient-update-toto-r4.json`
- `permission-patient-crus-and-filter-r5.json`
- `permission-patient-search-only-r5.json`
