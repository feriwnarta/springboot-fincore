#!/usr/bin/env bash
set -euo pipefail

services=(
  "loan-service|8081"
  "ledger-service|8082"
  "search-service|8083"
)

for service in "${services[@]}"; do
  name="${service%%|*}"
  port="${service##*|}"
  document=$(curl --disable --fail-with-body --silent --show-error --max-time 10 \
    "http://localhost:${port}/v3/api-docs")

  type=$(jq -er '.components.securitySchemes.bearerAuth.type' <<<"${document}")
  scheme=$(jq -er '.components.securitySchemes.bearerAuth.scheme' <<<"${document}")
  format=$(jq -er '.components.securitySchemes.bearerAuth.bearerFormat' <<<"${document}")

  [[ "${type}" == "http" ]]
  [[ "${scheme}" == "bearer" ]]
  [[ "${format}" == "JWT" ]]
  if [[ "${name}" == "loan-service" ]]; then
    jq -e '(.paths["/api/v1/auth/login"].post.security == null or
           .paths["/api/v1/auth/login"].post.security == []) and
           (.paths["/api/v1/loan-products"].post.security | map(.bearerAuth) | length > 0)' \
      <<<"${document}" >/dev/null
  fi
  printf '%s: bearerAuth type=%s scheme=%s format=%s\n' "${name}" "${type}" "${scheme}" "${format}"
done
