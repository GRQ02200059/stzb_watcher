import json
from pathlib import Path

from .catalog import decimal_command_id, normalize_hex_id
from .client_source import validate_source_anchor


EVIDENCE_LEVELS = {
    "CLIENT_CONFIRMED",
    "CAPTURE_CONFIRMED",
    "IMPLEMENTATION_ASSUMED",
    "UNKNOWN",
}
BUSINESS_LEVELS = {"CLIENT_CONFIRMED", "CAPTURE_CONFIRMED"}
STATUSES = {"typed", "raw", "unsupported"}
JSON_TYPES = {"null", "boolean", "integer", "number", "string", "array", "object"}


def _require_text(value, label):
    if not isinstance(value, str) or not value.strip():
        raise ValueError(f"{label} must be a non-empty string")
    return value.strip()


def _validate_level(value):
    if value not in EVIDENCE_LEVELS:
        raise ValueError(f"invalid evidence level: {value!r}")
    return value


def _validate_sources(client_root, sources):
    if not isinstance(sources, list):
        raise ValueError("clientSources must be a list")
    for source in sources:
        validate_source_anchor(client_root, source)
    return sources


def _normalize_field(field, client_root):
    if not isinstance(field, dict):
        raise ValueError("field evidence must be an object")
    path = _require_text(field.get("path"), "field path")
    name = _require_text(field.get("name"), "field name")
    raw_types = field.get("rawTypes")
    if (
        not isinstance(raw_types, list)
        or not raw_types
        or any(value not in JSON_TYPES for value in raw_types)
    ):
        raise ValueError(f"invalid rawTypes for field {path}")
    nullable = field.get("nullable")
    if not isinstance(nullable, bool):
        raise ValueError(f"nullable must be boolean for field {path}")
    unit = _require_text(field.get("unit"), "field unit")
    level = _validate_level(field.get("evidence"))
    business_approved = field.get("businessApproved", False)
    if not isinstance(business_approved, bool):
        raise ValueError(f"businessApproved must be boolean for field {path}")
    if business_approved and level not in BUSINESS_LEVELS:
        raise ValueError(
            f"business-approved field {path} requires confirmed evidence"
        )
    sources = _validate_sources(client_root, field.get("clientSources", []))
    return {
        "path": path,
        "name": name,
        "rawTypes": sorted(set(raw_types)),
        "nullable": nullable,
        "unit": unit,
        "evidence": level,
        "businessApproved": business_approved,
        "clientSources": sources,
    }


def _normalize_command(command, client_root, constants):
    if not isinstance(command, dict):
        raise ValueError("command evidence must be an object")
    hex_id = normalize_hex_id(command.get("hexId"))
    decimal_id = command.get("decimalId")
    if isinstance(decimal_id, bool) or not isinstance(decimal_id, int):
        raise ValueError(f"decimalId must be integer for {hex_id}")
    if decimal_command_id(hex_id) != decimal_id:
        raise ValueError(f"command id mismatch for {hex_id}")
    names = command.get("names")
    if not isinstance(names, list) or not names or any(not isinstance(x, str) or not x for x in names):
        raise ValueError(f"command names are required for {hex_id}")
    known_names = set(constants.get(decimal_id, []))
    if not set(names).issubset(known_names):
        raise ValueError(f"command constant mismatch for {hex_id}")
    level = _validate_level(command.get("evidence"))
    web_status = command.get("webStatus")
    android_status = command.get("androidStatus")
    if web_status not in STATUSES or android_status not in STATUSES:
        raise ValueError(f"invalid implementation status for {hex_id}")
    sources = _validate_sources(client_root, command.get("clientSources", []))
    fields = [_normalize_field(item, client_root) for item in command.get("fields", [])]
    paths = [field["path"] for field in fields]
    if len(paths) != len(set(paths)):
        raise ValueError(f"duplicate field path for {hex_id}")
    return {
        "hexId": hex_id,
        "decimalId": decimal_id,
        "names": sorted(set(names)),
        "evidence": level,
        "webStatus": web_status,
        "androidStatus": android_status,
        "clientSources": sources,
        "fields": sorted(fields, key=lambda item: item["path"]),
    }


def load_evidence_files(evidence_root: Path, client_root: Path, constants: dict) -> dict[str, dict]:
    evidence_root = Path(evidence_root)
    result = {}
    for path in sorted(evidence_root.glob("*.json")):
        payload = json.loads(path.read_text(encoding="utf-8"))
        commands = payload.get("commands") if isinstance(payload, dict) else None
        if not isinstance(commands, list):
            raise ValueError(f"evidence file must contain commands: {path.name}")
        for command in commands:
            normalized = _normalize_command(command, Path(client_root), constants)
            hex_id = normalized["hexId"]
            if hex_id in result:
                raise ValueError(f"duplicate command evidence: {hex_id}")
            result[hex_id] = normalized
    return dict(sorted(result.items()))
