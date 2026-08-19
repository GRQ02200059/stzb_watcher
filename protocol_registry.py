from copy import deepcopy
import hashlib
import json
from pathlib import Path

from protocol_evidence.catalog import decimal_command_id, normalize_hex_id


APPROVED_LEVELS = {"CLIENT_CONFIRMED", "CAPTURE_CONFIRMED"}


class ProtocolRegistry:
    def __init__(self, root: Path):
        self.root = Path(root)
        manifest = json.loads(
            (self.root / "manifest.json").read_text(encoding="utf-8")
        )
        for name in ("command-catalog.json", "field-registry.json"):
            path = self.root / name
            data = path.read_bytes()
            expected = manifest.get("files", {}).get(name, {})
            if expected.get("size") != len(data) or expected.get("sha256") != hashlib.sha256(data).hexdigest():
                raise ValueError(f"protocol registry checksum mismatch: {name}")
        catalog = json.loads(
            (self.root / "command-catalog.json").read_text(encoding="utf-8")
        )
        fields = json.loads(
            (self.root / "field-registry.json").read_text(encoding="utf-8")
        )
        self._commands = {
            row["hexId"]: row for row in catalog.get("commands", [])
        }
        self._fields = {
            (row["hexId"], row["path"]): row
            for row in fields.get("fields", [])
        }

    def _hex_id(self, value) -> str:
        if isinstance(value, bool):
            raise ValueError("boolean is not a command id")
        if isinstance(value, int):
            if value < 0 or value > 0xFFFFFFFF:
                raise ValueError(f"invalid command id: {value}")
            return f"{value:08x}"
        return normalize_hex_id(value)

    def command(self, hex_or_decimal):
        row = self._commands.get(self._hex_id(hex_or_decimal))
        return deepcopy(row) if row is not None else None

    def field(self, command_id, path):
        row = self._fields.get((self._hex_id(command_id), str(path)))
        return deepcopy(row) if row is not None else None

    def require_business_field(self, command_id, path):
        row = self.field(command_id, path)
        if row is None:
            raise ValueError(
                f"protocol field is not registered: {self._hex_id(command_id)} {path}"
            )
        if not row.get("businessApproved") or row.get("evidence") not in APPROVED_LEVELS:
            raise ValueError(
                f"protocol field is not approved for business use: {self._hex_id(command_id)} {path}"
            )
        return row
