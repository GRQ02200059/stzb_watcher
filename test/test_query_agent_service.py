import sqlite3
import unittest
from pathlib import Path

from intelligence.config_repository import IntelligenceConfigRepository
from query_agent.service import QueryAgentService
from query_agent.tools import QueryTools
from test.test_query_agent_tools import FakeResearchRepository

ROOT = Path(__file__).resolve().parents[1]

class QueryAgentServiceTest(unittest.TestCase):
    def setUp(self):
        self.conn = sqlite3.connect(":memory:")
        self.conn.row_factory = sqlite3.Row
        self.conn.executescript(
            """
            CREATE TABLE world_tiles(
                wid INTEGER PRIMARY KEY,
                row INTEGER,
                col INTEGER,
                name TEXT,
                city_type INTEGER,
                source_seq INTEGER
            );
            INSERT INTO world_tiles VALUES(10004,1,4,'土地名',1,7);
            CREATE TABLE world_armies(
                army_id INTEGER PRIMARY KEY,
                user_id INTEGER,
                wid_from INTEGER,
                wid_to INTEGER,
                end_time INTEGER,
                reside_wid INTEGER,
                stay_wid INTEGER,
                deleted_at_seq INTEGER
            );
            INSERT INTO world_armies VALUES(1001,42,10001,10004,9,0,0,NULL);
            CREATE TABLE battles_v2(
                battle_id INTEGER PRIMARY KEY,
                time INTEGER,
                atk_name TEXT,
                def_name TEXT,
                wid INTEGER,
                result INTEGER,
                atk_gongxun INTEGER
            );
            INSERT INTO battles_v2 VALUES(77,1700000000,'张三','李四',10004,1,1234);
            CREATE TABLE team_users(
                uid INTEGER PRIMARY KEY,
                name TEXT,
                group_name TEXT,
                power INTEGER,
                wuxun INTEGER
            );
            INSERT INTO team_users VALUES(42,'张三','一团',50000,6000);
            """
        )
        self.service = QueryAgentService(QueryTools(lambda: self.conn))

    def test_answers_wid_query_with_navigation(self):
        response = self.service.answer("查 10004")
        body = response.to_json()
        self.assertTrue(body["ok"])
        self.assertIn("10004", body["answer"])
        self.assertEqual(body["uiActions"][0]["route"], "map")

    def test_uses_llm_client_for_read_only_answers(self):
        class FakeLlmClient:
            def __init__(self):
                self.calls = []
                self.model_name = "fake-model"

            def answer(self, context):
                self.calls.append(context)
                return "LLM 生成：10004 是当前地图查询目标。"

        llm = FakeLlmClient()
        service = QueryAgentService(QueryTools(lambda: self.conn), llm_client=llm)

        response = service.answer("查 10004")
        body = response.to_json()

        self.assertEqual(body["answer"], "LLM 生成：10004 是当前地图查询目标。")
        self.assertTrue(body["llmUsed"])
        self.assertEqual(body["llmModel"], "fake-model")
        self.assertEqual(body["uiActions"][0]["route"], "map")
        self.assertEqual(llm.calls[0]["message"], "查 10004")
        self.assertIn("WID 10004", llm.calls[0]["draftAnswer"])
        self.assertEqual(llm.calls[0]["evidence"][0]["source"], "world_tiles")

    def test_rejects_execution_request(self):
        response = self.service.answer("派主力出征 10004")
        body = response.to_json()
        self.assertFalse(body["ok"])
        self.assertIn("只读", body["error"])

    def test_answers_hero_and_skill_queries_with_research_navigation(self):
        tools = QueryTools(
            lambda: self.conn,
            config_repository=IntelligenceConfigRepository(
                ROOT / "data/intelligence/client-9.2.2"
            ),
        )
        service = QueryAgentService(tools)
        hero = service.answer("查询武将张辽").to_json()
        self.assertIn("张辽", hero["answer"])
        self.assertEqual(hero["uiActions"][0]["route"], "intelligence-research")
        skill = service.answer("查询战法其疾如风").to_json()
        self.assertIn("其疾如风", skill["answer"])
        self.assertEqual(skill["evidence"][0]["source"], "client-9.2.2")

    def test_answers_lineup_and_risk_with_typed_evidence(self):
        class FakeLineupService:
            def get_lineup(self, key):
                return {
                    "key": key,
                    "battleStats": {"sampleSize": 18, "winRate": 61.1},
                    "confidence": {"label": "medium"},
                }

        class FakeWorldService:
            def risk_for_tile(self, wid):
                return {
                    "wid": wid,
                    "score": 72,
                    "level": "high",
                    "confidence": 0.71,
                    "freshness": "fresh",
                    "unknownComponents": ["estimatedTroops"],
                }

            def summary(self):
                return {"worldStateVersion": 7, "freshness": "fresh"}

        service = QueryAgentService(
            QueryTools(
                lambda: self.conn,
                lineup_service=FakeLineupService(),
                world_service_factory=lambda: FakeWorldService(),
            )
        )
        lineup = service.answer("查询阵容 101.102.103").to_json()
        self.assertIn("18 场", lineup["answer"])
        self.assertEqual(lineup["evidence"][0]["source"], "battles_v2")
        self.assertEqual(lineup["uiActions"][0]["params"]["lineupKey"], "101.102.103")

        risk = service.answer("解释风险 10004").to_json()
        self.assertIn("72", risk["answer"])
        self.assertIn("v7", risk["answer"])
        self.assertEqual(risk["evidence"][0]["source"], "world_state_v7")

    def test_answers_card_pack_and_hero_pack_queries(self):
        config = IntelligenceConfigRepository(
            ROOT / "data/intelligence/client-9.2.2"
        )
        service = QueryAgentService(
            QueryTools(
                lambda: self.conn,
                config_repository=config,
                research_repository=FakeResearchRepository(),
            )
        )

        pack = service.answer("查询卡包 802").to_json()
        self.assertIn("802", pack["answer"])
        self.assertEqual(pack["uiActions"][0]["params"]["packId"], 802)

        reverse = service.answer("张辽在哪些卡包").to_json()
        self.assertIn("802", reverse["answer"])
        self.assertEqual(reverse["evidence"][0]["entityType"], "card-pack")

        command = service.answer("查询命令 5028").to_json()
        self.assertTrue(command["needsClarification"])
        self.assertEqual(command["uiActions"], [])

        schema = service.answer("查询字段 Tb_world_city").to_json()
        self.assertTrue(schema["needsClarification"])
        self.assertEqual(schema["uiActions"], [])

    def test_package_exports_service(self):
        from query_agent import QueryAgentService as ExportedService

        self.assertIs(ExportedService, QueryAgentService)


if __name__ == "__main__":
    unittest.main()
