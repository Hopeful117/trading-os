#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
maven_fallback="${project_root}/trading-core/mvnw"
java_modules=(
  risk-domain
  eureka-server
  gateway
  broker-service
  market-data
  market-intelligence
  trading-core
)

run_maven() {
  local module="$1"
  shift
  local wrapper="${project_root}/${module}/mvnw"

  if [[ ! -x "${wrapper}" ]]; then
    wrapper="${maven_fallback}"
  fi

  "${wrapper}" -f "${project_root}/${module}/pom.xml" "$@"
}

for module in "${java_modules[@]}"; do
  printf '\nVerifying %s\n' "${module}"
  run_maven "${module}" verify

  report="${project_root}/${module}/target/site/jacoco/jacoco.xml"
  if [[ ! -f "${report}" ]]; then
    printf 'Missing JaCoCo XML report: %s\n' "${report}" >&2
    exit 1
  fi
done

printf '\nBackend coverage gate: checking LINE >= 80%% for business modules\n'
python3 "${project_root}/scripts/check-backend-coverage.py"

printf '\nInstalling frontend dependencies from package-lock.json\n'
npm --prefix "${project_root}/trading-os-web" ci

printf '\nChecking frontend lint (Prettier)\n'
npm --prefix "${project_root}/trading-os-web" exec -- prettier --check . || {
  printf '\nLint check failed. Run: npx prettier --write .\n' >&2
  exit 1
}

printf '\nTesting trading-os-web with LCOV coverage\n'
npm --prefix "${project_root}/trading-os-web" run test:coverage

lcov_report="${project_root}/trading-os-web/coverage/trading-os-web/lcov.info"
if [[ ! -f "${lcov_report}" ]]; then
  printf 'Missing frontend LCOV report: %s\n' "${lcov_report}" >&2
  exit 1
fi

printf '\nBuilding trading-os-web\n'
npm --prefix "${project_root}/trading-os-web" run build

printf '\nAll quality gates passed.\n'
