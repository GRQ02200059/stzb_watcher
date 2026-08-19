# -*- mode: python ; coding: utf-8 -*-
from PyInstaller.utils.hooks import collect_submodules
from pathlib import Path

ROOT = Path(SPECPATH).resolve().parents[1]

hiddenimports = [
    "api_server", "realtime_writer", "scrapy_v2", "profile_manager",
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
        (str(ROOT / "hero_scraper"), "hero_scraper"),
        (str(ROOT / "battle-engine"), "battle-engine"),
    ],
    hiddenimports=hiddenimports, hookspath=[str(ROOT / "packaging/pyinstaller/hooks")],
    noarchive=False,
)
pyz = PYZ(a.pure)
exe = EXE(pyz, a.scripts, a.binaries, a.datas, [], name="STZB助手-Web",
         debug=False, bootloader_ignore_signals=False, strip=False, upx=False, console=True)
