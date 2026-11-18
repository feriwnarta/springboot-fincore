# OpenAPI Bearer Security Design

## Goal

Make the three service Swagger UIs usable for authenticated API testing by exposing a `bearerAuth` JWT scheme and marking only protected operations as secured.

## Scope

- Add the same HTTP Bearer/JWT scheme to loan, ledger, and search OpenAPI documents.
- Add security requirements to protected controllers.
- Keep public loan product and loan quote operations documented without a required security lock.
- Keep Kong routing unchanged; Swagger remains available on the direct service ports.
- Add an automated contract check for the generated OpenAPI documents.

## Design

Each service owns a small `OpenApiConfiguration` under its existing `config` package. The configuration uses Springdoc's `@SecurityScheme` annotation, so the generated document contains `components.securitySchemes.bearerAuth` with an HTTP Bearer JWT definition.

Protected controllers use `@SecurityRequirement(name = "bearerAuth")`. Public controllers are not annotated. This keeps the OpenAPI contract aligned with the existing Spring Security rules rather than applying authentication globally.

## Verification

The OpenAPI contract test checks all three running service documents for the bearer scheme. Maven verification checks compilation and the existing unit/integration test suite. The final manual check opens each Swagger UI and confirms the `Authorize` button is available.
