from __future__ import annotations

import argparse
import json
import sys

from .dataset import DatasetValidationError
from .experiment import DataReadinessBlocked, run_from_paths
from .validator import validate_bundle


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(prog="research-dataset")
    subparsers = parser.add_subparsers(dest="command", required=True)
    for command in ("validate", "diagnose"):
        subparser = subparsers.add_parser(command)
        subparser.add_argument("bundle")
    experiment = subparsers.add_parser("experiment")
    experiment.add_argument("--manifest", required=True)
    experiment.add_argument("--samples", required=True)
    experiment.add_argument("--market", default="BTC/USD")
    experiment.add_argument("--market-id")
    experiment.add_argument("--horizon", default="PT4H")
    experiment.add_argument("--output-dir", required=True)
    args = parser.parse_args(argv)
    if args.command == "experiment":
        try:
            result = run_from_paths(args.manifest, args.samples, args.market, args.horizon, args.output_dir,
                                    args.market_id)
        except DatasetValidationError as error:
            for diagnostic in error.result.diagnostics:
                print(f"{diagnostic['code']}: {diagnostic['message']}", file=sys.stderr)
            return 1
        except DataReadinessBlocked as error:
            print("BLOCKED_BY_DATA_READINESS", file=sys.stderr)
            return 2
        except (OSError, ValueError, KeyError) as error:
            print(f"experiment.error: {error}", file=sys.stderr)
            return 1
        print(result["status"])
        return 0 if result["status"] == "COMPLETED" else 2
    result = validate_bundle(args.bundle)
    if args.command == "diagnose":
        print(json.dumps(result.as_dict(), indent=2, sort_keys=True))
    elif result.valid:
        print("valid")
    else:
        for diagnostic in result.diagnostics:
            prefix = f"line {diagnostic['line']}: " if "line" in diagnostic else ""
            print(f"{prefix}{diagnostic['code']}: {diagnostic['message']}", file=sys.stderr)
    return 0 if result.valid else 1


if __name__ == "__main__":
    raise SystemExit(main())
