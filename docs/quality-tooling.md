# Repository Quality Tooling

Trading OS keeps every Maven service independent. There is no root Maven
aggregator, so verification and SonarQube analysis run once per Java module.
The Angular application is also verified and analyzed as its own project.

## Fast local loop

Run tests, coverage, lint, and build without Sonar:

```bash
./scripts/quality-verify.sh
```

This is the recommended loop for everyday development. It runs all seven Maven
`verify` lifecycles, generates JaCoCo XML reports, runs the Angular tests with
LCOV, checks Prettier formatting, and builds the production frontend.

## Full local Sonar loop

Run tests, coverage, lint, build, and all Sonar analyses with Quality Gate
checks:

```bash
export SONAR_HOST_URL=http://localhost:9000
export SONAR_TOKEN='<analysis-token>'
./scripts/quality-scan.sh
```

This is the heavier loop to run before submitting PRs or periodically. It
performs the complete local verification, then scans each Maven module and the
frontend independently against the local SonarQube instance. Every scanner
waits for its Quality Gate.

## Continuous Integration

`.github/workflows/quality.yml` runs on every push to `main`, pull request
targeting `main`, and manual dispatch:

### Pull requests (GitHub-hosted runners only)

1. **Backend Quality** (matrix): JDK 21 `clean verify` per Maven module with
   JaCoCo XML reports, compiled classes, and test results uploaded.
2. **Frontend Quality**: Node 22, `npm ci`, Prettier check, Vitest coverage,
   production build, coverage artifact.
3. **Coverage Summary**: per-module JaCoCo metrics plus frontend summary.
4. **Trading OS Quality Gate**: aggregate check; fails unless backend and
   frontend jobs succeeded. Sonar is skipped on PRs.

### Push to main / workflow dispatch (GitHub-hosted + self-hosted)

All PR jobs above, plus:

5. **SonarQube Analysis** (self-hosted runner `trading-os-sonarqube`):
   downloads artifacts from GitHub-hosted jobs, restores compiled classes and
   coverage reports, runs SonarQube analysis for all 8 projects (7 backend +
   1 frontend), waits for every Quality Gate.
6. **Trading OS Quality Gate**: aggregate check including Sonar result.

### Security boundary

Self-hosted Sonar jobs ONLY execute on `push` to `main` or `workflow_dispatch`.
Pull requests (including fork PRs) NEVER trigger self-hosted jobs. This
prevents untrusted code from executing on the local machine, even if a PR
modifies the workflow file.

## Local SonarQube infrastructure

The analysis target is the shared standalone SonarQube (Community Build 26.7)
running at <http://localhost:9000> under
`dev-tools/sonarqube-26.7.0.124771`. This instance is shared with DevLog AI.

The pinned Docker Compose stack (`docker-compose.sonar.yml`) exists as an
isolated fallback and must never be run as a second parallel instance.

## Authenticated scans

Only run scans against an explicitly approved server and token:

```bash
export SONAR_HOST_URL=http://localhost:9000
export SONAR_TOKEN='<analysis-token>'
./scripts/quality-scan.sh
```

The script refuses to start without both environment variables.

## Sonar project topology

One Sonar project per backend module plus the frontend:

- `trading-os:risk-domain`
- `trading-os:gateway`
- `trading-os:eureka-server`
- `trading-os:trading-core`
- `trading-os:broker-service`
- `trading-os:market-data`
- `trading-os:market-intelligence`
- `trading-os:trading-os-web`

Each project has its own analysis token stored as a GitHub Secret
(`SONAR_TOKEN_*`).

## New-code Quality Gate

The SonarQube server enforces a Clean-as-You-Code gate for every
`trading-os:*` project:

- no new bugs;
- no new vulnerabilities;
- all new Security Hotspots reviewed;
- new-code coverage at least 80%;
- new-code duplication at most 3%.

The server owns gate configuration and the new-code definition. Repository
scripts enforce its result but do not attempt to mutate server policy.

SonarQube supplements tests, ADR validation, architecture review, and human
Code Review. It does not replace any of them.

## Troubleshooting

### Runner offline

If the self-hosted runner is offline, push-to-main quality checks will fail.
Ensure the `gha-trading-os` user's runner service is running:

```bash
sudo -u gha-trading-os /opt/gha-trading-os/actions-runner/svc.sh status
```

### Sonar unavailable

If SonarQube is not running at `http://localhost:9000`, the Sonar job will
fail. Start it:

```bash
cd dev-tools/sonarqube-26.7.0.124771/bin/linux-x86-64
./sonar.sh start
```

### Missing artifacts

If backend or frontend jobs fail, their artifacts won't be available for the
Sonar job. Fix the failing job first.

### Quality Gate failure

A Quality Gate failure means the code has real quality issues. Check the
SonarQube UI at `http://localhost:9000` for details.

### Token/auth failure

A 401 error means the `SONAR_TOKEN_*` secret is missing, invalid, or
unauthorized. Regenerate the token in SonarQube UI and update the GitHub
Secret.

## Pull Request Quality Policy

- persistent changes must be proposed through pull requests;
- `Trading OS Quality Gate` is a required merge check;
- direct pushes to `main` are not part of the normal engineering workflow.
