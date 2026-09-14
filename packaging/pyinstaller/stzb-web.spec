# -*- mode: python ; coding: utf-8 -*-
from PyInstaller.utils.hooks import collect_submodules
from pathlib import Path
import json

ROOT = Path(SPECPATH).resolve().parents[1]

for relative in (
    "static/dashboard.html",
    "data/intelligence/client-9.2.2/manifest.json",
    "data/intelligence/client-9.2.2/research/manifest.json",
    "data/protocol/client-9.2.2/manifest.json",
    "hero_scraper/output/heroes.json",
    "hero_scraper/output/skills_full.json",
    "battle-engine/SOURCE.json",
    "battle-engine/src/main/resources/battle-config/hero_table.csv",
    "battle-engine/src/main/resources/battle-config/skill_table.csv",
):
    resource = ROOT / relative
    if not resource.is_file() or not resource.stat().st_size:
        raise RuntimeError(f"Missing or empty runtime resource: {relative}")
    if resource.suffix == ".json":
        json.loads(resource.read_text(encoding="utf-8"))

hiddenimports = [
    "api_server", "realtime_writer", "scrapy_v2", "profile_manager",
    "desktop_battle_report_sync",
    "db_build", "db_extend", "db_import", "db_import_ext",
    "db_schema_v2", "sim_data", "battle_engine_adapter",
    *collect_submodules("intelligence"), *collect_submodules("query_agent"),
    *collect_submodules("score_center"), *collect_submodules("world_scene"),
]

a = Analysis(
    [str(ROOT / "run_web_exe.py")], pathex=[str(ROOT)], binaries=[],
    datas=[
        (str(ROOT / "static"), "static"),
        (str(ROOT / "data"), "data"),
        (str(ROOT / "protocol"), "protocol"),
        (str(ROOT / "hero_scraper/output/heroes.json"), "hero_scraper/output"),
        (str(ROOT / "hero_scraper/output/skills_full.json"), "hero_scraper/output"),
        (str(ROOT / "battle-engine/SOURCE.json"), "battle-engine"),
        (str(ROOT / "battle-engine/src/main/resources/battle-config"),
         "battle-engine/src/main/resources/battle-config"),
    ],
    hiddenimports=hiddenimports, hookspath=[str(ROOT / "packaging/pyinstaller/hooks")],
    noarchive=False,
)
pyz = PYZ(a.pure)
exe = EXE(pyz, a.scripts, [], name="STZB助手-Web", exclude_binaries=True,
         debug=False, bootloader_ignore_signals=False, strip=False, upx=False, console=True)
coll = COLLECT(exe, a.binaries, a.datas, strip=False, upx=False, name="STZB-Web")
