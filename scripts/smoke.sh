#!/usr/bin/env bash
set +x
set -euo pipefail

base_url="${BASE_URL:-http://localhost:8000}"
script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

base_url_pattern="^https?://[A-Za-z0-9.-]+(:[0-9]+)?(/([A-Za-z0-9._~!\$&'()+,;=:@/-]|%[A-Fa-f0-9]{2})*)?$"
[[ "${base_url}" =~ ${base_url_pattern} ]] ||
    { printf 'BASE_URL must be a single absolute HTTP(S) base URL without glob characters.\n' >&2; exit 1; }

fail() {
    printf 'FinCore smoke failed: %s\n' "$*" >&2
    exit 1
}

for dependency in curl jq uuidgen; do
    command -v "${dependency}" >/dev/null || fail "${dependency} is required"
done

new_id() {
    uuidgen | tr '[:upper:]' '[:lower:]'
}

work_dir="$(mktemp -d)"
trap 'rm -rf -- "${work_dir}"' EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

correlation_id="$(new_id)"
idempotency_key="${SMOKE_IDEMPOTENCY_KEY:-smoke-${correlation_id}}"
[[ "${idempotency_key}" =~ ^[a-zA-Z0-9._-]{1,128}$ ]] || fail "Invalid SMOKE_IDEMPOTENCY_KEY"
printf 'FinCore smoke correlation ID %s\n' "${correlation_id}"

get_token() {
    local label="$1" username="$2" password="$3" token="$4"
    if [[ -z "${token}" ]]; then
        token="$(KEYCLOAK_USERNAME="${username}" KEYCLOAK_PASSWORD="${password}" \
            "${script_dir}/keycloak-token.sh")" || fail "Could not obtain the ${label} token"
    fi
    [[ "${token}" =~ ^[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$ ]] ||
        fail "Invalid ${label} JWT bearer token format"
    printf '%s\n' "${token}"
}

response_header() {
    awk -v name="$1" '
        /^HTTP\// { value = "" }
        index(tolower($0), name ":") == 1 {
            sub(/^[^:]+:[ \t]*/, "")
            sub(/[ \t\r]+$/, "")
            value = $0
        }
        END { print value }
    ' "${work_dir}/headers"
}

check() {
    local label="$1" expected="$2" path="$3" token="$4"
    local request_id status error_code=''
    shift 4
    request_id="$(new_id)"

    # Use stdin for the bearer header so it stays out of curl's arguments.
    if ! status=$(
        if [[ -n "${token}" ]]; then
            printf 'Authorization: Bearer %s\n' "${token}"
        fi |
            curl --disable --globoff --silent --proto '=http,https' --max-time 30 \
                --header @- --header 'Accept: application/json' \
                --header "X-Correlation-Id: ${correlation_id}" --header "X-Request-Id: ${request_id}" \
                --dump-header "${work_dir}/headers" --output "${work_dir}/body" \
                --write-out '%{http_code}' "$@" --url "${base_url%/}${path}"
    ); then
        fail "${label}: request failed or timed out (request ID ${request_id})"
    fi
    [[ "${status}" == "${expected}" ]] ||
        fail "${label}: expected HTTP ${expected}, got ${status} (request ID ${request_id})"

    [[ "$(response_header x-correlation-id)" == "${correlation_id}" &&
        "$(response_header x-request-id)" == "${request_id}" ]] ||
        fail "${label}: missing or mismatched response ID headers (request ID ${request_id})"

    case "${expected}" in
        401) error_code=UNAUTHORIZED ;;
        403) error_code=FORBIDDEN ;;
    esac
    jq -es --arg correlation "${correlation_id}" --arg request "${request_id}" \
        --arg path "${path%%\?*}" --arg error_code "${error_code}" '
            length == 1 and (.[0] | type == "object"
                and .meta.apiVersion == "v1"
                and .meta.correlationId == $correlation
                and .meta.requestId == $request
                and .meta.path == $path
                and (.meta.timestamp | type == "string" and length > 0)
                and (if $error_code == "" then
                    .success == true and .data != null and has("error") and .error == null
                else
                    .success == false and has("data") and .data == null
                    and .error.code == $error_code
                    and (.error.message | type == "string" and length > 0)
                end))
        ' "${work_dir}/body" >/dev/null 2>&1 ||
        fail "${label}: invalid response envelope or metadata (request ID ${request_id})"
    printf '%s: HTTP %s (request ID %s)\n' "${label}" "${status}" "${request_id}"
}

customer1_token="$(get_token customer1 "${KEYCLOAK_CUSTOMER1_USERNAME:-customer1}" \
    "${KEYCLOAK_CUSTOMER1_PASSWORD:-customer1}" "${CUSTOMER1_ACCESS_TOKEN:-}")"
customer2_token="$(get_token customer2 "${KEYCLOAK_CUSTOMER2_USERNAME:-customer2}" \
    "${KEYCLOAK_CUSTOMER2_PASSWORD:-customer2}" "${CUSTOMER2_ACCESS_TOKEN:-}")"
operator_token="$(get_token operator "${KEYCLOAK_OPERATOR_USERNAME:-loan-operator}" \
    "${KEYCLOAK_OPERATOR_PASSWORD:-loan-operator}" "${OPERATOR_ACCESS_TOKEN:-${SEARCH_ACCESS_TOKEN:-}}")"

search_path='/api/v1/search/loans?limit=10'
savings_path='/api/v1/savings/accounts'

check 'Public loan products' 200 '/api/v1/loan-products?limit=10' ''
check 'Loan search without token' 401 "${search_path}" ''
check 'Customer token on operator report' 403 '/api/v1/reports/loans/top-customers' "${customer1_token}"
check 'Operator loan search' 200 "${search_path}" "${operator_token}"

check 'Customer1 opens savings account' 201 "${savings_path}" "${customer1_token}" \
    --header "Idempotency-Key: ${idempotency_key}" --header 'Content-Type: application/json' \
    --data-raw '{"customerId":"CUST-001"}'
account_id="$(jq -er '
    .data | select(.customerId == "CUST-001") | .accountId | strings
    | select(test("^[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}$"))
' "${work_dir}/body" 2>/dev/null)" || fail "Savings creation did not return a CUST-001 account UUID"

check 'Customer1 reads own savings account' 200 "${savings_path}/${account_id}" "${customer1_token}"
jq -e --arg account "${account_id}" \
    '.data.accountId == $account and .data.customerId == "CUST-001"' \
    "${work_dir}/body" >/dev/null 2>&1 || fail "Savings read did not return the account opened by customer1"
check 'Customer2 reads customer1 savings account' 403 "${savings_path}/${account_id}" "${customer2_token}"

printf 'FinCore Keycloak smoke checks passed (savings account %s)\n' "${account_id}"

