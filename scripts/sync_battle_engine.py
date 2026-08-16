#!/usr/bin/env python3
import argparse
import hashlib
import json
import subprocess
from datetime import datetime
from pathlib import Path


PACKAGE_REPLACEMENTS = (
    ("com.stzb.server.game.battle.skill", "com.stzb.battle.core.skill"),
    ("com.stzb.server.game.battle", "com.stzb.battle.core"),
    ("com.stzb.server.game", "com.stzb.battle.core"),
)

CORE_SOURCE_ROOT = Path(
    "src/main/kotlin/com/stzb/server/game/battle"
)
EXTRA_SOURCE_FILES = (
    Path("src/main/kotlin/com/stzb/server/game/ClientNpcArmyRepository.kt"),
    Path("src/main/kotlin/com/stzb/server/game/SkillInventoryCatalog.kt"),
)
RESOURCE_PATTERNS = (
    "src/main/resources/battle-config/*",
    "src/main/resources/client-config/tb_cfg_gear.bin",
    "src/main/resources/client-config/tb_cfg_gear_feature.bin",
    "src/main/resources/client-config/tb_cfg_hero_type_feature.bin",
)
TEST_SOURCE_ROOT = Path(
    "src/test/kotlin/com/stzb/server/game/battle"
)
REQUIRED_TEST_FILES = (
    "BattleEngineTest.kt",
    "BattleFormationCalculatorTest.kt",
    "BattleDamageCalculatorTest.kt",
    "BattleEffectStateTest.kt",
    "BattleTeamBuilderTest.kt",
    "BattleReportCodecTest.kt",
    "ClientBattleTextReplayAdapterTest.kt",
    "ClientBattleTextReplayProtocolTest.kt",
    "OfficialReportFixture.kt",
    "OfficialReportFixtureTest.kt",
    "OfficialFullBattleReportDiffTest.kt",
    "OfficialPreparationReportDiffTest.kt",
    "skill/CompleteSkillEngineIntegrationTest.kt",
    "skill/ControlEffectHandlersTest.kt",
    "skill/SkillConditionInterpreterTest.kt",
    "skill/SkillRuleInterpreterTest.kt",
    "skill/SkillTimingTest.kt",
)
TEST_FIXTURE_PATTERNS = (
    "assent/cfg/paper.zip",
    "assent/cfg/paper/11/*",
    "assent/cfg/paper/6231/*",
    "src/test/resources/skill-condition-plugin-owners.csv",
)
TEST_FIXTURE_PATH_FILES = {
    "ClientBattleTextReplayProtocolTest.kt",
    "OfficialReportFixture.kt",
    "OfficialReportFixtureTest.kt",
    "OfficialFullBattleReportDiffTest.kt",
    "OfficialPreparationReportDiffTest.kt",
}
EXCLUDED_TEST_REASON = (
    "not selected for the standalone core regression allowlist"
)
KNOWN_SOURCE_TEST_FAILURES = (
    (
        "BattleTeamBuilderTest",
        "surface cautious attack declares and applies physical damage reduction",
    ),
    (
        "OfficialFullBattleReportDiffTest",
        "official full battles stay inside deterministic simulation envelopes",
    ),
    (
        "OfficialFullBattleReportDiffTest",
        "baizhan and huangyi paper recovery stays inside deterministic simulation envelope",
    ),
    (
        "OfficialFullBattleReportDiffTest",
        "shuangyan paper total triggers include its zhengshi retrigger",
    ),
    (
        "OfficialFullBattleReportDiffTest",
        "fenji paper repeatedly reaches the attacker front",
    ),
    (
        "OfficialFullBattleReportDiffTest",
        "dual huangyi paper defender offense and attacker recovery stay inside simulation envelope",
    ),
    (
        "OfficialFullBattleReportDiffTest",
        "morale huangyi paper recovery stays inside deterministic simulation envelope",
    ),
    (
        "OfficialFullBattleReportDiffTest",
        "first round defender victory can reproduce the attacker base defeat",
    ),
    (
        "skill.SkillConditionInterpreterTest",
        "scoped condition inventory is an independent literal",
    ),
)
KNOWN_SOURCE_FAILURE_REASON = (
    "reproduces in the source repository at the recorded source commit"
)

REDUNDANT_IMPORTS = (
    "import com.stzb.battle.core.ClientTroopFeatureRepository\n",
    "import com.stzb.battle.core.ClientEquipmentSkillRepository\n",
    "import com.stzb.battle.core.ClientTroopTypeRepository\n",
)


def normalize_source_package(text):
    for source, target in PACKAGE_REPLACEMENTS:
        text = text.replace(source, target)
    return text


def apply_standalone_adapter(file_name, text):
    if file_name == "BattleEquipmentRepository.kt":
        adapted = text.replace(
            "import com.stzb.battle.core.MemoryPackTable\n", ""
        )
        marker = "private val CONFIG_PATHS = listOf(\n"
        if marker not in adapted:
            raise ValueError(
                "standalone adapter marker missing: %s" % file_name
            )
        adapted = adapted.replace(
            marker,
            marker
            + '            Path.of("src/main/resources/battle-config"),\n',
            1,
        )
        return adapted, "standalone-resource-path"
    if file_name in {
        "BattleFormationCalculator.kt",
        "BattleTeamBuilder.kt",
    }:
        adapted = text
        for line in REDUNDANT_IMPORTS:
            adapted = adapted.replace(line, "")
        return adapted, "remove-redundant-same-package-imports"
    if file_name == "ClientBattleReportStore.kt":
        adapted = text.replace(
            "import com.stzb.server.protocol.GameServerConfig\n", ""
        )
        adapted = adapted.replace(
            'put("attack_name", GameServerConfig.ROLE_NAME)',
            'put("attack_name", "模拟攻方")',
        )
        adapted = adapted.replace(
            "wid = GameServerConfig.CITY_WID + 1,",
            "wid = 10001,",
        )
        return adapted, "remove-server-config-dependency"
    if file_name in TEST_FIXTURE_PATH_FILES:
        adapted = text.replace(
            '"assent/cfg/paper',
            '"src/test/resources/assent/cfg/paper',
        )
        return adapted, "standalone-test-fixture-path"
    return text, ""


def _sha256_bytes(value):
    return hashlib.sha256(value).hexdigest()


def _sha256_file(path):
    return _sha256_bytes(path.read_bytes())


def _git_commit(root):
    result = subprocess.run(
        ["git", "rev-parse", "HEAD"],
        cwd=root,
        check=True,
        capture_output=True,
        text=True,
    )
    return result.stdout.strip()


def _target_for_source(source_path, source_root):
    relative = source_path.relative_to(source_root)
    parts = relative.parts
    if parts[:6] == (
        "src", "main", "kotlin", "com", "stzb", "server"
    ):
        suffix = list(parts[6:])
        if suffix[:2] == ["game", "battle"]:
            suffix = suffix[2:]
        elif suffix[:1] == ["game"]:
            suffix = suffix[1:]
        return Path(
            "src/main/kotlin/com/stzb/battle/core", *suffix
        )
    if parts[:3] == ("src", "main", "resources"):
        return Path("src/main/resources", *parts[3:])
    if parts[:6] == (
        "src", "test", "kotlin", "com", "stzb", "server"
    ):
        suffix = list(parts[6:])
        if suffix[:2] == ["game", "battle"]:
            suffix = suffix[2:]
        return Path(
            "src/test/kotlin/com/stzb/battle/core", *suffix
        )
    if parts[:3] == ("assent", "cfg", "paper.zip"):
        return Path(
            "src/test/resources/assent/cfg/paper.zip"
        )
    if parts[:3] == ("assent", "cfg", "paper"):
        return Path("src/test/resources", *parts)
    if parts[:3] == ("src", "test", "resources"):
        return Path("src/test/resources", *parts[3:])
    raise ValueError("unsupported battle engine source: %s" % relative)


def _source_files(source_root):
    files = list((source_root / CORE_SOURCE_ROOT).rglob("*.kt"))
    files.extend(
        source_root / relative
        for relative in EXTRA_SOURCE_FILES
        if (source_root / relative).is_file()
    )
    for pattern in RESOURCE_PATTERNS:
        files.extend(
            path for path in source_root.glob(pattern) if path.is_file()
        )
    files.extend(
        source_root / TEST_SOURCE_ROOT / relative
        for relative in REQUIRED_TEST_FILES
        if (source_root / TEST_SOURCE_ROOT / relative).is_file()
    )
    for pattern in TEST_FIXTURE_PATTERNS:
        files.extend(
            path for path in source_root.glob(pattern) if path.is_file()
        )
    return sorted(set(files))


def _source_test_files(source_root):
    root = source_root / TEST_SOURCE_ROOT
    if not root.is_dir():
        return []
    return sorted(root.rglob("*.kt"))


def _generated_bytes(path):
    if path.suffix == ".kt":
        normalized = normalize_source_package(
            path.read_text(encoding="utf-8")
        )
        adapted, _ = apply_standalone_adapter(path.name, normalized)
        return adapted.encode("utf-8")
    return path.read_bytes()


def _manifest(source_root, target_root):
    rows = []
    for source_path in _source_files(source_root):
        target = _target_for_source(source_path, source_root)
        generated = _generated_bytes(source_path)
        adapter = ""
        if source_path.suffix == ".kt":
            _, adapter = apply_standalone_adapter(
                source_path.name,
                normalize_source_package(
                    source_path.read_text(encoding="utf-8")
                ),
            )
        rows.append(
            {
                "source": str(source_path.relative_to(source_root)),
                "target": str(target),
                "sourceSha256": _sha256_file(source_path),
                "generatedSha256": _sha256_bytes(generated),
                "adapter": adapter,
            }
        )
    included_test_sources = {
        row["source"]
        for row in rows
        if row["target"].startswith("src/test/kotlin/")
    }
    excluded_tests = [
        {
            "source": str(path.relative_to(source_root)),
            "reason": EXCLUDED_TEST_REASON,
        }
        for path in _source_test_files(source_root)
        if str(path.relative_to(source_root))
        not in included_test_sources
    ]
    known_source_test_failures = []
    included_target_classes = {
        row["target"]
        .removeprefix("src/test/kotlin/")
        .removesuffix(".kt")
        .replace("/", ".")
        for row in rows
        if row["target"].startswith("src/test/kotlin/")
    }
    for class_suffix, method in KNOWN_SOURCE_TEST_FAILURES:
        source_class = (
            "com.stzb.server.game.battle.%s" % class_suffix
        )
        target_class = "com.stzb.battle.core.%s" % class_suffix
        if target_class not in included_target_classes:
            continue
        known_source_test_failures.append(
            {
                "sourceClass": source_class,
                "targetClass": target_class,
                "method": method,
                "reason": KNOWN_SOURCE_FAILURE_REASON,
            }
        )
    return {
        "schemaVersion": 1,
        "sourceRepository": str(source_root.resolve()),
        "sourceCommit": _git_commit(source_root),
        "generatedAt": datetime.now().astimezone().isoformat(
            timespec="seconds"
        ),
        "packageMapping": {
            source: target for source, target in PACKAGE_REPLACEMENTS
        },
        "adapters": sorted(
            {
                row["adapter"]
                for row in rows
                if row["adapter"]
            }
        ),
        "files": rows,
        "resources": [
            row for row in rows if not row["target"].endswith(".kt")
        ],
        "tests": [
            row
            for row in rows
            if row["target"].startswith("src/test/kotlin/")
        ],
        "excludedTests": excluded_tests,
        "knownSourceTestFailures": known_source_test_failures,
    }


def _verify_manifest(target_root, expected):
    manifest_path = target_root / "SOURCE.json"
    if not manifest_path.is_file():
        raise ValueError("SOURCE.json missing")
    actual = json.loads(manifest_path.read_text(encoding="utf-8"))
    if actual.get("sourceCommit") != expected["sourceCommit"]:
        raise ValueError("source commit drift")
    expected_by_target = {
        row["target"]: row for row in expected["files"]
    }
    actual_by_target = {
        row["target"]: row for row in actual.get("files", [])
    }
    if set(expected_by_target) != set(actual_by_target):
        raise ValueError("generated file set drift")
    for target, expected_row in expected_by_target.items():
        path = target_root / target
        if not path.is_file():
            raise ValueError("generated file missing: %s" % target)
        if _sha256_file(path) != expected_row["generatedSha256"]:
            raise ValueError("generated file drift: %s" % target)
        if (
            actual_by_target[target].get("generatedSha256")
            != expected_row["generatedSha256"]
        ):
            raise ValueError("manifest checksum drift: %s" % target)
    if actual.get("excludedTests", []) != expected["excludedTests"]:
        raise ValueError("source test accounting drift")
    if (
        actual.get("knownSourceTestFailures", [])
        != expected["knownSourceTestFailures"]
    ):
        raise ValueError("known source test failure drift")


def sync_engine(source_root, target_root, check=False):
    source_root = Path(source_root).resolve()
    target_root = Path(target_root).resolve()
    manifest = _manifest(source_root, target_root)
    if check:
        _verify_manifest(target_root, manifest)
        return manifest
    target_root.mkdir(parents=True, exist_ok=True)
    for row in manifest["files"]:
        source_path = source_root / row["source"]
        target_path = target_root / row["target"]
        target_path.parent.mkdir(parents=True, exist_ok=True)
        target_path.write_bytes(_generated_bytes(source_path))
    (target_root / "SOURCE.json").write_text(
        json.dumps(
            manifest, ensure_ascii=False, indent=2, sort_keys=True
        )
        + "\n",
        encoding="utf-8",
    )
    return manifest


def main(argv=None):
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", type=Path, required=True)
    parser.add_argument("--target-root", type=Path, required=True)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args(argv)
    manifest = sync_engine(
        args.source_root, args.target_root, check=args.check
    )
    if args.check:
        print("battle engine mirror check: PASS")
    else:
        print(
            "synced battle engine from %s" % manifest["sourceCommit"]
        )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
