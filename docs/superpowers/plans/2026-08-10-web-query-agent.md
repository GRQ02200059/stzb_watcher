# Web Query Agent Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a read-only natural-language Query Agent for map, march, battle, alliance, and monitoring data.

**Architecture:** Add a `query_agent/` Python package with intent routing, context allowlisting, read-only tools, and a service that returns an answer, citations, and frontend UI actions. Register `/api/query-agent/messages` in Flask and add a minimal dashboard command panel that can submit questions and apply navigation actions.

**Tech Stack:** Python 3, Flask, SQLite, `unittest`, existing static dashboard JavaScript.

## Global Constraints

- Query Agent is read-only.
- No packet sending, automation, game action execution, or database writes.
- The model or fallback router must not receive raw packets, SQL, logs, shell commands, file paths, tokens, or internal configs.
- Every answer must include evidence and freshness if data exists.
- UI actions may only change frontend route/filter state.
- Prefer the new world-scene read APIs from `world_scene`; if absent, use compatibility views and mark `dataCompleteness = "legacy"`.

---

## File Structure

- Create `query_agent/__init__.py`: package exports.
- Create `query_agent/models.py`: request, response, evidence, UI action dataclasses.
- Create `query_agent/context.py`: allowlisted page/context projection.
- Create `query_agent/tools.py`: read-only query tools.
- Create `query_agent/service.py`: router and response composer.
- Create `query_agent/api.py`: Flask registration.
- Modify `api_server.py`: register Query Agent API.
- Modify `static/dashboard.html`: add command panel shell.
- Modify `static/app1.js`: submit question and dispatch UI actions.
- Test `test/test_query_agent_context.py`
- Test `test/test_query_agent_tools.py`
- Test `test/test_query_agent_service.py`
- Test `test/test_query_agent_api.py`

---

### Task 1: Models and Context Allowlist

**Files:**
- Create: `query_agent/__init__.py`
- Create: `query_agent/models.py`
- Create: `query_agent/context.py`
- Test: `test/test_query_agent_context.py`

**Interfaces:**
- Produces: `build_query_context(message: str, page_context: dict | None) -> dict`
- Produces dataclasses: `Evidence`, `UiAction`, `QueryAgentResponse`

- [ ] **Step 1: Write failing context tests**

```python
# test/test_query_agent_context.py
import unittest

from query_agent.context import build_query_context


class QueryAgentContextTest(unittest.TestCase):
    def test_rejects_forbidden_context_keys(self):
        with self.assertRaises(ValueError):
            build_query_context("查这个队伍", {"sql": "select * from x"})

    def test_keeps_allowlisted_page_context(self):
        ctx = build_query_context("查 10004", {
            "page": "map",
            "selectedWid": 10004,
            "selectedArmyId": 1001,
            "rawPacket": "[...]",
        })
        self.assertEqual(ctx["message"], "查 10004")
        self.assertEqual(ctx["pageContext"]["selectedWid"], 10004)
        self.assertNotIn("rawPacket", ctx["pageContext"])


if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 2: Run test to verify failure**

Run: `python -m unittest test.test_query_agent_context -v`

Expected: FAIL with `ModuleNotFoundError: No module named 'query_agent'`.

- [ ] **Step 3: Implement models and allowlist**

```python
# query_agent/models.py
from dataclasses import asdict, dataclass, field
from typing import Any


@dataclass(frozen=True)
class Evidence:
    source: str
    label: str
    entity_type: str
    entity_id: str
    freshness: str = "unknown"


@dataclass(frozen=True)
class UiAction:
    type: str
    route: str
    params: dict[str, Any] = field(default_factory=dict)


@dataclass(frozen=True)
class QueryAgentResponse:
    ok: bool
    answer: str
    evidence: list[Evidence] = field(default_factory=list)
    ui_actions: list[UiAction] = field(default_factory=list)
    needs_clarification: bool = False
    error: str = ""
    data_completeness: str = "complete"

    def to_json(self) -> dict[str, Any]:
        return {
            "ok": self.ok,
            "answer": self.answer,
            "evidence": [asdict(item) for item in self.evidence],
            "uiActions": [asdict(item) for item in self.ui_actions],
            "needsClarification": self.needs_clarification,
            "error": self.error,
            "dataCompleteness": self.data_completeness,
        }
```

```python
# query_agent/context.py
FORBIDDEN_KEYS = {
    "rawPacket", "rawPackets", "payload", "cmd", "sql", "shell", "logs",
    "filePath", "dbPath", "token", "code", "trace",
}
ALLOWED_PAGE_KEYS = {
    "page", "selectedWid", "selectedArmyId", "selectedBattleId",
    "selectedUserId", "query", "timeRange", "filters",
}


def build_query_context(message: str, page_context: dict | None) -> dict:
    if not isinstance(message, str) or not message.strip():
        raise ValueError("message is required")
    clean = {}
    for key, value in (page_context or {}).items():
        if key in FORBIDDEN_KEYS:
            raise ValueError(f"context key is forbidden: {key}")
        if key in ALLOWED_PAGE_KEYS:
            clean[key] = value
    return {"message": message.strip(), "pageContext": clean}
```

```python
# query_agent/__init__.py
from .service import QueryAgentService

__all__ = ["QueryAgentService"]
```

- [ ] **Step 4: Run context tests**

Run: `python -m unittest test.test_query_agent_context -v`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add query_agent/__init__.py query_agent/models.py query_agent/context.py test/test_query_agent_context.py
git commit -m "feat: add query agent context model"
```

---

### Task 2: Read-Only Tools

**Files:**
- Create: `query_agent/tools.py`
- Test: `test/test_query_agent_tools.py`

**Interfaces:**
- Produces: `QueryTools.tile(wid: int) -> dict`
- Produces: `QueryTools.armies(army_id: int | None = None, wid: int | None = None) -> list[dict]`
- Produces: `QueryTools.battle_search(query: str = "", wid: int | None = None, limit: int = 5) -> list[dict]`
- Produces: `QueryTools.alliance_member(query: str, limit: int = 5) -> list[dict]`

- [ ] **Step 1: Write failing tool tests**

```python
# test/test_query_agent_tools.py
import sqlite3
import unittest

from query_agent.tools import QueryTools


class QueryAgentToolsTest(unittest.TestCase):
    def setUp(self):
        self.conn = sqlite3.connect(":memory:")
        self.conn.row_factory = sqlite3.Row
        self.conn.executescript("""
            CREATE TABLE world_tiles(wid INTEGER PRIMARY KEY,row INTEGER,col INTEGER,name TEXT,city_type INTEGER,source_seq INTEGER);
            INSERT INTO world_tiles VALUES(10004,1,4,'土地名',1,7);
            CREATE TABLE world_armies(army_id INTEGER PRIMARY KEY,user_id INTEGER,wid_from INTEGER,wid_to INTEGER,end_time INTEGER,deleted_at_seq INTEGER);
            INSERT INTO world_armies VALUES(1001,42,10001,10004,9,NULL);
            CREATE TABLE battles_v2(battle_id INTEGER PRIMARY KEY,time INTEGER,atk_name TEXT,def_name TEXT,wid INTEGER,result INTEGER,atk_gongxun INTEGER);
            INSERT INTO battles_v2 VALUES(77,1700000000,'张三','李四',10004,1,1234);
            CREATE TABLE team_users(uid INTEGER PRIMARY KEY,name TEXT,group_name TEXT,power INTEGER,wuxun INTEGER);
            INSERT INTO team_users VALUES(42,'张三','一团',50000,6000);
        """)

    def test_tile_and_army_queries(self):
        tools = QueryTools(lambda: self.conn)
        self.assertEqual(tools.tile(10004)["name"], "土地名")
        self.assertEqual(tools.armies(army_id=1001)[0]["army_id"], 1001)

    def test_battle_and_member_queries(self):
        tools = QueryTools(lambda: self.conn)
        self.assertEqual(tools.battle_search(query="张三")[0]["battle_id"], 77)
        self.assertEqual(tools.alliance_member("张三")[0]["uid"], 42)


if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 2: Run test to verify failure**

Run: `python -m unittest test.test_query_agent_tools -v`

Expected: FAIL with missing `query_agent.tools`.

- [ ] **Step 3: Implement read-only tools**

```python
# query_agent/tools.py
import sqlite3
from typing import Callable


class QueryTools:
    def __init__(self, get_connection: Callable[[], sqlite3.Connection]) -> None:
        self.get_connection = get_connection

    def tile(self, wid: int) -> dict:
        conn = self.get_connection()
        row = conn.execute("SELECT * FROM world_tiles WHERE wid=?", (int(wid),)).fetchone()
        if row is None:
            row = conn.execute("SELECT * FROM map_cells WHERE wid=?", (int(wid),)).fetchone()
        return dict(row) if row is not None else {}

    def armies(self, army_id: int | None = None, wid: int | None = None) -> list[dict]:
        conn = self.get_connection()
        where = ["deleted_at_seq IS NULL"]
        args = []
        if army_id is not None:
            where.append("army_id=?")
            args.append(int(army_id))
        if wid is not None:
            where.append("(wid_from=? OR wid_to=? OR reside_wid=? OR stay_wid=?)")
            args.extend([int(wid)] * 4)
        try:
            rows = conn.execute(f"SELECT * FROM world_armies WHERE {' AND '.join(where)} ORDER BY end_time, army_id LIMIT 50", args).fetchall()
        except sqlite3.OperationalError:
            rows = conn.execute("SELECT * FROM battle_monitor_moves ORDER BY arrive_time DESC LIMIT 50").fetchall()
        return [dict(row) for row in rows]

    def battle_search(self, query: str = "", wid: int | None = None, limit: int = 5) -> list[dict]:
        conn = self.get_connection()
        where = ["1=1"]
        args = []
        if query:
            where.append("(atk_name LIKE ? OR def_name LIKE ?)")
            args.extend([f"%{query}%", f"%{query}%"])
        if wid is not None:
            where.append("wid=?")
            args.append(int(wid))
        args.append(int(limit))
        rows = conn.execute(
            f"SELECT battle_id,time,atk_name,def_name,wid,result,atk_gongxun FROM battles_v2 WHERE {' AND '.join(where)} ORDER BY time DESC LIMIT ?",
            args,
        ).fetchall()
        return [dict(row) for row in rows]

    def alliance_member(self, query: str, limit: int = 5) -> list[dict]:
        conn = self.get_connection()
        rows = conn.execute(
            "SELECT uid,name,group_name,power,wuxun FROM team_users WHERE name LIKE ? ORDER BY power DESC LIMIT ?",
            (f"%{query}%", int(limit)),
        ).fetchall()
        return [dict(row) for row in rows]
```

- [ ] **Step 4: Run tool tests**

Run: `python -m unittest test.test_query_agent_tools -v`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add query_agent/tools.py test/test_query_agent_tools.py
git commit -m "feat: add read-only query tools"
```

---

### Task 3: Query Agent Service

**Files:**
- Create: `query_agent/service.py`
- Test: `test/test_query_agent_service.py`

**Interfaces:**
- Consumes: `QueryTools`
- Produces: `QueryAgentService.answer(message: str, page_context: dict | None = None) -> QueryAgentResponse`

- [ ] **Step 1: Write failing service tests**

```python
# test/test_query_agent_service.py
import sqlite3
import unittest

from query_agent.service import QueryAgentService
from query_agent.tools import QueryTools


class QueryAgentServiceTest(unittest.TestCase):
    def setUp(self):
        self.conn = sqlite3.connect(":memory:")
        self.conn.row_factory = sqlite3.Row
        self.conn.executescript("""
            CREATE TABLE world_tiles(wid INTEGER PRIMARY KEY,row INTEGER,col INTEGER,name TEXT,city_type INTEGER,source_seq INTEGER);
            INSERT INTO world_tiles VALUES(10004,1,4,'土地名',1,7);
            CREATE TABLE world_armies(army_id INTEGER PRIMARY KEY,user_id INTEGER,wid_from INTEGER,wid_to INTEGER,end_time INTEGER,deleted_at_seq INTEGER);
            INSERT INTO world_armies VALUES(1001,42,10001,10004,9,NULL);
            CREATE TABLE battles_v2(battle_id INTEGER PRIMARY KEY,time INTEGER,atk_name TEXT,def_name TEXT,wid INTEGER,result INTEGER,atk_gongxun INTEGER);
            INSERT INTO battles_v2 VALUES(77,1700000000,'张三','李四',10004,1,1234);
            CREATE TABLE team_users(uid INTEGER PRIMARY KEY,name TEXT,group_name TEXT,power INTEGER,wuxun INTEGER);
            INSERT INTO team_users VALUES(42,'张三','一团',50000,6000);
        """)
        self.service = QueryAgentService(QueryTools(lambda: self.conn))

    def test_answers_wid_query_with_navigation(self):
        response = self.service.answer("查 10004")
        body = response.to_json()
        self.assertTrue(body["ok"])
        self.assertIn("10004", body["answer"])
        self.assertEqual(body["uiActions"][0]["route"], "map")

    def test_rejects_execution_request(self):
        response = self.service.answer("派主力出征 10004")
        body = response.to_json()
        self.assertFalse(body["ok"])
        self.assertIn("只读", body["error"])


if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 2: Run test to verify failure**

Run: `python -m unittest test.test_query_agent_service -v`

Expected: FAIL with missing service.

- [ ] **Step 3: Implement deterministic service**

```python
# query_agent/service.py
import re

from .context import build_query_context
from .models import Evidence, QueryAgentResponse, UiAction
from .tools import QueryTools


EXECUTION_WORDS = ("出征", "召回", "发包", "自动", "建设", "屯田", "练兵", "领取")


class QueryAgentService:
    def __init__(self, tools: QueryTools) -> None:
        self.tools = tools

    def answer(self, message: str, page_context: dict | None = None) -> QueryAgentResponse:
        build_query_context(message, page_context)
        text = message.strip()
        if any(word in text for word in EXECUTION_WORDS):
            return QueryAgentResponse(False, "", error="当前 Agent 入口只读，不能执行游戏动作、发包或自动化任务。")
        army_match = re.search(r"(?:队伍|army|Army)?\s*(\d{4,})", text)
        if "队伍" in text and army_match:
            army_id = int(army_match.group(1))
            rows = self.tools.armies(army_id=army_id)
            if not rows:
                return QueryAgentResponse(True, f"没有查到队伍 {army_id} 的当前行军状态。", data_completeness="legacy")
            row = rows[0]
            return QueryAgentResponse(
                True,
                f"队伍 {army_id} 当前目标 WID 是 {row.get('wid_to') or row.get('to_wid')}，预计到达时间字段为 {row.get('end_time') or row.get('arrive_time')}。",
                evidence=[Evidence("world_armies", "当前队伍状态", "army", str(army_id), "current")],
                ui_actions=[UiAction("open", "battlefield-monitor", {"armyId": army_id})],
            )
        wid_match = re.search(r"\b(\d{5,})\b", text)
        if wid_match:
            wid = int(wid_match.group(1))
            tile = self.tools.tile(wid)
            armies = self.tools.armies(wid=wid)
            battles = self.tools.battle_search(wid=wid, limit=3)
            parts = [f"WID {wid}"]
            if tile:
                parts.append(f"地块名：{tile.get('name') or tile.get('city_name') or '未命名'}")
            parts.append(f"关联行军 {len(armies)} 条，关联战报 {len(battles)} 条。")
            evidence = [Evidence("world_tiles" if tile else "map_cells", "地块查询", "wid", str(wid), "current")]
            return QueryAgentResponse(
                True,
                "；".join(parts),
                evidence=evidence,
                ui_actions=[UiAction("open", "map", {"wid": wid}), UiAction("filter", "battles", {"wid": wid})],
                data_completeness="complete" if tile else "legacy",
            )
        member_rows = self.tools.alliance_member(text, limit=3)
        if member_rows:
            member = member_rows[0]
            return QueryAgentResponse(
                True,
                f"找到成员 {member['name']}，分组 {member.get('group_name') or '未分组'}，势力 {member.get('power') or 0}。",
                evidence=[Evidence("team_users", "同盟成员", "user", str(member["uid"]), "current")],
                ui_actions=[UiAction("open", "alliance-members", {"uid": member["uid"]})],
            )
        return QueryAgentResponse(True, "没有找到明确实体。请提供 WID、队伍 ID、玩家名或战报 ID。", needs_clarification=True)
```

- [ ] **Step 4: Run service tests**

Run: `python -m unittest test.test_query_agent_service -v`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add query_agent/service.py test/test_query_agent_service.py
git commit -m "feat: answer read-only query agent requests"
```

---

### Task 4: Flask API

**Files:**
- Create: `query_agent/api.py`
- Modify: `api_server.py`
- Test: `test/test_query_agent_api.py`

**Interfaces:**
- Produces: `register_query_agent_api(app, get_connection) -> None`
- Produces: `POST /api/query-agent/messages`

- [ ] **Step 1: Write failing API test**

```python
# test/test_query_agent_api.py
import sqlite3
import unittest
from flask import Flask

from query_agent.api import register_query_agent_api


class QueryAgentApiTest(unittest.TestCase):
    def setUp(self):
        self.conn = sqlite3.connect(":memory:")
        self.conn.row_factory = sqlite3.Row
        self.conn.executescript("""
            CREATE TABLE world_tiles(wid INTEGER PRIMARY KEY,row INTEGER,col INTEGER,name TEXT,city_type INTEGER,source_seq INTEGER);
            INSERT INTO world_tiles VALUES(10004,1,4,'土地名',1,7);
            CREATE TABLE world_armies(army_id INTEGER PRIMARY KEY,user_id INTEGER,wid_from INTEGER,wid_to INTEGER,end_time INTEGER,deleted_at_seq INTEGER);
            CREATE TABLE battles_v2(battle_id INTEGER PRIMARY KEY,time INTEGER,atk_name TEXT,def_name TEXT,wid INTEGER,result INTEGER,atk_gongxun INTEGER);
            CREATE TABLE team_users(uid INTEGER PRIMARY KEY,name TEXT,group_name TEXT,power INTEGER,wuxun INTEGER);
        """)
        app = Flask(__name__)
        register_query_agent_api(app, lambda: self.conn)
        self.client = app.test_client()

    def test_post_message(self):
        response = self.client.post("/api/query-agent/messages", json={"message": "查 10004"})
        self.assertEqual(response.status_code, 200)
        self.assertTrue(response.get_json()["ok"])


if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 2: Run test to verify failure**

Run: `python -m unittest test.test_query_agent_api -v`

Expected: FAIL with missing API module.

- [ ] **Step 3: Implement API**

```python
# query_agent/api.py
from flask import jsonify, request

from .service import QueryAgentService
from .tools import QueryTools


def register_query_agent_api(app, get_connection):
    @app.route("/api/query-agent/messages", methods=["POST"])
    def api_query_agent_messages():
        body = request.get_json(silent=True) or {}
        message = body.get("message", "")
        page_context = body.get("pageContext") or {}
        if not isinstance(message, str) or not message.strip():
            return jsonify({"ok": False, "error": "message is required"}), 400
        try:
            service = QueryAgentService(QueryTools(get_connection))
            return jsonify(service.answer(message, page_context).to_json())
        except ValueError as error:
            return jsonify({"ok": False, "error": str(error)}), 400
```

- [ ] **Step 4: Register in `api_server.py`**

Add import:

```python
from query_agent.api import register_query_agent_api
```

After app creation:

```python
register_query_agent_api(app, get_db)
```

- [ ] **Step 5: Run API test**

Run: `python -m unittest test.test_query_agent_api -v`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add query_agent/api.py api_server.py test/test_query_agent_api.py
git commit -m "feat: expose query agent API"
```

---

### Task 5: Static Dashboard Panel

**Files:**
- Modify: `static/dashboard.html`
- Modify: `static/app1.js`
- Test: manual smoke

**Interfaces:**
- Consumes: `POST /api/query-agent/messages`
- Produces: global command panel with answers, evidence, and UI actions.

- [ ] **Step 1: Add panel markup**

In `static/dashboard.html`, add this near the end of `<body>` before scripts:

```html
<aside id="query-agent-panel" class="query-agent-panel" style="display:none">
  <div class="query-agent-head">
    <strong>战术检索</strong>
    <button class="btn" type="button" onclick="toggleQueryAgent(false)">关闭</button>
  </div>
  <div id="query-agent-answer" class="query-agent-answer">输入 WID、队伍 ID、玩家或战报问题。</div>
  <textarea id="query-agent-input" rows="3" placeholder="例如：查 10004 周围有什么行军"></textarea>
  <button class="btn btn-primary" type="button" onclick="sendQueryAgentMessage()">查询</button>
</aside>
<button id="query-agent-fab" class="btn btn-primary" type="button" onclick="toggleQueryAgent(true)">战术检索</button>
```

Add CSS:

```css
.query-agent-panel{position:fixed;right:16px;top:72px;width:380px;max-width:calc(100vw - 32px);z-index:900;background:var(--panel);border:1px solid var(--border);border-radius:12px;padding:14px;box-shadow:0 18px 60px #0008}
.query-agent-head{display:flex;align-items:center;justify-content:space-between;margin-bottom:10px}
.query-agent-answer{min-height:120px;max-height:360px;overflow:auto;background:#07111d;border:1px solid #162638;border-radius:8px;padding:10px;color:var(--text);font-size:.82rem;line-height:1.55;margin-bottom:10px}
#query-agent-input{width:100%;box-sizing:border-box;background:var(--panel2);color:var(--text);border:1px solid var(--border);border-radius:8px;padding:8px}
#query-agent-fab{position:fixed;right:18px;bottom:18px;z-index:850}
```

- [ ] **Step 2: Add JavaScript**

In `static/app1.js`, add:

```javascript
function toggleQueryAgent(open){
  const panel=document.getElementById('query-agent-panel');
  if(panel) panel.style.display=open?'block':'none';
}

function currentQueryAgentContext(){
  const active=document.querySelector('.page.active');
  const route=active?.id||'';
  return {
    page: route,
    query: document.querySelector('.page.active input')?.value || '',
    filters: {}
  };
}

function renderQueryAgentResponse(data){
  const box=document.getElementById('query-agent-answer');
  if(!box) return;
  if(!data || !data.ok){
    box.innerHTML=`<div style="color:var(--red)">查询失败：${esc(data?.error||'未知错误')}</div>`;
    return;
  }
  const evidence=(data.evidence||[]).map(e=>`<li>${esc(e.label||e.source)} · ${esc(e.entityType||'')} ${esc(e.entityId||'')} · ${esc(e.freshness||'')}</li>`).join('');
  const actions=(data.uiActions||[]).map((a,i)=>`<button class="btn" onclick='applyQueryAgentAction(${JSON.stringify(a).replace(/'/g,"&#39;")})'>执行动作 ${i+1}</button>`).join(' ');
  box.innerHTML=`<div>${esc(data.answer||'')}</div>${evidence?`<ul>${evidence}</ul>`:''}<div style="margin-top:8px">${actions}</div>`;
}

async function sendQueryAgentMessage(){
  const input=document.getElementById('query-agent-input');
  const message=(input?.value||'').trim();
  if(!message){showToast('请输入查询内容','var(--gold)');return;}
  const data=await apiFetch('/api/query-agent/messages',{
    method:'POST',
    headers:{'Content-Type':'application/json'},
    body:JSON.stringify({message,pageContext:currentQueryAgentContext()})
  });
  renderQueryAgentResponse(data);
}

function applyQueryAgentAction(action){
  if(!action) return;
  if(action.route==='map'){
    const btn=[...document.querySelectorAll('nav button')].find(b=>String(b.onclick).includes('switchTab(12,'));
    if(btn) switchTab(12,btn);
    const input=document.getElementById('map-filter');
    if(input && action.params?.wid){input.value=String(action.params.wid);filterMapCities();}
  } else if(action.route==='battles'){
    const btn=[...document.querySelectorAll('nav button')].find(b=>String(b.onclick).includes('switchTab(10,'));
    if(btn) switchTab(10,btn);
    const wid=document.getElementById('ba-wid');
    if(wid && action.params?.wid){wid.value=String(action.params.wid);loadBattlesAll(1);}
  } else if(action.route==='battlefield-monitor'){
    const btn=[...document.querySelectorAll('nav button')].find(b=>String(b.onclick).includes('switchTab(27,'));
    if(btn) switchTab(27,btn);
  } else if(action.route==='alliance-members'){
    const btn=[...document.querySelectorAll('nav button')].find(b=>String(b.onclick).includes('switchTab(14,'));
    if(btn) switchTab(14,btn);
  }
}
```

- [ ] **Step 3: Manual smoke**

Run: `python api_server.py`

Expected:
- Dashboard loads.
- Click “战术检索”.
- Query `查 10004`.
- Panel displays answer and evidence.
- UI action opens map/battle page without console errors.

- [ ] **Step 4: Commit**

```bash
git add static/dashboard.html static/app1.js
git commit -m "feat: add query agent panel"
```

---

## Plan Self-Review

Spec coverage:
- Read-only Agent: Tasks 1-4.
- Query tools: Task 2.
- Evidence and UI actions: Tasks 3 and 5.
- Reject write/action requests: Task 3.
- Frontend entry: Task 5.
- No direct SQL/model exposure: Task 1 allowlist and Task 2 tool boundary.

No placeholders remain. Function names and response fields are consistent across tasks.
