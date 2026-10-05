#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# Load local developer variables when the caller has not exported them.
# CI keeps precedence because its variables are already present.
if [[ -f "${project_root}/.env" ]]; then
  exported_sonar_host_url="${SONAR_HOST_URL-}"
  exported_sonar_token="${SONAR_TOKEN-}"
  set -a
  # shellcheck disable=SC1091
  source "${project_root}/.env"
  set +a
  if [[ -n "${exported_sonar_host_url}" ]]; then SONAR_HOST_URL="${exported_sonar_host_url}"; fi
  if [[ -n "${exported_sonar_token}" ]]; then SONAR_TOKEN="${exported_sonar_token}"; fi
fi

: "${SONAR_HOST_URL:?Set SONAR_HOST_URL to the approved SonarQube server URL}"
: "${SONAR_TOKEN:?Set SONAR_TOKEN to an analysis token; never commit it}"

maven_fallback="${project_root}/trading-core/mvnw"
maven_scanner="org.sonarsource.scanner.maven:sonar-maven-plugin:5.7.0.6970:sonar"
java_modules=(
  risk-domain
  eureka-server
  gateway
  broker-service
  market-data
  market-intelligence
  trading-core
  news-service
)

"${project_root}/scripts/quality-verify.sh"

for module in "${java_modules[@]}"; do
  printf '\nScanning %s and waiting for its Quality Gate\n' "${module}"
  wrapper="${project_root}/${module}/mvnw"
  if [[ ! -x "${wrapper}" ]]; then
    wrapper="${maven_fallback}"
  fi

  "${wrapper}" -f "${project_root}/${module}/pom.xml" \
    "${maven_scanner}" \
    -Dsonar.qualitygate.wait=true \
    -Dsonar.qualitygate.timeout=300
done

printf '\nScanning trading-os-web and waiting for its Quality Gate\n'
(
  cd "${project_root}/trading-os-web"
  npm exec -- sonar-scanner-npm \
    -Dsonar.qualitygate.wait=true \
    -Dsonar.qualitygate.timeout=300
)
