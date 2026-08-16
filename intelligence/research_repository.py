import json
from pathlib import Path


class ResearchCatalogRepository:
    def __init__(self, root: Path, config_repository=None) -> None:
        self.root = Path(root)
        self.config_repository = config_repository
        self.load_count = 0
        self._load()

    def _load(self):
        self.load_count += 1
        self.manifest = self._json("manifest.json")
        card_payload = self._json("card_packs.json")
        protocol_payload = self._json("protocol_commands.json")
        schema_payload = self._json("table_fields.json")
        self.dataset_version = card_payload["datasetVersion"]
        self.packs = card_payload["packs"]
        self.pack_by_id = {int(row["packId"]): row for row in self.packs}
        self.pack_ids_by_hero = {}
        for pack in self.packs:
            for hero_id in pack["heroIds"]:
                self.pack_ids_by_hero.setdefault(int(hero_id), []).append(
                    int(pack["packId"])
                )
        self.protocol_dataset_version = protocol_payload["datasetVersion"]
        self.commands_by_version = {
            version: {int(row["id"]): row for row in rows}
            for version, rows in protocol_payload["versions"].items()
        }
        self.protocol_diff = protocol_payload["diff"]
        self.change_by_command = {}
        for change in ("added", "removed", "renamed"):
            for row in self.protocol_diff[change]:
                self.change_by_command[int(row["id"])] = change
        self.schema_dataset_version = schema_payload["datasetVersion"]
        self.schema_tables = schema_payload["tables"]

    def _json(self, name):
        return json.loads((self.root / name).read_text(encoding="utf-8"))

    def summary(self):
        return {
            "datasetVersion": self.dataset_version,
            "evidenceClass": "RESEARCH_CATALOG",
            "cardPackCount": len(self.packs),
            "protocolVersions": {
                version: len(commands)
                for version, commands in self.commands_by_version.items()
            },
            "protocolDiff": dict(self.protocol_diff["summary"]),
            "schemaTableCount": len(self.schema_tables),
        }

    def search_card_packs(self, query="", hero_id=None, page=1, size=50):
        page, size = _page_args(page, size)
        text = str(query or "").strip().lower()
        hero_id = int(hero_id) if hero_id not in (None, "") else None
        rows = []
        for pack in self.packs:
            if hero_id is not None and hero_id not in pack["heroIds"]:
                continue
            heroes = self._hero_rows(pack["heroIds"])
            search_text = " ".join(
                [
                    str(pack["packId"]),
                    str(pack.get("parentPackId") or ""),
                    str(pack.get("containerPackId") or ""),
                    *[str(hero.get("name") or "") for hero in heroes],
                ]
            ).lower()
            if text and text not in search_text:
                continue
            rows.append(self._pack_summary(pack, heroes))
        return _page(rows, page, size, self.dataset_version, "CONFIG_FACT")

    def hero_card_packs(self, hero_id):
        pack_ids = self.pack_ids_by_hero.get(int(hero_id), [])
        return [
            self._pack_summary(self.pack_by_id[pack_id])
            for pack_id in pack_ids
            if pack_id in self.pack_by_id
        ]

    def card_pack_detail(self, pack_id):
        pack = self.pack_by_id.get(int(pack_id))
        if pack is None:
            return None
        heroes = self._hero_rows(pack["heroIds"])
        country_distribution = _distribution(
            heroes, lambda row: row.get("country_name") or row.get("country") or "未知"
        )
        type_distribution = _distribution(
            heroes, lambda row: str(row.get("hero_type") or "未知")
        )
        return {
            "datasetVersion": self.dataset_version,
            "evidenceClass": "CONFIG_FACT",
            **self._pack_summary(pack, heroes),
            "heroIds": list(pack["heroIds"]),
            "heroes": heroes,
            "countryDistribution": country_distribution,
            "heroTypeDistribution": type_distribution,
            "children": [
                self._pack_summary(row)
                for row in self.packs
                if row.get("parentPackId") == pack["packId"]
                or row.get("containerPackId") == pack["packId"]
            ],
        }

    def _pack_summary(self, pack, heroes=None):
        return {
            "packId": int(pack["packId"]),
            "parentPackId": int(pack.get("parentPackId") or 0),
            "containerPackId": int(pack.get("containerPackId") or 0),
            "priority": int(pack.get("priority") or 0),
            "heroCount": int(pack.get("heroCount") or len(pack.get("heroIds") or [])),
            "sourceConfigs": list(pack.get("sourceConfigs") or []),
            "heroPreview": [
                {
                    "heroid": int(row.get("heroid") or 0),
                    "name": row.get("name") or "",
                }
                for row in (heroes or [])[:4]
            ],
        }

    def _hero_rows(self, hero_ids):
        rows = []
        for hero_id in hero_ids:
            hero = (
                self.config_repository.hero_by_id.get(int(hero_id))
                if self.config_repository is not None
                else None
            )
            if hero is None:
                rows.append({"heroid": int(hero_id), "name": f"武将 {hero_id}"})
                continue
            rows.append(
                {
                    "heroid": int(hero_id),
                    "name": hero.get("name") or "",
                    "country": hero.get("country") or "",
                    "country_name": hero.get("country_name") or "",
                    "quality": hero.get("quality") or "",
                    "quality_name": hero.get("quality_name") or "",
                    "hero_type": hero.get("hero_type") or "",
                    "hit_range": hero.get("hit_range") or "",
                }
            )
        return rows

    def search_commands(
        self,
        query="",
        version="all",
        change="all",
        page=1,
        size=50,
    ):
        page, size = _page_args(page, size)
        if version != "all" and version not in self.commands_by_version:
            raise ValueError("invalid protocol version")
        if change not in {"all", "added", "removed", "renamed", "stable"}:
            raise ValueError("invalid protocol change")
        text = str(query or "").strip().lower()
        command_ids = set()
        selected_versions = (
            self.commands_by_version.values()
            if version == "all"
            else [self.commands_by_version[version]]
        )
        for commands in selected_versions:
            command_ids.update(commands)
        rows = []
        for command_id in sorted(command_ids):
            commands = {
                name: rows_by_id.get(command_id)
                for name, rows_by_id in self.commands_by_version.items()
                if rows_by_id.get(command_id) is not None
            }
            names = sorted(
                {
                    name
                    for command in commands.values()
                    for name in command.get("names") or []
                }
            )
            sources = [
                source
                for command in commands.values()
                for source in (
                    command.get("requestSources", [])
                    + command.get("receiveSources", [])
                )
            ]
            search_text = f"{command_id} {' '.join(names)} {' '.join(sources)}".lower()
            if text and text not in search_text:
                continue
            status = self.change_by_command.get(command_id, "stable")
            if change != "all" and status != change:
                continue
            rows.append(
                {
                    "id": command_id,
                    "names": names,
                    "change": status,
                    "versions": sorted(commands),
                    "captureSendCount": sum(
                        int(row.get("captureSendCount") or 0)
                        for row in commands.values()
                    ),
                    "captureReceiveCount": sum(
                        int(row.get("captureReceiveCount") or 0)
                        for row in commands.values()
                    ),
                }
            )
        return _page(
            rows,
            page,
            size,
            self.protocol_dataset_version,
            "PROTOCOL_CATALOG",
        )

    def command_detail(self, command_id):
        command_id = int(command_id)
        versions = {
            version: commands[command_id]
            for version, commands in self.commands_by_version.items()
            if command_id in commands
        }
        if not versions:
            return None
        names = sorted(
            {
                name
                for command in versions.values()
                for name in command.get("names") or []
            }
        )
        return {
            "datasetVersion": self.protocol_dataset_version,
            "evidenceClass": "PROTOCOL_CATALOG",
            "id": command_id,
            "names": names,
            "change": self.change_by_command.get(command_id, "stable"),
            "versions": versions,
        }

    def search_schema(self, query="", page=1, size=50):
        page, size = _page_args(page, size)
        text = str(query or "").strip().lower()
        rows = []
        for table_name, table in self.schema_tables.items():
            search_text = " ".join(
                [
                    table_name,
                    *[field["name"] for field in table["fields"]],
                ]
            ).lower()
            if text and text not in search_text:
                continue
            rows.append(
                {
                    "table": table_name,
                    "fieldCount": table["fieldCount"],
                    "fieldPreview": table["fields"][:5],
                }
            )
        rows.sort(key=lambda row: row["table"])
        return _page(
            rows,
            page,
            size,
            self.schema_dataset_version,
            "SCHEMA_FACT",
        )

    def schema_detail(self, table_name):
        table = self.schema_tables.get(str(table_name))
        if table is None:
            return None
        return {
            "datasetVersion": self.schema_dataset_version,
            "evidenceClass": "SCHEMA_FACT",
            "table": str(table_name),
            "fieldCount": table["fieldCount"],
            "fields": list(table["fields"]),
        }


def _distribution(rows, key):
    counts = {}
    for row in rows:
        value = str(key(row))
        counts[value] = counts.get(value, 0) + 1
    return [
        {"name": name, "count": count}
        for name, count in sorted(counts.items(), key=lambda item: (-item[1], item[0]))
    ]


def _page_args(page, size):
    page = int(page)
    size = int(size)
    if page < 1 or size < 1 or size > 100:
        raise ValueError("invalid pagination")
    return page, size


def _page(rows, page, size, dataset_version, evidence_class):
    start = (page - 1) * size
    return {
        "datasetVersion": dataset_version,
        "evidenceClass": evidence_class,
        "total": len(rows),
        "page": page,
        "size": size,
        "rows": rows[start : start + size],
    }
