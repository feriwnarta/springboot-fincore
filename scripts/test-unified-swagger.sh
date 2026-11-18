#!/usr/bin/env bash
set -euo pipefail

base_url="${BASE_URL:-http://localhost:8000}"
expected_server_url="${EXPECTED_SERVER_URL:-${base_url}}"

ui_document=$(curl --disable --fail-with-body --silent --show-error --location --max-time 10 \
  "${base_url}/docs")
grep -q "Swagger UI" <<<"${ui_document}"

for service in loan ledger search; do
  if [[ "${service}" == "loan" ]]; then
    document_path="/v3/api-docs"
  else
    document_path="/docs/openapi/${service}"
  fi
  document=$(curl --disable --fail-with-body --silent --show-error --max-time 10 \
    "${base_url}${document_path}")
  jq -e '.openapi and (.paths | length > 0)' <<<"${document}" >/dev/null
  if [[ "${service}" == "loan" ]]; then
    jq -e '.paths["/api/v1/auth/login"].post and .paths["/api/v1/loan-products"].post' \
      <<<"${document}" >/dev/null
  fi
  server_url=$(jq -er '.servers[0].url' <<<"${document}")
  if [[ "${server_url}" != "${expected_server_url}" ]]; then
    printf '%s OpenAPI server URL is %s, expected %s\n' \
      "${service}" "${server_url}" "${expected_server_url}" >&2
    exit 1
  fi
  printf '%s OpenAPI is reachable through gateway\n' "${service}"
done

printf 'Unified Swagger UI is reachable through gateway\n'
