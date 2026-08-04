# Repository Quality Tooling

Trading OS keeps every Maven service independent. There is no root Maven
aggregator, so verification and SonarQube analysis run once per Java module.
The Angular application is also verified and analyzed as its own project.

## Local verification and coverage

Run all seven Maven `verify` lifecycles, generate JaCoCo XML reports, run the
Angular tests with LCOV, and build the production frontend:

```bash
./scripts/quality-verify.sh
```

The script fails on the first failed test, missing coverage report, dependency
installation failure, or frontend build failure. Reports are generated at:

- `<module>/target/site/jacoco/jacoco.xml` for each Java module;
- `trading-os-web/coverage/trading-os-web/lcov.info` for Angular.

Generated reports and scanner work directories are ignored by Git.

## Local SonarQube infrastructure

SonarQube Community Build and its dedicated PostgreSQL database are isolated
from the application Compose stack, network, databases, and startup lifecycle:

```bash
docker compose -f docker-compose.sonar.yml up -d
docker compose -f docker-compose.sonar.yml down
```

The pinned local stack exposes SonarQube at <http://localhost:9000>. The default
database password is for local development only. Override it before first
startup when needed:

```bash
SONAR_DB_PASSWORD='<local-password>' docker compose -f docker-compose.sonar.yml up -d
```

Create analysis projects and a token in SonarQube. Do not store the token or
database password in repository files.

## Authenticated scans

Only run scans against an explicitly approved server and token:

```bash
export SONAR_HOST_URL=http://localhost:9000
export SONAR_TOKEN='<analysis-token>'
./scripts/quality-scan.sh
```

The script refuses to start without both environment variables. It first runs
the complete local verification, then scans each Maven module and the frontend
independently. Every scanner waits for its Quality Gate and returns a failure
when analysis, upload, or the gate fails. The Maven scanner is pinned in the
script; the frontend scanner is pinned in `package-lock.json`.

## New-code Quality Gate

Configure the initial Clean-as-You-Code gate on the SonarQube server for every
`trading-os:*` project:

- no new high-severity bugs;
- no new high-severity vulnerabilities;
- all new Security Hotspots reviewed;
- new-code coverage at least 80%;
- new-code duplication at most 3%.

The server owns gate configuration and the new-code definition. Repository
scripts enforce its result but do not attempt to mutate server policy.

SonarQube supplements tests, ADR validation, architecture review, migration
review, and human Code Review. It does not replace any of them.
