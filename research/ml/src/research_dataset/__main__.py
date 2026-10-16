from __future__ import annotations

import argparse
import json
import sys

from .validator import validate_bundle


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(prog="research-dataset")
    subparsers = parser.add_subparsers(dest="command", required=True)
    for command in ("validate", "diagnose"):
        subparser = subparsers.add_parser(command)
        subparser.add_argument("bundle")
    args = parser.parse_args(argv)
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
