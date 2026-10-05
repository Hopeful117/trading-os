#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

for service in eureka-server gateway broker-service market-data trading-core news-service; do
  echo "Testing ${service}"
  wrapper="${project_root}/${service}/mvnw"
  if [[ ! -x "${wrapper}" ]]; then
    wrapper="${project_root}/trading-core/mvnw"
  fi
  "${wrapper}" -q -f "${project_root}/${service}/pom.xml" test
done

echo "Testing market-intelligence"
"${project_root}/trading-core/mvnw" -q \
  -f "${project_root}/market-intelligence/pom.xml" test

echo "Testing risk-domain"
"${project_root}/trading-core/mvnw" -q \
  -f "${project_root}/risk-domain/pom.xml" test

echo "Testing trading-os-web"
npm --prefix "${project_root}/trading-os-web" run test:ci

echo "Building trading-os-web"
npm --prefix "${project_root}/trading-os-web" run build
