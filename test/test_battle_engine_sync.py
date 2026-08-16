import json
import subprocess
import tempfile
import unittest
from pathlib import Path

from scripts.sync_battle_engine import (
    REQUIRED_TEST_FILES,
    apply_standalone_adapter,
    normalize_source_package,
    sync_engine,
)


class BattleEngineSyncTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        root = Path(self.temp.name)
        self.source = root / "source"
        self.target = root / "target"
        battle = (
            self.source
            / "src/main/kotlin/com/stzb/server/game/battle"
        )
        battle.mkdir(parents=True)
        (battle / "BattleEngine.kt").write_text(
            "\n".join(
                [
                    "package com.stzb.server.game.battle",
                    "",
                    "import com.stzb.server.game.battle.skill.SkillRule",
                    "",
                    "object BattleEngine { fun name() = \"source\" }",
                    "",
                ]
            ),
            encoding="utf-8",
        )
        skill = battle / "skill"
        skill.mkdir()
        (skill / "SkillRule.kt").write_text(
            "\n".join(
                [
                    "package com.stzb.server.game.battle.skill",
                    "",
                    "class SkillRule",
                    "",
                ]
            ),
            encoding="utf-8",
        )
        test_root = (
            self.source
            / "src/test/kotlin/com/stzb/server/game/battle"
        )
        for file_name in REQUIRED_TEST_FILES:
            relative = Path(file_name)
            path = test_root / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            package = "com.stzb.server.game.battle"
            if relative.parent != Path("."):
                package += "." + ".".join(relative.parent.parts)
            path.write_text(
                "\n".join(
                    [
                        "package %s" % package,
                        "",
                        "class %s" % relative.stem,
                        "",
                    ]
                ),
                encoding="utf-8",
            )
        (test_root / "NotMirroredTest.kt").write_text(
            "\n".join(
                [
                    "package com.stzb.server.game.battle",
                    "",
                    "class NotMirroredTest",
                    "",
                ]
            ),
            encoding="utf-8",
        )
        resources = self.source / "src/main/resources/battle-config"
        resources.mkdir(parents=True)
        (resources / "hero_table.csv").write_text(
            "heroid,name\n100027,张辽\n",
            encoding="utf-8",
        )
        fixture_root = self.source / "assent/cfg"
        (fixture_root / "paper/11").mkdir(parents=True)
        (fixture_root / "paper/6231").mkdir(parents=True)
        (fixture_root / "paper.zip").write_bytes(b"paper zip")
        (fixture_root / "paper/11/report.json").write_text(
            '{"report":"11"}\n',
            encoding="utf-8",
        )
        (fixture_root / "paper/6231/report.json").write_text(
            '{"report":"6231"}\n',
            encoding="utf-8",
        )
        test_resources = self.source / "src/test/resources"
        test_resources.mkdir(parents=True)
        (test_resources / "skill-condition-plugin-owners.csv").write_text(
            "skillId,field,value,owner\n",
            encoding="utf-8",
        )
        subprocess.run(["git", "init", "-q"], cwd=self.source, check=True)
        subprocess.run(
            ["git", "config", "user.email", "test@example.com"],
            cwd=self.source,
            check=True,
        )
        subprocess.run(
            ["git", "config", "user.name", "Test"],
            cwd=self.source,
            check=True,
        )
        subprocess.run(["git", "add", "."], cwd=self.source, check=True)
        subprocess.run(
            ["git", "commit", "-qm", "fixture"],
            cwd=self.source,
            check=True,
        )

    def tearDown(self):
        self.temp.cleanup()

    def test_sync_records_source_commit_and_file_hashes(self):
        result = sync_engine(self.source, self.target)

        self.assertEqual(result["schemaVersion"], 1)
        self.assertEqual(result["sourceRepository"], str(self.source.resolve()))
        self.assertRegex(result["sourceCommit"], r"^[0-9a-f]{40}$")
        self.assertIn(
            "src/main/kotlin/com/stzb/battle/core/BattleEngine.kt",
            {row["target"] for row in result["files"]},
        )
        self.assertTrue((self.target / "SOURCE.json").is_file())
        self.assertTrue(
            (
                self.target
                / "src/main/resources/battle-config/hero_table.csv"
            ).is_file()
        )

    def test_package_mapping_preserves_non_package_content(self):
        sync_engine(self.source, self.target)
        source = (
            self.source
            / "src/main/kotlin/com/stzb/server/game/battle/BattleEngine.kt"
        ).read_text(encoding="utf-8")
        target = (
            self.target
            / "src/main/kotlin/com/stzb/battle/core/BattleEngine.kt"
        ).read_text(encoding="utf-8")

        self.assertEqual(normalize_source_package(source), target)

    def test_check_rejects_manual_core_edit(self):
        sync_engine(self.source, self.target)
        path = self.target / "src/main/kotlin/com/stzb/battle/core/BattleEngine.kt"
        path.write_text(path.read_text(encoding="utf-8") + "// drift\n")

        with self.assertRaisesRegex(ValueError, "generated file drift"):
            sync_engine(self.source, self.target, check=True)

    def test_check_rejects_source_commit_drift(self):
        sync_engine(self.source, self.target)
        manifest_path = self.target / "SOURCE.json"
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        manifest["sourceCommit"] = "0" * 40
        manifest_path.write_text(json.dumps(manifest), encoding="utf-8")

        with self.assertRaisesRegex(ValueError, "source commit drift"):
            sync_engine(self.source, self.target, check=True)

    def test_equipment_adapter_adds_standalone_resource_path(self):
        source = "\n".join(
            [
                "package com.stzb.battle.core",
                "import com.stzb.battle.core.MemoryPackTable",
                "private val CONFIG_PATHS = listOf(",
                '    Path.of("assent/cfg"),',
                '    Path.of("server/assent/cfg"),',
                ")",
            ]
        )

        adapted, name = apply_standalone_adapter(
            "BattleEquipmentRepository.kt", source
        )

        self.assertEqual(name, "standalone-resource-path")
        self.assertNotIn(
            "import com.stzb.battle.core.MemoryPackTable", adapted
        )
        self.assertIn(
            'Path.of("src/main/resources/battle-config")', adapted
        )

    def test_same_package_import_adapter_removes_only_known_imports(self):
        source = "\n".join(
            [
                "package com.stzb.battle.core",
                "import com.stzb.battle.core.ClientTroopFeatureRepository",
                "import com.stzb.battle.core.ClientEquipmentSkillRepository",
                "import com.stzb.battle.core.ClientTroopTypeRepository",
                "data class KeepMe(val value: Int)",
            ]
        )

        adapted, name = apply_standalone_adapter(
            "BattleTeamBuilder.kt", source
        )

        self.assertEqual(
            name, "remove-redundant-same-package-imports"
        )
        self.assertNotIn("import com.stzb.battle.core.Client", adapted)
        self.assertIn("data class KeepMe", adapted)

    def test_report_store_adapter_removes_server_config_dependency(self):
        source = "\n".join(
            [
                "package com.stzb.battle.core",
                "import com.stzb.server.protocol.GameServerConfig",
                'put("attack_name", GameServerConfig.ROLE_NAME)',
                "wid = GameServerConfig.CITY_WID + 1,",
            ]
        )

        adapted, name = apply_standalone_adapter(
            "ClientBattleReportStore.kt", source
        )

        self.assertEqual(name, "remove-server-config-dependency")
        self.assertNotIn("com.stzb.server.protocol", adapted)
        self.assertIn('put("attack_name", "模拟攻方")', adapted)
        self.assertIn("wid = 10001,", adapted)

    def test_sync_includes_required_source_tests(self):
        manifest = sync_engine(self.source, self.target)
        included = {row["target"] for row in manifest["tests"]}
        required = {
            "src/test/kotlin/com/stzb/battle/core/BattleEngineTest.kt",
            "src/test/kotlin/com/stzb/battle/core/BattleFormationCalculatorTest.kt",
            "src/test/kotlin/com/stzb/battle/core/BattleDamageCalculatorTest.kt",
            "src/test/kotlin/com/stzb/battle/core/skill/CompleteSkillEngineIntegrationTest.kt",
            "src/test/kotlin/com/stzb/battle/core/skill/SkillRuleInterpreterTest.kt",
            "src/test/kotlin/com/stzb/battle/core/skill/SkillTimingTest.kt",
            "src/test/kotlin/com/stzb/battle/core/OfficialFullBattleReportDiffTest.kt",
            "src/test/kotlin/com/stzb/battle/core/OfficialPreparationReportDiffTest.kt",
        }

        self.assertTrue(required <= included)

    def test_source_tests_are_included_or_excluded_with_a_reason(self):
        manifest = sync_engine(self.source, self.target)
        included = {row["source"] for row in manifest["tests"]}
        excluded = {
            row["source"]: row["reason"]
            for row in manifest["excludedTests"]
        }
        source_tests = {
            str(path.relative_to(self.source))
            for path in (
                self.source
                / "src/test/kotlin/com/stzb/server/game/battle"
            ).rglob("*.kt")
        }

        self.assertEqual(source_tests, included | set(excluded))
        self.assertTrue(all(reason.strip() for reason in excluded.values()))

    def test_sync_copies_only_required_official_report_fixtures(self):
        manifest = sync_engine(self.source, self.target)
        resources = {row["target"] for row in manifest["resources"]}

        self.assertIn(
            "src/test/resources/assent/cfg/paper.zip",
            resources,
        )
        self.assertIn(
            "src/test/resources/assent/cfg/paper/11/report.json",
            resources,
        )
        self.assertIn(
            "src/test/resources/assent/cfg/paper/6231/report.json",
            resources,
        )

    def test_sync_copies_independent_skill_condition_fixture(self):
        manifest = sync_engine(self.source, self.target)
        resources = {row["target"] for row in manifest["resources"]}

        self.assertIn(
            "src/test/resources/skill-condition-plugin-owners.csv",
            resources,
        )

    def test_manifest_records_known_source_baseline_failures(self):
        manifest = sync_engine(self.source, self.target)
        failures = manifest["knownSourceTestFailures"]

        self.assertEqual(9, len(failures))
        self.assertTrue(
            all(
                row["sourceClass"].startswith(
                    "com.stzb.server.game.battle."
                )
                for row in failures
            )
        )
        self.assertTrue(
            all(
                row["targetClass"].startswith(
                    "com.stzb.battle.core."
                )
                for row in failures
            )
        )
        self.assertTrue(all(row["method"].strip() for row in failures))
        self.assertTrue(all(row["reason"].strip() for row in failures))

    def test_official_fixture_paths_are_adapted_to_test_resources(self):
        source = "\n".join(
            [
                "package com.stzb.battle.core",
                "import java.nio.file.Path",
                'val report = Path.of("assent/cfg/paper/11/report.json")',
            ]
        )

        adapted, name = apply_standalone_adapter(
            "OfficialReportFixtureTest.kt",
            source,
        )

        self.assertEqual(name, "standalone-test-fixture-path")
        self.assertIn(
            'Path.of("src/test/resources/assent/cfg/paper/11/report.json")',
            adapted,
        )


if __name__ == "__main__":
    unittest.main()
