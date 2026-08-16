import re
from dataclasses import asdict

from .context import build_query_context
from .models import Evidence, QueryAgentResponse, UiAction
from .tools import QueryTools


EXECUTION_WORDS = ("出征", "召回", "发包", "自动", "建设", "屯田", "练兵", "领取")


class QueryAgentService:
    def __init__(self, tools: QueryTools, llm_client=None) -> None:
        self.tools = tools
        self.llm_client = llm_client

    def answer(self, message: str, page_context=None) -> QueryAgentResponse:
        context = build_query_context(message, page_context)
        text = message.strip()
        if any(word in text for word in EXECUTION_WORDS):
            return QueryAgentResponse(
                False,
                "",
                error="当前 Agent 入口只读，不能执行游戏动作、发包或自动化任务。",
            )

        pack_match = re.search(r"^(?:查询卡包|查卡包|卡包)\s*(\d+)\s*$", text)
        if pack_match:
            pack_id = int(pack_match.group(1))
            pack = self.tools.card_pack(pack_id=pack_id)
            if pack:
                names = "、".join(
                    hero.get("name") or str(hero.get("heroid") or "")
                    for hero in (pack.get("heroes") or [])[:5]
                )
                return self._answer_with_llm(
                    context,
                    QueryAgentResponse(
                        True,
                        f"卡包 {pack_id} 收录 {pack.get('heroCount', 0)} 名武将"
                        f"{f'，包括 {names}' if names else ''}。"
                        "当前数据只证明卡包武将池，不代表抽取概率或保底。",
                        evidence=[
                            Evidence(
                                "client-9.2.2-research",
                                "客户端卡包武将池",
                                "card-pack",
                                str(pack_id),
                                "versioned",
                            )
                        ],
                        ui_actions=[
                            UiAction(
                                "open",
                                "intelligence-research",
                                {"packId": pack_id},
                            )
                        ],
                    ),
                )

        hero_pack_match = re.search(r"^(.+?)在哪些卡包[？?]?$", text)
        if hero_pack_match:
            hero_query = hero_pack_match.group(1).strip()
            heroes = self.tools.hero_search(hero_query, limit=1)
            if heroes:
                hero = heroes[0]
                packs = self.tools.hero_card_packs(hero["heroid"])
                if packs:
                    pack_ids = "、".join(str(row["packId"]) for row in packs)
                    return self._answer_with_llm(
                        context,
                        QueryAgentResponse(
                            True,
                            f"{hero['name']} 出现在 {len(packs)} 个配置卡包中：{pack_ids}。"
                            "这是客户端卡包收录关系，不代表抽取概率。",
                            evidence=[
                                Evidence(
                                    "client-9.2.2-research",
                                    "武将卡包反查",
                                    "card-pack",
                                    str(packs[0]["packId"]),
                                    "versioned",
                                )
                            ],
                            ui_actions=[
                                UiAction(
                                    "open",
                                    "intelligence-research",
                                    {"packId": packs[0]["packId"]},
                                )
                            ],
                        ),
                    )

        if text.startswith(("查询武将", "查武将", "武将")):
            query = re.sub(r"^(查询武将|查武将|武将)\s*", "", text)
            rows = self.tools.hero_search(query, limit=3)
            if rows:
                hero = rows[0]
                return self._answer_with_llm(
                    context,
                    QueryAgentResponse(
                        True,
                        f"找到武将 {hero['name']}（ID {hero['heroid']}），"
                        f"{hero.get('country_name') or hero.get('country') or '未知阵营'}，"
                        f"攻击距离 {hero.get('hit_range') or 0}。",
                        evidence=[
                            Evidence(
                                "client-9.2.2",
                                "客户端武将配置",
                                "hero",
                                str(hero["heroid"]),
                                "versioned",
                            )
                        ],
                        ui_actions=[
                            UiAction(
                                "open",
                                "intelligence-research",
                                {"heroId": hero["heroid"]},
                            )
                        ],
                    ),
                )

        if text.startswith(("查询战法", "查战法", "战法")):
            query = re.sub(r"^(查询战法|查战法|战法)\s*", "", text)
            rows = self.tools.skill_search(query, limit=3)
            if rows:
                skill = rows[0]
                return self._answer_with_llm(
                    context,
                    QueryAgentResponse(
                        True,
                        f"找到战法 {skill['name']}（ID {skill['skill_id']}），"
                        f"发动概率 {skill.get('probability_init') or 0}%，"
                        f"准备回合 {skill.get('prepare') or 0}。",
                        evidence=[
                            Evidence(
                                "client-9.2.2",
                                "客户端战法配置",
                                "skill",
                                str(skill["skill_id"]),
                                "versioned",
                            )
                        ],
                        ui_actions=[
                            UiAction(
                                "open",
                                "intelligence-research",
                                {"skillId": skill["skill_id"]},
                            )
                        ],
                    ),
                )

        lineup_match = re.search(
            r"(?:查询阵容|查阵容|阵容)\s*(\d+\.\d+\.\d+)",
            text,
        )
        if lineup_match:
            key = lineup_match.group(1)
            lineup = self.tools.lineup(key)
            if lineup:
                stats = lineup.get("battleStats") or {}
                confidence = lineup.get("confidence") or {}
                return self._answer_with_llm(
                    context,
                    QueryAgentResponse(
                        True,
                        f"阵容 {key} 有 {stats.get('sampleSize', 0)} 场历史样本，"
                        f"历史胜率 {stats.get('winRate', 0)}%，"
                        f"统计置信度 {confidence.get('label', 'unknown')}。"
                        "这是 BATTLE_STAT，不是模拟或确定性克制结论。",
                        evidence=[
                            Evidence(
                                "battles_v2",
                                f"BATTLE_STAT 样本 {stats.get('sampleSize', 0)}",
                                "lineup",
                                key,
                                "historical",
                            )
                        ],
                        ui_actions=[
                            UiAction(
                                "open",
                                "intelligence-research",
                                {"lineupKey": key},
                            )
                        ],
                    ),
                )

        risk_match = re.search(r"(?:解释风险|风险解释|风险)\s*(\d{5,})", text)
        if risk_match:
            wid = int(risk_match.group(1))
            risk = self.tools.explain_risk(wid)
            summary = self.tools.world_summary()
            if risk:
                version = int(summary.get("worldStateVersion") or 0)
                unknown = "、".join(risk.get("unknownComponents") or []) or "无"
                return self._answer_with_llm(
                    context,
                    QueryAgentResponse(
                        True,
                        f"WID {wid} 风险 {risk.get('score', 0)} 分"
                        f"（{risk.get('level', 'unknown')}），"
                        f"置信度 {round(float(risk.get('confidence', 0)) * 100)}%，"
                        f"WorldState v{version}，新鲜度 {risk.get('freshness', 'unknown')}；"
                        f"未知分量：{unknown}。",
                        evidence=[
                            Evidence(
                                f"world_state_v{version}",
                                "风险分量解释",
                                "wid",
                                str(wid),
                                risk.get("freshness", "unknown"),
                            )
                        ],
                        ui_actions=[
                            UiAction("open", "intelligence-map", {"wid": wid})
                        ],
                    ),
                )

        army_match = re.search(r"(?:队伍|army|Army)?\s*(\d{4,})", text)
        if "队伍" in text and army_match:
            return self._answer_with_llm(
                context, self._answer_army(int(army_match.group(1)))
            )

        wid_match = re.search(r"\b(\d{5,})\b", text)
        if wid_match:
            return self._answer_with_llm(
                context, self._answer_wid(int(wid_match.group(1)))
            )

        member_rows = self.tools.alliance_member(text, limit=3)
        if member_rows:
            member = member_rows[0]
            return self._answer_with_llm(
                context,
                QueryAgentResponse(
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
                ),
            )

        return self._answer_with_llm(
            context,
            QueryAgentResponse(
                True,
                "没有找到明确实体。请提供 WID、队伍 ID、玩家名或战报 ID。",
                needs_clarification=True,
            ),
        )

    def _answer_with_llm(
        self, context: dict, draft: QueryAgentResponse
    ) -> QueryAgentResponse:
        if self.llm_client is None or not draft.ok or draft.error:
            return draft
        llm_context = {
            "message": context["message"],
            "pageContext": context.get("pageContext", {}),
            "draftAnswer": draft.answer,
            "evidence": [asdict(item) for item in draft.evidence],
            "uiActions": [asdict(item) for item in draft.ui_actions],
            "needsClarification": draft.needs_clarification,
            "dataCompleteness": draft.data_completeness,
            "constraints": [
                "只读回答，不能执行游戏动作、发包、写数据库或启动自动化。",
                "只能使用已提供的白名单上下文、草稿答案和证据。",
                "如果证据不足，要求用户补充 WID、队伍 ID、玩家名或战报 ID。",
            ],
        }
        try:
            answer = (self.llm_client.answer(llm_context) or "").strip()
        except Exception as error:
            return QueryAgentResponse(
                draft.ok,
                draft.answer,
                evidence=draft.evidence,
                ui_actions=draft.ui_actions,
                needs_clarification=draft.needs_clarification,
                error=draft.error,
                data_completeness=draft.data_completeness,
                llm_error=str(error),
            )
        if not answer:
            return QueryAgentResponse(
                draft.ok,
                draft.answer,
                evidence=draft.evidence,
                ui_actions=draft.ui_actions,
                needs_clarification=draft.needs_clarification,
                error=draft.error,
                data_completeness=draft.data_completeness,
                llm_error="empty model response",
            )
        return QueryAgentResponse(
            draft.ok,
            answer,
            evidence=draft.evidence,
            ui_actions=draft.ui_actions,
            needs_clarification=draft.needs_clarification,
            error=draft.error,
            data_completeness=draft.data_completeness,
            llm_used=True,
            llm_model=getattr(self.llm_client, "model_name", ""),
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
