from dataclasses import dataclass
from pathlib import Path
from typing import Dict, Iterable, List
import hashlib
import json
import re
import struct


SCHEMA_TABLE_ALLOWLIST = (
    "Tb_army",
    "Tb_army_alert",
    "Tb_battle_report_attack",
    "Tb_battle_report_defend",
    "Tb_fight_area",
    "Tb_force_info",
    "Tb_hero",
    "Tb_union_army_group",
    "Tb_union_assembly",
    "Tb_user_union_attr",
    "Tb_war_ship",
    "Tb_world_city",
)

CARD_EXTRACT_PATTERN = re.compile(r"tb_cfg_card_extract(?:_(\d+))?\.bin$")
HERO_ID_FACTOR = 1_000_000


@dataclass(frozen=True)
class CardPackRow:
    pack_id: int
    parent_pack_id: int
    container_pack_id: int
    priority: int
    source_config: str


class LittleEndianReader:
    def __init__(self, data: bytes, source: str) -> None:
        self.data = data
        self.source = source
        self.position = 0

    def _take(self, size: int) -> bytes:
        if size < 0 or self.position + size > len(self.data):
            raise ValueError(f"unexpected end of MemoryPack table: {self.source}")
        value = self.data[self.position : self.position + size]
        self.position += size
        return value

    def byte(self) -> int:
        return self._take(1)[0]

    def int(self) -> int:
        return struct.unpack("<i", self._take(4))[0]

    def skip(self, size: int) -> None:
        self._take(size)

    def int_array(self) -> None:
        length = self.int()
        if length < 0:
            raise ValueError(f"invalid array length in {self.source}")
        self.skip(length * 4)

    def nested_int_array(self) -> None:
        length = self.int()
        if length < 0:
            raise ValueError(f"invalid nested array length in {self.source}")
        for _ in range(length):
            self.int_array()

    def memory_pack_string(self) -> None:
        length = self.int()
        if length == -1:
            return
        if length >= 0:
            self.skip(length * 2)
            return
        byte_count = ~length
        self.int()
        self.skip(byte_count)


def _open_memory_pack(path: Path):
    reader = LittleEndianReader(path.read_bytes(), path.name)
    string_table_length = reader.int()
    if string_table_length < 4:
        raise ValueError(f"invalid string table in {path.name}")
    string_table_end = reader.position + string_table_length
    string_count = reader.int()
    if string_count < -1:
        raise ValueError(f"invalid string count in {path.name}")
    for _ in range(max(0, string_count)):
        reader.memory_pack_string()
    if reader.position != string_table_end:
        raise ValueError(f"invalid string table boundary in {path.name}")
    if reader.byte() != 2:
        raise ValueError(f"invalid table header in {path.name}")
    key_count = reader.int()
    if key_count < 0:
        raise ValueError(f"invalid key count in {path.name}")
    keys = [reader.int() for _ in range(key_count)]
    if reader.int() != key_count:
        raise ValueError(f"key/value count mismatch in {path.name}")
    return keys, reader


def _source_config(path: Path) -> str:
    match = CARD_EXTRACT_PATTERN.fullmatch(path.name)
    if match is None:
        raise ValueError(f"unsupported card extract table: {path.name}")
    return match.group(1) or "default"


def _parse_card_extract(path: Path) -> List[CardPackRow]:
    keys, reader = _open_memory_pack(path)
    source_config = _source_config(path)
    rows = []
    for key in keys:
        if reader.byte() != 62:
            raise ValueError(f"invalid card extract row in {path.name}")
        pack_id = reader.int()
        parent_pack_id = reader.int()
        container_pack_id = reader.int()
        for _ in range(13):
            reader.int()
        priority = reader.int()
        for _ in range(10):
            reader.int()
        reader.skip(17)
        reader.int_array()
        for _ in range(3):
            reader.nested_int_array()
        for _ in range(14):
            reader.int()
        if key != pack_id:
            raise ValueError(f"card pack key mismatch in {path.name}: {key}")
        rows.append(
            CardPackRow(
                pack_id,
                parent_pack_id,
                container_pack_id,
                priority,
                source_config,
            )
        )
    return rows


def _parse_card_prob(path: Path) -> Dict[int, List[int]]:
    keys, reader = _open_memory_pack(path)
    pools: Dict[int, List[int]] = {}
    for key in keys:
        if reader.byte() != 1:
            raise ValueError(f"invalid card probability row in {path.name}")
        refresh_way_hero_id = reader.int()
        if key != refresh_way_hero_id:
            raise ValueError(f"card probability key mismatch in {path.name}: {key}")
        pack_id, hero_id = divmod(refresh_way_hero_id, HERO_ID_FACTOR)
        if pack_id > 0 and hero_id > 0:
            pools.setdefault(pack_id, []).append(hero_id)
    return pools


def parse_card_pack_tables(config_root: Path) -> List[dict]:
    config_root = Path(config_root)
    rows_by_id: Dict[int, CardPackRow] = {}
    source_configs: Dict[int, set] = {}
    direct_pools: Dict[int, set] = {}

    extract_paths = sorted(
        (
            path
            for path in config_root.glob("tb_cfg_card_extract*.bin")
            if CARD_EXTRACT_PATTERN.fullmatch(path.name)
        ),
        key=lambda path: (
            _source_config(path) != "default",
            int(_source_config(path)) if _source_config(path).isdigit() else 0,
        ),
    )
    if not extract_paths:
        raise ValueError("no card extract tables found")

    for extract_path in extract_paths:
        source_config = _source_config(extract_path)
        for row in _parse_card_extract(extract_path):
            rows_by_id.setdefault(row.pack_id, row)
            source_configs.setdefault(row.pack_id, set()).add(source_config)
        suffix = "" if source_config == "default" else f"_{source_config}"
        prob_path = config_root / f"tb_cfg_card_prob{suffix}.bin"
        if prob_path.is_file():
            for pack_id, hero_ids in _parse_card_prob(prob_path).items():
                direct_pools.setdefault(pack_id, set()).update(hero_ids)

    def resolve(pack_id: int, visiting=frozenset()) -> List[int]:
        direct = direct_pools.get(pack_id)
        if direct:
            return sorted(direct)
        if pack_id in visiting:
            return []
        children = [
            row.pack_id
            for row in rows_by_id.values()
            if row.parent_pack_id == pack_id or row.container_pack_id == pack_id
        ]
        return sorted(
            {
                hero_id
                for child_id in children
                for hero_id in resolve(child_id, visiting | {pack_id})
            }
        )

    result = []
    for row in sorted(rows_by_id.values(), key=lambda item: (item.priority, item.pack_id)):
        hero_ids = resolve(row.pack_id)
        if not hero_ids:
            continue
        result.append(
            {
                "packId": row.pack_id,
                "parentPackId": row.parent_pack_id,
                "containerPackId": row.container_pack_id,
                "priority": row.priority,
                "heroIds": hero_ids,
                "heroCount": len(hero_ids),
                "sourceConfigs": sorted(
                    source_configs.get(row.pack_id, {row.source_config}),
                    key=lambda item: (item != "default", int(item) if item.isdigit() else 0),
                ),
            }
        )
    return result


def _standardize_command(command: dict) -> dict:
    return {
        "id": int(command.get("id") or 0),
        "names": sorted({str(name) for name in command.get("names") or [] if name}),
        "requestSources": sorted(
            {str(source) for source in command.get("requestSources") or [] if source}
        ),
        "receiveSources": sorted(
            {str(source) for source in command.get("receiveSources") or [] if source}
        ),
        "captureSendCount": int(command.get("captureSendCount") or 0),
        "captureReceiveCount": int(command.get("captureReceiveCount") or 0),
    }


def build_protocol_catalog(old_payload: dict, new_payload: dict) -> dict:
    old_version = str(old_payload.get("clientVersion") or "")
    new_version = str(new_payload.get("clientVersion") or "")
    old_commands = {
        row["id"]: row
        for row in map(_standardize_command, old_payload.get("commands") or [])
        if row["id"] > 0
    }
    new_commands = {
        row["id"]: row
        for row in map(_standardize_command, new_payload.get("commands") or [])
        if row["id"] > 0
    }
    added = [
        {"id": command_id, "names": new_commands[command_id]["names"]}
        for command_id in sorted(new_commands.keys() - old_commands.keys())
    ]
    removed = [
        {"id": command_id, "names": old_commands[command_id]["names"]}
        for command_id in sorted(old_commands.keys() - new_commands.keys())
    ]
    renamed = [
        {
            "id": command_id,
            "oldNames": old_commands[command_id]["names"],
            "newNames": new_commands[command_id]["names"],
        }
        for command_id in sorted(old_commands.keys() & new_commands.keys())
        if old_commands[command_id]["names"] != new_commands[command_id]["names"]
    ]
    return {
        "versions": {
            old_version: [old_commands[key] for key in sorted(old_commands)],
            new_version: [new_commands[key] for key in sorted(new_commands)],
        },
        "diff": {
            "summary": {
                "added": len(added),
                "removed": len(removed),
                "renamed": len(renamed),
            },
            "added": added,
            "removed": removed,
            "renamed": renamed,
        },
    }


def select_table_fields(field_types: dict) -> dict:
    tables = {}
    for table_name in SCHEMA_TABLE_ALLOWLIST:
        rows = field_types.get(table_name)
        if not isinstance(rows, list):
            raise ValueError(f"missing schema table: {table_name}")
        fields = []
        for index, row in enumerate(rows):
            if not isinstance(row, list) or len(row) != 2:
                raise ValueError(f"invalid schema field in {table_name}")
            fields.append(
                {
                    "index": index,
                    "name": str(row[0]),
                    "type": str(row[1]),
                }
            )
        tables[table_name] = {"fieldCount": len(fields), "fields": fields}
    return {"allowlist": list(SCHEMA_TABLE_ALLOWLIST), "tables": tables}


def _write_json(path: Path, payload) -> None:
    path.write_text(
        json.dumps(payload, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )


def _sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def build_research_snapshot(
    source_root: Path,
    output_root: Path,
    generated_at: str,
) -> dict:
    source_root = Path(source_root).resolve()
    output_root = Path(output_root)
    config_root = source_root / "client-config"
    protocol_root = source_root / "protocol"
    card_packs = parse_card_pack_tables(config_root)
    protocol = build_protocol_catalog(
        json.loads(
            (protocol_root / "client-9.2.2-command-inventory.json").read_text(
                encoding="utf-8"
            )
        ),
        json.loads(
            (protocol_root / "client-9.2.4-command-inventory.json").read_text(
                encoding="utf-8"
            )
        ),
    )
    table_fields = select_table_fields(
        json.loads((source_root / "tb_field_types.json").read_text(encoding="utf-8"))
    )

    output_root.mkdir(parents=True, exist_ok=True)
    payloads = {
        "card_packs.json": {
            "datasetVersion": "client-9.2.2-research",
            "evidenceClass": "CONFIG_FACT",
            "packs": card_packs,
        },
        "protocol_commands.json": {
            "datasetVersion": "client-9.2.2-9.2.4-protocol",
            "evidenceClass": "PROTOCOL_CATALOG",
            **protocol,
        },
        "table_fields.json": {
            "datasetVersion": "client-9.2.2-schema",
            "evidenceClass": "SCHEMA_FACT",
            **table_fields,
        },
    }
    for name, payload in payloads.items():
        _write_json(output_root / name, payload)

    files = [
        {
            "target": name,
            "bytes": (output_root / name).stat().st_size,
            "sha256": _sha256(output_root / name),
        }
        for name in sorted(payloads)
    ]
    validation = {
        "cardPackCount": len(card_packs),
        "uniqueCardPackCount": len({row["packId"] for row in card_packs}),
        "protocolVersions": sorted(protocol["versions"]),
        "protocolDiff": protocol["diff"]["summary"],
        "schemaTableCount": len(table_fields["tables"]),
    }
    manifest = {
        "datasetVersion": "client-9.2.2-research",
        "generatedAt": generated_at,
        "sourceRoot": str(source_root),
        "files": files,
        "validation": validation,
        "privacy": {
            "containsAccountData": False,
            "containsCapturePayloads": False,
            "containsPlayerOrAllianceData": False,
        },
    }
    _write_json(output_root / "manifest.json", manifest)
    (output_root / "checksums.sha256").write_text(
        "".join(f"{item['sha256']}  {item['target']}\n" for item in files),
        encoding="utf-8",
    )
    return manifest
