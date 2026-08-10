import re

from .context import build_query_context
from .models import Evidence, QueryAgentResponse, UiAction
from .tools import QueryTools


EXECUTION_WORDS = ("出征", "召回", "发包", "自动", "建设", "屯田", "练兵", "领取")


class QueryAgentService:
    def __init__(self, tools: QueryTools) -> None:
        self.tools = tools

    def answer(self, message: str, page_context=None) -> QueryAgentResponse:
        build_query_context(message, page_context)
        text = message.strip()
        if any(word in text for word in EXECUTION_WORDS):
            return QueryAgentResponse(
                False,
                "",
                error="当前 Agent 入口只读，不能执行游戏动作、发包或自动化任务。",
            )

        army_match = re.search(r"(?:队伍|army|Army)?\s*(\d{4,})", text)
        if "队伍" in text and army_match:
            return self._answer_army(int(army_match.group(1)))

        wid_match = re.search(r"\b(\d{5,})\b", text)
        if wid_match:
            return self._answer_wid(int(wid_match.group(1)))

        member_rows = self.tools.alliance_member(text, limit=3)
        if member_rows:
            member = member_rows[0]
            return QueryAgentResponse(
                True,
                f"找到成员 {member['name']}，分组 {member.get('group_name') or '未分组'}，势力 {member.get('power') or 0}。",
                evidence=[
                    Evidence(
                        "team_users",
                        "同盟成员",
                        "user",
                        str(member["uid"]),
                        "current",
                    )
                ],
                ui_actions=[
                    UiAction("open", "alliance-members", {"uid": member["uid"]})
                ],
            )

        return QueryAgentResponse(
            True,
            "没有找到明确实体。请提供 WID、队伍 ID、玩家名或战报 ID。",
            needs_clarification=True,
        )

    def _answer_army(self, army_id: int) -> QueryAgentResponse:
        rows = self.tools.armies(army_id=army_id)
        if not rows:
            return QueryAgentResponse(
                True,
                f"没有查到队伍 {army_id} 的当前行军状态。",
                data_completeness="legacy",
            )
        row = rows[0]
        target = row.get("wid_to") or row.get("to_wid")
        arrival = row.get("end_time") or row.get("arrive_time")
        return QueryAgentResponse(
            True,
            f"队伍 {army_id} 当前目标 WID 是 {target}，预计到达时间字段为 {arrival}。",
            evidence=[
                Evidence("world_armies", "当前队伍状态", "army", str(army_id), "current")
            ],
            ui_actions=[
                UiAction("open", "battlefield-monitor", {"armyId": army_id})
            ],
        )

    def _answer_wid(self, wid: int) -> QueryAgentResponse:
        tile = self.tools.tile(wid)
        armies = self.tools.armies(wid=wid)
        battles = self.tools.battle_search(wid=wid, limit=3)
        parts = [f"WID {wid}"]
        if tile:
            parts.append(f"地块名：{tile.get('name') or tile.get('city_name') or '未命名'}")
        parts.append(f"关联行军 {len(armies)} 条，关联战报 {len(battles)} 条。")
        return QueryAgentResponse(
            True,
            "；".join(parts),
            evidence=[
                Evidence(
                    "world_tiles" if tile else "map_cells",
                    "地块查询",
                    "wid",
                    str(wid),
                    "current",
                )
            ],
            ui_actions=[
                UiAction("open", "map", {"wid": wid}),
                UiAction("filter", "battles", {"wid": wid}),
            ],
            data_completeness="complete" if tile else "legacy",
        )
