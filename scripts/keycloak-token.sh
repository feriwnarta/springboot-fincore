#!/usr/bin/env bash
set +x
set -euo pipefail

url="${KEYCLOAK_URL:-http://localhost:8180}"
realm="${KEYCLOAK_REALM:-fincore}"
client_id="${KEYCLOAK_CLIENT_ID:-fincore-cli}"
username="${KEYCLOAK_USERNAME:-customer1}"

# Accept one HTTP(S) base URL with an optional path prefix. The path character
# set excludes URL glob syntax, query strings and fragments.
url_pattern="^https?://[A-Za-z0-9.-]+(:[0-9]+)?(/([A-Za-z0-9._~!\$&'()+,;=:@/-]|%[A-Fa-f0-9]{2})*)?$"
if [[ ! "${url}" =~ ${url_pattern} ]]; then
  printf 'KEYCLOAK_URL must be a single absolute HTTP(S) base URL without glob characters.\n' >&2
  exit 1
fi

if ! response=$(
  printf '%s' "${KEYCLOAK_PASSWORD:-customer1}" |
    curl --disable --globoff --silent --proto '=http,https' --max-time 30 --write-out $'\n%{http_code}' \
      --data-urlencode 'grant_type=password' \
      --data-urlencode "client_id=${client_id}" \
      --data-urlencode "username=${username}" \
      --data-urlencode 'password@-' \
      --url "${url%/}/realms/${realm}/protocol/openid-connect/token"
); then
  printf 'Could not complete the Keycloak token request.\n' >&2
  exit 1
fi

status=${response##*$'\n'}
if [[ "${status}" != 2[0-9][0-9] ]]; then
  printf 'Keycloak token request failed (HTTP %s).\n' "${status}" >&2
  exit 1
fi

if ! token=$(jq -ers '
  select(length == 1) | .[0].access_token | strings
  | select(test("\\A[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\z"))
' \
  <<< "${response%$'\n'*}" 2>/dev/null); then
  printf 'Keycloak response did not contain a valid access token.\n' >&2
  exit 1
fi

printf '%s\n' "${token}"
