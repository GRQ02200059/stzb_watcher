# 战斗模拟器武将画像卡实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 `/Users/bytedance/stzb` 中的客户端武将大图解码、压缩并同步为项目内 WebP 画像包，在战斗模拟器中实现 A 方案全息战场立绘卡与可靠降级链。

**Architecture:** `scripts/sync_hero_portraits.py` 负责配置映射、循环 XOR 解码、JPEG 校验、WebP 转换、manifest 和漂移检测；`sim_data.py` 只消费项目内 manifest 并扩展英雄目录元数据。前端只渲染画像、阵营光效与降级链，不把画像字段写入阵容状态、模板或战斗请求。

**Tech Stack:** Python 3.9+、`cwebp`、CSV/JSON/SHA-256、Vanilla JavaScript ES Module、CSS Design Tokens、Node.js test runner、Playwright + 系统 Chrome。

## Global Constraints

- 已确认视觉方向为 **A：全息战场立绘**。
- 本地源画像是固定 8 字节循环 XOR 数据，密钥为 `8e 50 9f e8 59 67 91 fb`。
- 解码后必须以 JPEG SOI `ff d8` 开头；缺失 JPEG EOI `ff d9` 时补齐一次，再使用 `cwebp`。
- WebP 最长边 `720px`，质量 `78`，不保留 metadata。
- 不复制原始约 `305MB` JPG 目录。
- 本地画像优先，CDN 只作一次后备，最终使用项目内占位图。
- 不新增第二套 `:root`，继续使用现有 design tokens。
- 不修改 Kotlin 战斗引擎、回放语义、阵容模板 schema 或模拟请求。
- 保留 `window.StzbSimulator.loadLineup()`、`getState()`、`run()`。
- 不执行 Git commit。

---

## 文件结构

### 新建

```text
scripts/sync_hero_portraits.py
test/test_hero_portrait_sync.py
static/hero-portraits/manifest.json
static/hero-portraits/placeholder.svg
static/hero-portraits/cards/*.webp
```

### 修改

```text
.gitignore
sim_data.py
README.md
static/simulator-workbench.js
static/simulator-workbench.css
test/test_sim_data.py
test/test_battle_simulator_static.py
test/js/simulator-workbench.test.mjs
test/js/dashboard-e2e.mjs
```

---

### Task 1: 实现画像解码、转换与漂移检测

**Files:**
- Create: `scripts/sync_hero_portraits.py`
- Create: `test/test_hero_portrait_sync.py`
- Modify: `.gitignore`

**Interfaces:**
- Produces: `decode_client_jpeg(encoded: bytes) -> bytes`
- Produces: `load_portrait_mappings(hero_table: Path, source_root: Path) -> list[dict]`
- Produces: `sync_portraits(source_root: Path, hero_table: Path, target_root: Path, cwebp: str = "cwebp", check: bool = False) -> dict`
- Produces: CLI `python scripts/sync_hero_portraits.py --source-root ... --hero-table ... --target-root ... [--check]`

- [ ] **Step 1: Write failing XOR and mapping tests**

Create `test/test_hero_portrait_sync.py`:

```python
import csv
import json
import os
import stat
import tempfile
import unittest
from pathlib import Path

from scripts.sync_hero_portraits import (
    XOR_KEY,
    decode_client_jpeg,
    load_portrait_mappings,
    sync_portraits,
)


class HeroPortraitSyncTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.source = self.root / "source"
        self.target = self.root / "target"
        self.source.mkdir()
        self.hero_table = self.root / "hero_table.csv"
        self.hero_table.write_text(
            "heroid,icon_hero_id,is_release,name\n"
            "100001,100900,1,甲\n"
            "100002,100900,1,乙\n"
            "100003,0,1,丙\n"
            "100004,0,0,未发布\n",
            encoding="utf-8",
        )
        self.jpeg = b"\xff\xd8\xff\xe0fixture-jpeg"
        for image_id in (100900, 100003):
            encoded = bytes(
                value ^ XOR_KEY[index % len(XOR_KEY)]
                for index, value in enumerate(self.jpeg)
            )
            (self.source / f"big_card_{image_id}.jpg").write_bytes(encoded)

    def tearDown(self):
        self.temp.cleanup()

    def test_decode_client_jpeg_uses_verified_cycle_xor_key(self):
        encoded = bytes(
            value ^ XOR_KEY[index % len(XOR_KEY)]
            for index, value in enumerate(self.jpeg)
        )

        self.assertEqual(self.jpeg, decode_client_jpeg(encoded))

    def test_decode_client_jpeg_appends_missing_eoi_once(self):
        encoded = bytes(
            value ^ XOR_KEY[index % len(XOR_KEY)]
            for index, value in enumerate(self.jpeg[:-2])
        )

        self.assertEqual(self.jpeg, decode_client_jpeg(encoded))

    def test_icon_id_precedes_hero_id_and_deduplicates_assets(self):
        mappings = load_portrait_mappings(self.hero_table, self.source)

        by_hero = {row["heroId"]: row for row in mappings}
        self.assertEqual(100900, by_hero[100001]["iconId"])
        self.assertEqual(100900, by_hero[100002]["iconId"])
        self.assertEqual(100003, by_hero[100003]["iconId"])
        self.assertEqual(
            {100900, 100003},
            {row["iconId"] for row in mappings if row["sourceExists"]},
        )
```

- [ ] **Step 2: Run tests and verify RED**

Run:

```bash
PYTHONPYCACHEPREFIX=/private/tmp/stzb-pycache \
  .venv/bin/python -m unittest test.test_hero_portrait_sync -v
```

Expected: FAIL with `ModuleNotFoundError: scripts.sync_hero_portraits`.

- [ ] **Step 3: Implement XOR decoder and mappings**

Create `scripts/sync_hero_portraits.py` with:

```python
#!/usr/bin/env python3
from __future__ import annotations

import argparse
import csv
import hashlib
import json
import shutil
import subprocess
import tempfile
from datetime import datetime
from pathlib import Path


XOR_KEY = bytes.fromhex("8e509fe8596791fb")


def decode_client_jpeg(encoded):
    decoded = bytes(
        value ^ XOR_KEY[index % len(XOR_KEY)]
        for index, value in enumerate(encoded)
    )
    if not decoded.startswith(b"\xff\xd8"):
        raise ValueError("decoded portrait is not JPEG")
    if not decoded.endswith(b"\xff\xd9"):
        decoded += b"\xff\xd9"
    return decoded


def load_portrait_mappings(hero_table, source_root):
    hero_table = Path(hero_table)
    source_root = Path(source_root)
    rows = []
    with hero_table.open(encoding="utf-8-sig", newline="") as handle:
        for row in csv.DictReader(handle):
            if row.get("is_release") != "1":
                continue
            hero_id = int(row.get("heroid") or 0)
            if hero_id <= 0:
                continue
            configured_icon = int(row.get("icon_hero_id") or 0)
            candidates = [configured_icon, hero_id] if configured_icon else [hero_id]
            icon_id = next(
                (
                    candidate
                    for candidate in candidates
                    if (source_root / f"big_card_{candidate}.jpg").is_file()
                ),
                candidates[0],
            )
            source = source_root / f"big_card_{icon_id}.jpg"
            rows.append({
                "heroId": hero_id,
                "iconId": icon_id,
                "source": str(source),
                "sourceExists": source.is_file(),
            })
    return rows
```

- [ ] **Step 4: Add failing sync/manifest/check tests**

Append:

```python
    def _fake_cwebp(self):
        path = self.root / "fake-cwebp"
        path.write_text(
            "#!/usr/bin/env python3\n"
            "import pathlib,sys\n"
            "output = pathlib.Path(sys.argv[sys.argv.index('-o') + 1])\n"
            "output.write_bytes(b'RIFFfixtureWEBP')\n",
            encoding="utf-8",
        )
        path.chmod(path.stat().st_mode | stat.S_IEXEC)
        return str(path)

    def test_sync_writes_unique_assets_manifest_and_placeholder(self):
        manifest = sync_portraits(
            self.source,
            self.hero_table,
            self.target,
            cwebp=self._fake_cwebp(),
        )

        self.assertEqual(1, manifest["schemaVersion"])
        self.assertEqual(2, manifest["assetCount"])
        self.assertEqual(3, manifest["heroCount"])
        self.assertTrue((self.target / "cards/100900.webp").is_file())
        self.assertTrue((self.target / "cards/100003.webp").is_file())
        self.assertTrue((self.target / "placeholder.svg").is_file())
        self.assertTrue((self.target / "manifest.json").is_file())
        self.assertEqual(
            manifest["heroes"]["100001"]["iconId"],
            manifest["heroes"]["100002"]["iconId"],
        )

    def test_check_rejects_modified_output(self):
        converter = self._fake_cwebp()
        sync_portraits(
            self.source,
            self.hero_table,
            self.target,
            cwebp=converter,
        )
        (self.target / "cards/100900.webp").write_bytes(b"drift")

        with self.assertRaisesRegex(ValueError, "portrait output drift"):
            sync_portraits(
                self.source,
                self.hero_table,
                self.target,
                cwebp=converter,
                check=True,
            )

    def test_invalid_decoded_jpeg_is_recorded_without_stopping_other_assets(self):
        (self.source / "big_card_100003.jpg").write_bytes(b"invalid")

        manifest = sync_portraits(
            self.source,
            self.hero_table,
            self.target,
            cwebp=self._fake_cwebp(),
        )

        self.assertEqual(1, manifest["assetCount"])
        self.assertEqual(1, len(manifest["errors"]))
        self.assertEqual(100003, manifest["errors"][0]["iconId"])
```

- [ ] **Step 5: Run tests and verify RED**

Run:

```bash
PYTHONPYCACHEPREFIX=/private/tmp/stzb-pycache \
  .venv/bin/python -m unittest test.test_hero_portrait_sync -v
```

Expected: FAIL because `sync_portraits()` is missing.

- [ ] **Step 6: Implement converter, manifest, placeholder and check**

Implement:

```python
def _sha256_bytes(value):
    return hashlib.sha256(value).hexdigest()


def _sha256_file(path):
    return _sha256_bytes(Path(path).read_bytes())


def _convert_portrait(decoded, output, cwebp):
    output = Path(output)
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(suffix=".jpg") as source:
        source.write(decoded)
        source.flush()
        result = subprocess.run(
            [
                cwebp,
                "-quiet",
                "-q", "78",
                "-resize", "0", "720",
                "-metadata", "none",
                source.name,
                "-o", str(output),
            ],
            capture_output=True,
            text=True,
            check=False,
        )
    if result.returncode != 0:
        raise ValueError(result.stderr or "cwebp failed")


def _placeholder_svg():
    return """<svg xmlns="http://www.w3.org/2000/svg" width="720" height="720" viewBox="0 0 720 720">
<defs><radialGradient id="g"><stop stop-color="#193653"/><stop offset="1" stop-color="#07101d"/></radialGradient></defs>
<rect width="720" height="720" fill="url(#g)"/>
<path d="M190 500c34-88 93-132 170-132s136 44 170 132" fill="none" stroke="#43d5ff55" stroke-width="18"/>
<circle cx="360" cy="258" r="94" fill="none" stroke="#43d5ff55" stroke-width="18"/>
<text x="360" y="650" text-anchor="middle" fill="#8ca3bb" font-family="monospace" font-size="24" letter-spacing="6">PORTRAIT OFFLINE</text>
</svg>"""
```

`sync_portraits()` must:

1. fail if source root or hero table is missing;
2. fail with `cwebp not found` when `shutil.which(cwebp)` is empty;
3. decode and convert each unique existing `iconId` once;
4. record `sourceSha256`, `decodedSha256`, `outputSha256`, `outputBytes`;
5. map every hero to `iconId`, `local`, and relative output;
6. collect per-icon errors;
7. write deterministic `manifest.json` plus generated timestamp;
8. in check mode compare hero mapping, source hashes, file set, output hashes and placeholder hash.

- [ ] **Step 7: Allow generated WebP assets in Git**

Append to `.gitignore`:

```gitignore
!static/hero-portraits/
!static/hero-portraits/**/*.webp
!static/hero-portraits/**/*.svg
!static/hero-portraits/**/*.json
```

- [ ] **Step 8: Run Task 1 GREEN**

Run:

```bash
PYTHONPYCACHEPREFIX=/private/tmp/stzb-pycache \
  .venv/bin/python -m unittest test.test_hero_portrait_sync -v

.venv/bin/python scripts/sync_hero_portraits.py --help
```

Expected: all tests pass and CLI help lists `--source-root`, `--hero-table`, `--target-root`, `--cwebp`, `--check`.

---

### Task 2: 生成真实画像包并扩展英雄 API

**Files:**
- Generate: `static/hero-portraits/manifest.json`
- Generate: `static/hero-portraits/placeholder.svg`
- Generate: `static/hero-portraits/cards/*.webp`
- Modify: `sim_data.py`
- Modify: `test/test_sim_data.py`
- Modify: `test/test_simulate_api.py`

**Interfaces:**
- Consumes: Task 1 `manifest.json`
- Produces: `load_portrait_manifest() -> dict`
- Produces hero fields: `iconId`, `portraitUrl`, `portraitFallbackUrl`, `portraitLocal`

- [ ] **Step 1: Generate the real portrait package**

Run:

```bash
.venv/bin/python scripts/sync_hero_portraits.py \
  --source-root /Users/bytedance/stzb/work/emulator-backups/Pixel_6-before-12G-20260814-223729/Documents/mini_client_res/card/card_big \
  --hero-table battle-engine/src/main/resources/battle-config/hero_table.csv \
  --target-root static/hero-portraits
```

Expected:

```text
synced hero portraits: heroes=1400 assets=<non-zero> errors=<count>
```

Verify default heroes with valid local sources:

```bash
for id in 100027 100016 100090 100013 100023; do
  test -s "static/hero-portraits/cards/$id.webp"
done
```

If a hero uses a distinct `icon_hero_id`, verify its manifest mapping instead of requiring `<heroId>.webp`.
The current local `100649` source remains corrupt after XOR + EOI repair; its manifest row must be
`local=false` and the UI must load its CDN fallback.

- [ ] **Step 2: Write failing hero metadata tests**

Append to `test/test_sim_data.py`:

```python
    def test_heroes_expose_local_portrait_and_cdn_fallback(self):
        by_id = {hero["id"]: hero for hero in sim_data.load_heroes()}
        zhangliao = by_id[100027]

        self.assertEqual(100027, zhangliao["iconId"])
        self.assertEqual(
            "/static/hero-portraits/cards/100027.webp",
            zhangliao["portraitUrl"],
        )
        self.assertTrue(zhangliao["portraitLocal"])
        self.assertIn(
            "card_medium_100027.jpg",
            zhangliao["portraitFallbackUrl"],
        )

    def test_missing_local_portrait_uses_placeholder(self):
        hero = sim_data._portrait_fields(
            hero_id=999999,
            icon_id=999999,
            manifest={"heroes": {}},
        )

        self.assertFalse(hero["portraitLocal"])
        self.assertEqual(
            "/static/hero-portraits/placeholder.svg",
            hero["portraitUrl"],
        )
        self.assertIn("card_medium_999999.jpg", hero["portraitFallbackUrl"])
```

Append to `test/test_simulate_api.py`:

```python
    def test_heroes_endpoint_exposes_portrait_metadata(self):
        response = api_server.app.test_client().get("/api/simulate/heroes")
        by_id = {
            hero["id"]: hero
            for hero in response.get_json()["heroes"]
        }

        self.assertTrue(by_id[100027]["portraitLocal"])
        self.assertTrue(by_id[100027]["portraitUrl"].endswith(".webp"))
        self.assertIn("portraitFallbackUrl", by_id[100027])
```

- [ ] **Step 3: Run tests and verify RED**

Run:

```bash
PYTHONPYCACHEPREFIX=/private/tmp/stzb-pycache \
  .venv/bin/python -m unittest \
  test.test_sim_data \
  test.test_simulate_api.SimulateApiTest.test_heroes_endpoint_exposes_portrait_metadata -v
```

Expected: FAIL because portrait fields are missing.

- [ ] **Step 4: Implement portrait manifest consumption**

Modify `sim_data.py`:

```python
_PORTRAIT_ROOT = os.path.join(_BASE_DIR, "static", "hero-portraits")
_PORTRAIT_MANIFEST = os.path.join(_PORTRAIT_ROOT, "manifest.json")
_PORTRAIT_PLACEHOLDER = "/static/hero-portraits/placeholder.svg"
_PORTRAIT_CDN = (
    "https://g0.gph.netease.com/ngsocial/community/stzb/cn/cards/"
    "cut/card_medium_{icon_id}.jpg?gameid=g10"
)


@lru_cache(maxsize=1)
def load_portrait_manifest():
    try:
        with open(_PORTRAIT_MANIFEST, encoding="utf-8") as handle:
            return json.load(handle)
    except (OSError, ValueError):
        return {"heroes": {}}


def _portrait_fields(hero_id, icon_id, manifest=None):
    manifest = manifest or load_portrait_manifest()
    row = manifest.get("heroes", {}).get(str(hero_id), {})
    resolved_icon_id = int(row.get("iconId") or icon_id or hero_id)
    local = bool(row.get("local"))
    return {
        "iconId": resolved_icon_id,
        "portraitUrl": (
            f"/static/hero-portraits/cards/{resolved_icon_id}.webp"
            if local else _PORTRAIT_PLACEHOLDER
        ),
        "portraitFallbackUrl": _PORTRAIT_CDN.format(
            icon_id=resolved_icon_id
        ),
        "portraitLocal": local,
    }
```

Import `json`. In `load_heroes()`, read `icon_hero_id`, then merge:

```python
hero = {
    "id": hero_id,
    "name": ...,
    "camp": ...,
    "army": ...,
    "quality": ...,
}
hero.update(
    _portrait_fields(
        hero_id,
        _to_int(row.get("icon_hero_id")) or hero_id,
    )
)
heroes.append(hero)
```

- [ ] **Step 5: Run API GREEN and manifest check**

Run:

```bash
PYTHONPYCACHEPREFIX=/private/tmp/stzb-pycache \
  .venv/bin/python -m unittest \
  test.test_sim_data \
  test.test_simulate_api -v

.venv/bin/python scripts/sync_hero_portraits.py \
  --source-root /Users/bytedance/stzb/work/emulator-backups/Pixel_6-before-12G-20260814-223729/Documents/mini_client_res/card/card_big \
  --hero-table battle-engine/src/main/resources/battle-config/hero_table.csv \
  --target-root static/hero-portraits \
  --check
```

Expected: tests pass and `hero portrait mirror check: PASS`.

---

### Task 3: 实现 A 方案画像卡、缩略图与降级链

**Files:**
- Modify: `static/simulator-workbench.js`
- Modify: `static/simulator-workbench.css`
- Modify: `test/js/simulator-workbench.test.mjs`
- Modify: `test/test_battle_simulator_static.py`

**Interfaces:**
- Consumes hero metadata from Task 2
- Produces: `portraitPresentation(hero, loading) -> dict`
- Produces: `advancePortraitFallback(image) -> "cdn" | "placeholder" | "done"`
- Produces CSS classes: `.sim-hero-portrait`, `.sim-hero-portrait-image`, `.sim-hero-scan`, `.sim-hero-glass`, `.sim-library-portrait`

- [ ] **Step 1: Write failing portrait model and fallback tests**

Append to `test/js/simulator-workbench.test.mjs`:

```javascript
import {
  advancePortraitFallback,
  portraitPresentation,
} from "../../static/simulator-workbench.js";

test("portrait presentation keeps local CDN and placeholder sources", () => {
  const model = portraitPresentation(
    {
      id: 100027,
      name: "张辽",
      portraitUrl: "/static/hero-portraits/cards/100027.webp",
      portraitFallbackUrl: "https://cdn/card_medium_100027.jpg",
    },
    "eager",
  );

  assert.deepEqual(model, {
    src: "/static/hero-portraits/cards/100027.webp",
    fallbackSrc: "https://cdn/card_medium_100027.jpg",
    placeholderSrc: "/static/hero-portraits/placeholder.svg",
    alt: "张辽武将画像",
    loading: "eager",
  });
});

test("portrait fallback advances once through CDN placeholder and done", () => {
  const image = {
    src: "/static/hero-portraits/cards/100027.webp",
    dataset: {
      portraitStep: "local",
      fallbackSrc: "https://cdn/card_medium_100027.jpg",
      placeholderSrc: "/static/hero-portraits/placeholder.svg",
    },
  };

  assert.equal(advancePortraitFallback(image), "cdn");
  assert.equal(image.src, image.dataset.fallbackSrc);
  assert.equal(advancePortraitFallback(image), "placeholder");
  assert.equal(image.src, image.dataset.placeholderSrc);
  assert.equal(advancePortraitFallback(image), "done");
});

test("templates do not serialize portrait metadata", () => {
  const encoded = serializeTemplate(fixtureState(), "matchup");
  assert.equal(JSON.stringify(encoded).includes("portrait"), false);
});
```

- [ ] **Step 2: Write failing static CSS/markup contracts**

Append to `test/test_battle_simulator_static.py`:

```python
    def test_portrait_card_has_holographic_visual_contract(self):
        script = WORKBENCH.read_text(encoding="utf-8")
        css = CSS.read_text(encoding="utf-8")

        for token in (
            "sim-hero-portrait",
            "sim-hero-portrait-image",
            "sim-hero-scan",
            "sim-hero-glass",
            "sim-library-portrait",
            "data-sim-portrait",
            "data-fallback-src",
        ):
            self.assertIn(token, script + css)
        self.assertIn("scale(1.04)", css)
        self.assertIn("prefers-reduced-motion", css)
```

- [ ] **Step 3: Run tests and verify RED**

Run:

```bash
node --test test/js/simulator-workbench.test.mjs

PYTHONPYCACHEPREFIX=/private/tmp/stzb-pycache \
  .venv/bin/python -m unittest \
  test.test_battle_simulator_static.BattleSimulatorStaticTest.test_portrait_card_has_holographic_visual_contract -v
```

Expected: FAIL because portrait helpers and selectors do not exist.

- [ ] **Step 4: Implement portrait helpers**

Add to `static/simulator-workbench.js`:

```javascript
const PORTRAIT_PLACEHOLDER = "/static/hero-portraits/placeholder.svg";
const CAMP_COLORS = {
  1: "#46b06e",
  2: "#4a8fe0",
  3: "#e05050",
  4: "#c8a044",
  5: "#9060d0",
  6: "#3ab8c8",
  0: "#60758a",
};

export function portraitPresentation(hero, loading = "lazy") {
  return {
    src: hero?.portraitUrl || PORTRAIT_PLACEHOLDER,
    fallbackSrc: hero?.portraitFallbackUrl || "",
    placeholderSrc: PORTRAIT_PLACEHOLDER,
    alt: `${hero?.name || `武将 ${hero?.id || "?"}`}武将画像`,
    loading,
  };
}

export function advancePortraitFallback(image) {
  const step = image.dataset.portraitStep || "local";
  if (step === "local" && image.dataset.fallbackSrc) {
    image.dataset.portraitStep = "cdn";
    image.src = image.dataset.fallbackSrc;
    return "cdn";
  }
  if (step !== "placeholder") {
    image.dataset.portraitStep = "placeholder";
    image.src = image.dataset.placeholderSrc || PORTRAIT_PLACEHOLDER;
    return "placeholder";
  }
  image.dataset.portraitStep = "done";
  return "done";
}
```

Add a captured document error listener:

```javascript
function handlePortraitError(event) {
  const image = event.target;
  if (!(image instanceof HTMLImageElement)) return;
  if (!image.matches("[data-sim-portrait]")) return;
  advancePortraitFallback(image);
}
```

In `installBrowserController()`:

```javascript
document.addEventListener("error", handlePortraitError, true);
```

- [ ] **Step 5: Replace hero card visual markup**

In `heroCardMarkup(side, hero)`:

```javascript
const portrait = portraitPresentation(info, "eager");
const accent = CAMP_COLORS[Number(info.camp) || 0];
```

Render:

```html
<article
  class="sim-hero-card"
  data-side="${side}"
  data-position="${hero.position}"
  data-camp="${info.camp || 0}"
  style="--sim-camp-accent:${accent};--sim-camp-glow:${accent}55"
>
  <div class="sim-hero-visual" data-monogram="${...}">
    <div class="sim-hero-portrait">
      <img
        class="sim-hero-portrait-image"
        data-sim-portrait
        data-portrait-step="local"
        data-fallback-src="${escapeHtml(portrait.fallbackSrc)}"
        data-placeholder-src="${escapeHtml(portrait.placeholderSrc)}"
        src="${escapeHtml(portrait.src)}"
        alt="${escapeHtml(portrait.alt)}"
        loading="${portrait.loading}"
        decoding="async"
      >
      <div class="sim-hero-scan"></div>
    </div>
    ...HUD...
  </div>
  <div class="sim-hero-body sim-hero-glass">
    <div class="sim-hero-runtime-stats">
      <span>LV <b>${hero.level}</b></span>
      <span>ADV <b>+${hero.up}</b></span>
      <span>MORALE <b>${browserRuntime.state[side].morale}</b></span>
    </div>
    ...existing inputs, skills and actions...
  </div>
</article>
```

Do not add portrait fields to reducer state.

- [ ] **Step 6: Add library thumbnails**

In `heroLibraryItem(hero)` use `portraitPresentation(hero, "lazy")` and render:

```html
<span class="sim-library-portrait">
  <img
    data-sim-portrait
    data-portrait-step="local"
    data-fallback-src="..."
    data-placeholder-src="..."
    src="..."
    alt="..."
    loading="lazy"
    decoding="async"
  >
</span>
```

Keep the 160-item render cap.

- [ ] **Step 7: Implement A-direction CSS**

Add:

```css
.sim-hero-card {
  border-color: color-mix(in srgb, var(--sim-camp-accent) 48%, var(--border2));
  box-shadow:
    var(--shadow-sm),
    inset 0 1px color-mix(in srgb, var(--sim-camp-accent) 22%, transparent);
}

.sim-hero-visual {
  min-height: 206px;
  isolation: isolate;
  background: var(--panel);
}

.sim-hero-portrait {
  position: absolute;
  inset: 0;
  overflow: hidden;
}

.sim-hero-portrait::after {
  position: absolute;
  inset: 0;
  content: "";
  background:
    linear-gradient(90deg, var(--panel) 0, transparent 35%, transparent 70%, color-mix(in srgb, var(--panel) 70%, transparent)),
    linear-gradient(0deg, var(--panel) 0, transparent 58%),
    radial-gradient(circle at 76% 14%, var(--sim-camp-glow), transparent 36%);
}

.sim-hero-portrait-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: center 14%;
  filter: saturate(1.06) contrast(1.05);
  transition: transform var(--dur-in) var(--ease), filter var(--dur) var(--ease);
}

.sim-hero-card:hover .sim-hero-portrait-image,
.sim-hero-card:focus-within .sim-hero-portrait-image {
  transform: scale(1.04);
  filter: saturate(1.14) contrast(1.08);
}

.sim-hero-scan {
  position: absolute;
  inset: -30% auto -30% -42%;
  width: 26%;
  pointer-events: none;
  opacity: 0;
  background: linear-gradient(90deg, transparent, #ffffff2a, transparent);
  transform: rotate(16deg);
}

.sim-hero-card:hover .sim-hero-scan,
.sim-hero-card:focus-within .sim-hero-scan {
  animation: sim-portrait-scan 800ms var(--ease) 1;
}

@keyframes sim-portrait-scan {
  0% { left: -42%; opacity: 0; }
  20% { opacity: 1; }
  100% { left: 126%; opacity: 0; }
}

.sim-hero-glass {
  position: relative;
  z-index: 4;
  margin: -18px 8px 8px;
  background: linear-gradient(135deg, #0a1423eb, #0c1725cf);
  border: 1px solid color-mix(in srgb, var(--sim-camp-accent) 35%, var(--border));
  border-radius: var(--r-md);
  backdrop-filter: blur(15px);
}

.sim-library-portrait {
  width: 44px;
  height: 52px;
  overflow: hidden;
  border: 1px solid color-mix(in srgb, var(--cyan) 32%, var(--border));
  border-radius: var(--r-sm);
}

.sim-library-portrait img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

@media (prefers-reduced-motion: reduce) {
  .sim-hero-portrait-image {
    transform: none !important;
  }
  .sim-hero-scan {
    display: none;
  }
}
```

- [ ] **Step 8: Run Task 3 GREEN**

Run:

```bash
node --check static/simulator-workbench.js
node --test test/js/simulator-workbench.test.mjs

PYTHONPYCACHEPREFIX=/private/tmp/stzb-pycache \
  .venv/bin/python -m unittest test.test_battle_simulator_static -v
```

Expected: all tests pass.

---

### Task 4: Chrome E2E、文档和完整回归

**Files:**
- Modify: `test/js/dashboard-e2e.mjs`
- Modify: `README.md`

**Interfaces:**
- Verifies image loading, replacement, fallback and responsive behavior

- [ ] **Step 1: Extend simulator E2E mocks**

Add portrait fields to `simulatorHeroes`:

```javascript
const portrait = (id) => ({
  iconId: id,
  portraitUrl: `/static/hero-portraits/cards/${id}.webp`,
  portraitFallbackUrl:
    `https://g0.gph.netease.com/ngsocial/community/stzb/cn/cards/` +
    `cut/card_medium_${id}.jpg?gameid=g10`,
  portraitLocal: true,
});

const simulatorHeroes = [
  { id: 100027, name: "张辽", camp: 2, army: 3, quality: 4, ...portrait(100027) },
  ...
];
```

- [ ] **Step 2: Add failing portrait Chrome flow**

After opening tab 25:

```javascript
await page.waitForFunction(() => {
  const images = [...document.querySelectorAll(
    "#sim-attacker-team [data-sim-portrait], #sim-defender-team [data-sim-portrait]"
  )];
  return images.length === 6 &&
    images.every((image) => image.complete && image.naturalWidth > 0);
});

assert.equal(
  await page.locator("#sim-attacker-team [data-sim-portrait]").first()
    .getAttribute("src"),
  "/static/hero-portraits/cards/100027.webp",
);

await page.locator(
  "#sim-attacker-team [data-position='0'] [data-sim-action='open-library'][data-kind='hero']"
).click();
await page.locator("[data-sim-input='library-query']").fill("刘备");
await page.getByRole("button", { name: /刘备/ }).click();
await page.waitForFunction(() =>
  document.querySelector(
    "#sim-attacker-team [data-position='0'] [data-sim-portrait]"
  )?.getAttribute("src").includes("100016.webp")
);

await page.evaluate(() => {
  const image = document.querySelector(
    "#sim-attacker-team [data-position='0'] [data-sim-portrait]"
  );
  image.dataset.fallbackSrc = "/static/missing-fallback.jpg";
  image.src = "/static/missing-local.webp";
});
await page.waitForFunction(() =>
  document.querySelector(
    "#sim-attacker-team [data-position='0'] [data-sim-portrait]"
  )?.dataset.portraitStep === "placeholder"
);
```

Mobile assertion:

```javascript
await page.setViewportSize({ width: 390, height: 844 });
assert.equal(await page.locator(".sim-hero-grid").first().evaluate(
  (element) => element.scrollWidth > element.clientWidth
), true);
assert.equal(await page.locator(".sim-hero-glass").first().isVisible(), true);
```

- [ ] **Step 3: Run E2E and verify RED**

Run:

```bash
PYTHONPYCACHEPREFIX=/private/tmp/stzb-pycache \
  .venv/bin/python -m unittest test.test_dashboard_e2e -v
```

Expected: FAIL before portrait markup exists.

- [ ] **Step 4: Fix only portrait-related E2E issues**

Allowed fixes:

- image source/fallback metadata;
- card CSS dimensions;
- captured error handler;
- library thumbnail markup;
- mobile overflow.

Do not modify unrelated dashboard behavior.

- [ ] **Step 5: Update README**

Add:

```text
Hero portrait source and XOR decoding
sync_hero_portraits.py command
--check command
cwebp prerequisite
local-first/CDN/placeholder fallback order
generated manifest and asset directory
```

Commands:

```bash
.venv/bin/python scripts/sync_hero_portraits.py \
  --source-root /Users/bytedance/stzb/work/emulator-backups/Pixel_6-before-12G-20260814-223729/Documents/mini_client_res/card/card_big \
  --hero-table battle-engine/src/main/resources/battle-config/hero_table.csv \
  --target-root static/hero-portraits

.venv/bin/python scripts/sync_hero_portraits.py \
  --source-root /Users/bytedance/stzb/work/emulator-backups/Pixel_6-before-12G-20260814-223729/Documents/mini_client_res/card/card_big \
  --hero-table battle-engine/src/main/resources/battle-config/hero_table.csv \
  --target-root static/hero-portraits \
  --check
```

- [ ] **Step 6: Run focused validation**

Run:

```bash
PYTHONPYCACHEPREFIX=/private/tmp/stzb-pycache \
  .venv/bin/python -m unittest \
  test.test_hero_portrait_sync \
  test.test_sim_data \
  test.test_simulate_api \
  test.test_battle_simulator_static \
  test.test_dashboard_e2e -v

node --check static/simulator-workbench.js
node --test test/js/simulator-workbench.test.mjs
```

- [ ] **Step 7: Run full validation**

Run:

```bash
git diff --check

.venv/bin/python scripts/sync_hero_portraits.py \
  --source-root /Users/bytedance/stzb/work/emulator-backups/Pixel_6-before-12G-20260814-223729/Documents/mini_client_res/card/card_big \
  --hero-table battle-engine/src/main/resources/battle-config/hero_table.csv \
  --target-root static/hero-portraits \
  --check

PYTHONPYCACHEPREFIX=/private/tmp/stzb-pycache \
  .venv/bin/python -m unittest discover -s test -v
```

Expected: all checks pass.

- [ ] **Step 8: Restart and verify the existing backend**

Restart only the existing `api_server.py` process on port `8080`, then verify:

```bash
curl -fsS http://127.0.0.1:8080/api/simulate/heroes
curl -fsSI http://127.0.0.1:8080/static/hero-portraits/cards/100027.webp
```

Expected: hero metadata contains portrait fields and the WebP returns `200`.

---

## Self-Review Checklist

- [ ] XOR decode precedes JPEG/WebP handling.
- [ ] Known key is covered by a deterministic test.
- [ ] `icon_hero_id` mapping and asset deduplication are tested.
- [ ] Manifest and `--check` cover source and generated drift.
- [ ] Only referenced unique images are generated.
- [ ] API is local-first and manifest-driven.
- [ ] Portrait metadata never enters state, templates or simulation requests.
- [ ] Card and library both use one finite fallback chain.
- [ ] A-direction visual requirements map to explicit CSS.
- [ ] Reduced motion is honored.
- [ ] Chrome verifies image loading, replacement, fallback and mobile layout.
- [ ] No Kotlin battle semantics are changed.
- [ ] No Git commit is executed.
