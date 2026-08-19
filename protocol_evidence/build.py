import hashlib
import json
from pathlib import Path
import tempfile

from .catalog import scan_capture_inventory
from .client_source import extract_command_constants
from .evidence import load_evidence_files
from .shapes import summarize_command_samples


GENERATOR_VERSION = 1
COMMAND_DEF = Path("Game.Network") / "Tenth.Network" / "NetCommandDef.cs"


def _json_bytes(value) -> bytes:
    return (
        json.dumps(value, ensure_ascii=False, indent=2, sort_keys=True) + "\n"
    ).encode("utf-8")


def _sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def _write(path: Path, data: bytes) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(data)


def _status_counts(rows, key):
    result = {"typed": 0, "raw": 0, "unsupported": 0}
    for row in rows:
        result[row[key]] += 1
    return result


def _coverage_report(rows, fields, web_counts, android_counts):
    invalid = sum(row["shape"]["invalidCount"] for row in rows)
    drift = sum(1 for row in rows if row["shape"]["drift"])
    named = sum(1 for row in rows if row["names"])
    lines = [
        "# Protocol coverage: client 9.2.2",
        "",
        "| Metric | Count |",
        "|---|---:|",
        f"| Captured commands | {len(rows)} |",
        f"| Client-named commands | {named} |",
        f"| Registered fields | {len(fields)} |",
        f"| Shape drift commands | {drift} |",
        f"| Invalid scanned samples | {invalid} |",
        f"| Web typed | {web_counts['typed']} |",
        f"| Web raw | {web_counts['raw']} |",
        f"| Web unsupported | {web_counts['unsupported']} |",
        f"| Android typed | {android_counts['typed']} |",
        f"| Android raw | {android_counts['raw']} |",
        f"| Android unsupported | {android_counts['unsupported']} |",
        "",
        "## Commands",
        "",
        "| Hex | Decimal | Names | Samples | Shape | Web | Android | Evidence |",
        "|---|---:|---|---:|---|---|---|---|",
    ]
    for row in rows:
        shape = "/".join(row["shape"]["rootTypes"]) or "invalid"
        names = ", ".join(row["names"]) or "-"
        lines.append(
            f"| {row['hexId']} | {row['decimalId']} | {names} | "
            f"{row['count']} | {shape} | {row['webStatus']} | "
            f"{row['androidStatus']} | {row['evidence']} |"
        )
    lines.extend(
        [
            "",
            "A captured command is not automatically a typed business command.",
            "UNKNOWN and raw entries remain available to the generic capture layer.",
            "",
        ]
    )
    return "\n".join(lines).encode("utf-8")


def _build_outputs(
    capture_root,
    client_root,
    evidence_root,
    client_version,
):
    capture_root = Path(capture_root)
    client_root = Path(client_root)
    inventory = scan_capture_inventory(capture_root)
    constants = extract_command_constants(client_root / COMMAND_DEF)
    evidence = load_evidence_files(evidence_root, client_root, constants)
    rows = []
    fields = []
    for item in inventory:
        hex_id = item["hexId"]
        overlay = evidence.get(hex_id)
        names = constants.get(item["decimalId"], [])
        row = {
            **item,
            "names": list(overlay["names"] if overlay else names),
            "shape": summarize_command_samples(
                capture_root.parent, item["samplePaths"]
            ),
            "evidence": overlay["evidence"] if overlay else (
                "CAPTURE_CONFIRMED" if item["count"] else "UNKNOWN"
            ),
            "webStatus": overlay["webStatus"] if overlay else "raw",
            "androidStatus": overlay["androidStatus"] if overlay else "raw",
            "clientSources": overlay["clientSources"] if overlay else [],
        }
        rows.append(row)
        if overlay:
            for field in overlay["fields"]:
                fields.append(
                    {
                        "hexId": hex_id,
                        "decimalId": item["decimalId"],
                        **field,
                    }
                )
    fields.sort(key=lambda value: (value["decimalId"], value["path"]))
    approved_android_fields = [
        {
            key: field[key]
            for key in (
                "hexId", "decimalId", "path", "name", "rawTypes",
                "nullable", "unit", "evidence", "businessApproved",
            )
        }
        for field in fields
        if field["businessApproved"]
    ]
    android_commands = [
        {
            "hexId": row["hexId"],
            "decimalId": row["decimalId"],
            "names": row["names"],
            "evidence": row["evidence"],
        }
        for row in rows
        if row["androidStatus"] == "typed"
    ]
    android_bytes = _json_bytes(
        {
            "clientVersion": client_version,
            "conventions": {
                "wid": "x=wid/10000,y=wid%10000",
                "timestamp": "unix_seconds_unless_field_declares_otherwise",
                "heroId": "preserve_raw_and_normalized",
            },
            "commands": android_commands,
            "fields": approved_android_fields,
        }
    )
    web_counts = _status_counts(rows, "webStatus")
    android_counts = _status_counts(rows, "androidStatus")
    catalog_bytes = _json_bytes(
        {"clientVersion": client_version, "commands": rows}
    )
    fields_bytes = _json_bytes(
        {"clientVersion": client_version, "fields": fields}
    )
    report_bytes = _coverage_report(rows, fields, web_counts, android_counts)
    manifest = {
        "clientVersion": client_version,
        "generatorVersion": GENERATOR_VERSION,
        "commandCount": len(rows),
        "fieldCount": len(fields),
        "webStatusCounts": web_counts,
        "androidStatusCounts": android_counts,
        "files": {
            "command-catalog.json": {
                "sha256": _sha256(catalog_bytes),
                "size": len(catalog_bytes),
            },
            "field-registry.json": {
                "sha256": _sha256(fields_bytes),
                "size": len(fields_bytes),
            },
            "protocol-coverage-client-9.2.2.md": {
                "sha256": _sha256(report_bytes),
                "size": len(report_bytes),
            },
            "protocol_contract_client_9_2_2.json": {
                "sha256": _sha256(android_bytes),
                "size": len(android_bytes),
            },
        },
    }
    return {
        "command-catalog.json": catalog_bytes,
        "field-registry.json": fields_bytes,
        "manifest.json": _json_bytes(manifest),
        "report": report_bytes,
        "android": android_bytes,
        "summary": manifest,
    }


def build_protocol_evidence(
    capture_root,
    client_root,
    evidence_root,
    output_root,
    report_path,
    client_version="9.2.2",
    android_contract_path=None,
):
    outputs = _build_outputs(
        capture_root, client_root, evidence_root, client_version
    )
    output_root = Path(output_root)
    for name in ("command-catalog.json", "field-registry.json", "manifest.json"):
        _write(output_root / name, outputs[name])
    _write(Path(report_path), outputs["report"])
    if android_contract_path is not None:
        _write(Path(android_contract_path), outputs["android"])
    return outputs["summary"]


def check_protocol_evidence(
    capture_root,
    client_root,
    evidence_root,
    output_root,
    report_path,
    client_version="9.2.2",
    android_contract_path=None,
):
    outputs = _build_outputs(
        capture_root, client_root, evidence_root, client_version
    )
    expected = {
        Path(output_root) / "command-catalog.json": outputs["command-catalog.json"],
        Path(output_root) / "field-registry.json": outputs["field-registry.json"],
        Path(output_root) / "manifest.json": outputs["manifest.json"],
        Path(report_path): outputs["report"],
    }
    if android_contract_path is not None:
        expected[Path(android_contract_path)] = outputs["android"]
    stale = [
        str(path)
        for path, data in expected.items()
        if not path.is_file() or path.read_bytes() != data
    ]
    if stale:
        raise ValueError("protocol evidence is out of date: " + ", ".join(stale))
    return outputs["summary"]
