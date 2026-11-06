# Trading OS - Docker Build Cache Cleanup Result

**Date:** 2026-11-06  
**Status:** `BLOCKED`  
**Operation:** controlled Docker builder-cache cleanup

## Result

The authorized cleanup was **not executed**.

The required command remains:

```bash
docker builder prune --force
```

It was not run because the preflight builder check reported an operational
error for a configured Docker builder. The mandatory stop condition requires
stopping when Docker reports errors, even though the default daemon and default
builder were otherwise available.

## Initial Measurements

### Filesystem

```text
Filesystem: /dev/mapper/data-root
Total:      449G
Used:       423G
Available:  3.0G
Usage:      100%
```

### Docker

```text
Images:       120 total, 20 active, 13.66GB, 5.304GB reclaimable
Containers:   25 total, 25 active, 238.1MB, 0B reclaimable
Volumes:      18 total, 13 active, 96.73GB, 442.2MB reclaimable
Build cache:  24.4GB total, 16.25GB reclaimable
```

Detailed volume inspection confirmed that the protected
`trading-os_market_data` volume is approximately 91.41GB. No volume operation
was attempted.

## Safety Checks

- `df -h`: completed; filesystem remains at 100% usage.
- `docker system df`: completed.
- `docker system df -v`: completed; build cache was identified separately from
  images, containers, and volumes.
- `docker ps`: completed; 25 containers were running.
- `docker info`: completed; the default Docker daemon responded and reported
  25 running containers, no stopped containers, and overlay2 storage.
- Process inspection: no active local `docker build`, BuildKit, or buildx job was
  observed. The inspection commands themselves were visible during collection.
- `docker buildx ls`: **reported an error**:

```text
desktop-linux error
Cannot load builder desktop-linux: Cannot connect to the Docker daemon at
unix:///home/ludo/.docker/desktop/docker.sock
```

The `default` builder was reported as running with BuildKit `v0.26.2`, but the
unavailable `desktop-linux` builder means the Docker builder configuration is
not fully healthy. No build was terminated and no container was stopped.

## Cleanup Execution

```text
Executed:     no
Command:      not run
Exit status:  not applicable
Start time:   not applicable
End time:     not applicable
Reclaimed:    0B by this operation
```

No protected data, images, volumes, containers, PostgreSQL files, or Order Book
evidence were modified.

## Post-Execution Measurements

Not applicable because the cleanup did not execute. The filesystem and Docker
state remain at the initial measured values.

## Remaining Risk

The filesystem remains critically constrained at 3.0GB free. PostgreSQL write
failures, incomplete persistence, container operation failures, and service
instability remain possible.

The forensic recovery estimate of 16.25GB was not realized. Even if the full
estimate were reclaimed later, the resulting approximately 19.25GB free would
remain below the recommended 10% headroom, approximately 45GB for this host.

This blocked operation does not authorize collection, replay, deployment,
database cleanup, or any other cleanup command.

## Recommended Next Decision

Resolve or explicitly assess the unavailable `desktop-linux` builder/socket,
then repeat the preflight checks. A future execution must still use exactly one
command, without additional flags:

```bash
docker builder prune --force
```

No alternative prune command or daemon recovery was attempted.
