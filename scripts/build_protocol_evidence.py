#!/usr/bin/env python3
import argparse
from pathlib import Path
import sys

PROJECT_ROOT = Path(__file__).resolve().parents[1]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

from protocol_evidence.build import build_protocol_evidence, check_protocol_evidence


def main(argv=None):
    parser = argparse.ArgumentParser(description="Build protocol evidence artifacts")
    parser.add_argument("--capture-root", required=True)
    parser.add_argument("--client-root", required=True)
    parser.add_argument("--evidence-root", required=True)
    parser.add_argument("--output-root", required=True)
    parser.add_argument("--report", required=True)
    parser.add_argument("--client-version", default="9.2.2")
    parser.add_argument("--android-contract")
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args(argv)
    function = check_protocol_evidence if args.check else build_protocol_evidence
    result = function(
        Path(args.capture_root),
        Path(args.client_root),
        Path(args.evidence_root),
        Path(args.output_root),
        Path(args.report),
        client_version=args.client_version,
        android_contract_path=(
            Path(args.android_contract) if args.android_contract else None
        ),
    )
    if args.check:
        print("protocol evidence is current")
    else:
        print(
            "protocol evidence generated: commands={commandCount} fields={fieldCount}".format(
                **result
            )
        )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
