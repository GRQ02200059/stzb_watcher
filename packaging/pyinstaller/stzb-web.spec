# -*- mode: python ; coding: utf-8 -*-
from PyInstaller.utils.hooks import collect_submodules

hiddenimports = [
    "api_server", "realtime_writer", "scrapy_v2", "profile_manager",
    "db_build", "db_extend", "db_import", "db_import_ext",
    "db_schema_v2", "sim_data", "battle_engine_adapter",
    *collect_submodules("intelligence"), *collect_submodules("query_agent"),
    *collect_submodules("score_center"), *collect_submodules("world_scene"),
]

a = Analysis(
    ["run_web_exe.py"], pathex=["."], binaries=[],
    datas=[
        ("static", "static"), ("data", "data"),
        ("protocol", "protocol"), ("hero_scraper", "hero_scraper"),
        ("battle-engine", "battle-engine"),
    ],
    hiddenimports=hiddenimports, hookspath=["packaging/pyinstaller/hooks"],
    noarchive=False,
)
pyz = PYZ(a.pure)
exe = EXE(pyz, a.scripts, a.binaries, a.datas, [], name="STZB助手-Web",
         debug=False, bootloader_ignore_signals=False, strip=False, upx=False, console=True)
