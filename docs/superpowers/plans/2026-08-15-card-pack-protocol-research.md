# 卡包与协议研究中心实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

## 范围修订

用户最终要求协议能力不进入产品展示。执行结果必须满足：

- tab34 只保留武将、战法、卡包；
- 查询助手不回答协议命令或字段字典问题；
- 前端不得请求 `/api/intelligence/protocol/*`；
- 协议快照和内部只读 API 可作为开发资料保留。

**Goal:** 将卡包武将池、双版本协议命令目录和白名单字段字典一次性迁移为项目内只读快照，并接入研究页与查询助手。

**Architecture:** 同步器在开发阶段解析 `/Users/bytedance/stzb/server/src/main/resources`，输出版本化 JSON、manifest 和 checksum；Flask 运行时只加载项目内快照。`ResearchCatalogRepository` 统一提供卡包、协议和 Schema 检索，前端在现有 tab34 内扩展研究域，不增加侧栏。

**Tech Stack:** Python 3.9+、Flask、原生 JavaScript/CSS、Node.js 静态测试、Playwright Chrome E2E。

## Global Constraints

- 不迁移静态地图、城池或守军。
- 不运行时依赖 `/Users/bytedance/stzb`。
- 不展示未经验证的概率、保底或活动权重。
- 不提供发包、自动化动作、任意 SQL 或任意脚本执行。
- Schema 只包含规格指定的 12 张表。
- 保持 Modern Dark Data Console，不新增左栏，不使用 Emoji 作为正式标签。
- 不执行 Git commit。

---

### Task 1: 研究快照解析与生成

**Files:**
- Create: `intelligence/research_snapshot.py`
- Create: `test/test_intelligence_research_snapshot.py`
- Modify: `intelligence/__init__.py`

**Interfaces:**
- Produces: `build_research_snapshot(source_root: Path, output_root: Path, generated_at: str) -> dict`
- Produces: `parse_card_pack_tables(config_root: Path) -> list[dict]`
- Produces: `build_protocol_catalog(protocol_922: dict, protocol_924: dict) -> dict`
- Produces: `select_table_fields(field_types: dict) -> dict`

- [ ] **Step 1: Write failing parser tests**

覆盖：

```python
def test_card_pack_parser_merges_direct_and_child_pools(): ...
def test_protocol_diff_ignores_source_line_drift(): ...
def test_schema_export_is_exact_allowlist(): ...
def test_snapshot_writes_manifest_and_checksums(): ...
```

- [ ] **Step 2: Run tests and verify RED**

Run:

```bash
PYTHONPYCACHEPREFIX=/private/tmp/stzb-pycache \
  .venv/bin/python -m unittest test.test_intelligence_research_snapshot -v
```

Expected: import or missing-function failures.

- [ ] **Step 3: Implement minimal MemoryPack and catalog parser**

解析 `tb_cfg_card_extract*.bin` 的卡包关系，解析对应 `tb_cfg_card_prob*.bin` 的武将池，递归合并父子池；标准化双版本命令并只计算新增、删除、改名；字段字典严格按常量白名单裁剪。

- [ ] **Step 4: Run tests and verify GREEN**

Run the command from Step 2. Expected: all tests pass.

- [ ] **Step 5: Skip commit**

用户要求不执行 Git commit。

### Task 2: 生成真实研究快照

**Files:**
- Modify: `scripts/sync_intelligence_snapshot.py`
- Create: `data/intelligence/client-9.2.2/research/card_packs.json`
- Create: `data/intelligence/client-9.2.2/research/protocol_commands.json`
- Create: `data/intelligence/client-9.2.2/research/table_fields.json`
- Create: `data/intelligence/client-9.2.2/research/manifest.json`
- Create: `data/intelligence/client-9.2.2/research/checksums.sha256`

**Interfaces:**
- Consumes: `build_research_snapshot`
- Produces: CLI options `--research-source-root` and `--research-only`

- [ ] **Step 1: Write failing CLI/check tests**

验证真实输出包含 271 个卡包、63/2/1 协议差异、12 张表，并能检测 checksum 漂移。

- [ ] **Step 2: Run tests and verify RED**

Run:

```bash
PYTHONPYCACHEPREFIX=/private/tmp/stzb-pycache \
  .venv/bin/python -m unittest test.test_intelligence_research_snapshot -v
```

- [ ] **Step 3: Extend sync CLI and generate snapshot**

Run:

```bash
.venv/bin/python scripts/sync_intelligence_snapshot.py \
  --research-source-root /Users/bytedance/stzb/server/src/main/resources \
  --output-root data/intelligence/client-9.2.2 \
  --research-only
```

- [ ] **Step 4: Verify generated snapshot**

Run:

```bash
.venv/bin/python scripts/sync_intelligence_snapshot.py \
  --output-root data/intelligence/client-9.2.2 \
  --check
```

Expected: exit 0.

### Task 3: 研究仓库与 API

**Files:**
- Create: `intelligence/research_repository.py`
- Create: `intelligence/research_api.py`
- Create: `test/test_intelligence_research_repository.py`
- Create: `test/test_intelligence_research_api.py`
- Modify: `api_server.py`

**Interfaces:**
- Produces: `ResearchCatalogRepository(root: Path)`
- Produces: `search_card_packs`, `card_pack_detail`, `hero_card_packs`
- Produces: `search_commands`, `command_detail`, `protocol_summary`
- Produces: `search_schema`, `schema_detail`
- Produces: `register_intelligence_research_api(app, root, repository=None)`

- [ ] **Step 1: Write failing repository/API tests**

覆盖分页、搜索、反查、详情、404、400 和差异摘要。

- [ ] **Step 2: Run tests and verify RED**

```bash
PYTHONPYCACHEPREFIX=/private/tmp/stzb-pycache \
  .venv/bin/python -m unittest \
  test.test_intelligence_research_repository \
  test.test_intelligence_research_api -v
```

- [ ] **Step 3: Implement repository and Flask routes**

Repository 初始化时只加载一次 JSON；API 做类型转换和错误映射，不读取源仓库。

- [ ] **Step 4: Run tests and verify GREEN**

Run the Step 2 command. Expected: all tests pass.

### Task 4: 查询助手能力

**Files:**
- Modify: `query_agent/tools.py`
- Modify: `query_agent/service.py`
- Modify: `query_agent/api.py`
- Modify: `test/test_query_agent_tools.py`
- Modify: `test/test_query_agent_service.py`
- Modify: `test/test_query_agent_api.py`

**Interfaces:**
- `QueryTools.card_pack(pack_id=None, hero_id=None, query="")`
- `QueryTools.protocol_command(command_id)`
- `QueryTools.protocol_schema(table_name)`

- [ ] **Step 1: Write failing tool/service tests**

覆盖：

```text
查询卡包 802
张辽在哪些卡包
查询命令 5028
查询字段 Tb_world_city
```

- [ ] **Step 2: Run tests and verify RED**

```bash
PYTHONPYCACHEPREFIX=/private/tmp/stzb-pycache \
  .venv/bin/python -m unittest \
  test.test_query_agent_tools \
  test.test_query_agent_service \
  test.test_query_agent_api -v
```

- [ ] **Step 3: Implement bounded read-only tools and answers**

所有回答附带 `client-9.2.2-research` 证据和 `intelligence-research` UI action。执行词拒绝逻辑不变。

- [ ] **Step 4: Run tests and verify GREEN**

Run the Step 2 command. Expected: all tests pass.

### Task 5: 研究页四域交互

**Files:**
- Modify: `static/dashboard.html`
- Modify: `static/intelligence-research.js`
- Modify: `static/intelligence-research.css`
- Modify: `static/app1.js`
- Modify: `test/test_intelligence_research_static.py`
- Modify: `test/test_query_agent_static.py`

**Interfaces:**
- `ResearchCenter.openCardPack(packId)`
- `ResearchCenter.openCommand(commandId)`
- `ResearchCenter.openSchema(tableName)`

- [ ] **Step 1: Write failing static contract tests**

断言四个研究域、卡包/协议 API、状态光、字段矩阵和三种导航方法存在；断言不存在“抽卡概率”“发包”“执行 SQL”按钮文案。

- [ ] **Step 2: Run tests and verify RED**

```bash
PYTHONPYCACHEPREFIX=/private/tmp/stzb-pycache \
  .venv/bin/python -m unittest \
  test.test_intelligence_research_static \
  test.test_query_agent_static -v
```

- [ ] **Step 3: Implement HTML/JS/CSS**

保留现有武将、战法、阵容和模拟功能，新增卡包和协议渲染；协议详情内嵌 Schema 浏览器。

- [ ] **Step 4: Run tests and verify GREEN**

Run the Step 2 command. Expected: all tests pass.

### Task 6: 浏览器 E2E 与完整回归

**Files:**
- Modify: `test/js/dashboard-e2e.mjs`
- Modify: `README.md`

- [ ] **Step 1: Add failing E2E assertions**

Mock 新 API，并验证：

- 卡包搜索与武将跳转；
- 命令 5028 双版本详情；
- Schema 字段矩阵；
- 查询助手导航到卡包和命令详情；
- 移动端无横向溢出。

- [ ] **Step 2: Run focused E2E**

```bash
PYTHONPYCACHEPREFIX=/private/tmp/stzb-pycache \
  .venv/bin/python -m unittest test.test_dashboard_e2e -v
```

- [ ] **Step 3: Fix only feature-related failures**

不处理无关历史问题。

- [ ] **Step 4: Run full validation**

```bash
node --check static/intelligence-research.js
git diff --check
PYTHONPYCACHEPREFIX=/private/tmp/stzb-pycache \
  .venv/bin/python -m unittest discover -s test -v
```

Expected: all checks pass.

- [ ] **Step 5: Skip commit**

用户要求不执行 Git commit。
