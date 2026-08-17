# Product README Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Maintain a Chinese product homepage that presents Web and Android as capability-equivalent clients and displays ten real product screenshots without broken images.

**Architecture:** `README.md` owns the short product narrative, capability matrix, launch paths, privacy guidance, seven visible Web screenshots, and three visible Android screenshots. `docs/assets/screenshots/README.md` owns the stable screenshot filename contract and dimensions. The approved design spec remains the source of truth.

**Tech Stack:** Markdown, GitHub HTML tables and details blocks, Mermaid, Python 3.9+, Flask, Vanilla JavaScript, Kotlin/JVM 17, Android SDK 35, Gradle.

## Global Constraints

- Write the product homepage in Chinese and do not use Emoji.
- Present Web and Android as having the same core business capabilities; differences are limited to interaction form and runtime.
- Display one Web overview and six Web gallery screenshots captured from the real local product.
- Display three Android screenshots captured from the real local emulator.
- Keep all ten screenshot paths repository-relative and backed by existing files.
- Do not expose local absolute paths, account details, databases, packet captures, or profile contents.
- Keep the README focused on users; move API dictionaries and implementation state-machine details out of the main reading path.

---

### Task 1: Define the screenshot contract

**Files:**
- Create: `docs/assets/screenshots/README.md`

**Interfaces:**
- Consumes: the ten fixed filenames in `docs/superpowers/specs/2026-08-16-product-readme-redesign.md`
- Produces: a stable repository-relative upload directory and enable workflow referenced by `README.md`

- [ ] **Step 1: Create the screenshot directory guide**

Create a compact guide containing exactly these names:

```text
overview-intelligence.webp
gallery-live-army.webp
gallery-simulator.webp
gallery-research.webp
gallery-score.webp
gallery-attendance.webp
gallery-player-teams.webp
android-battlefield.webp
android-teams.webp
android-simulator.webp
```

Document that all ten files are enabled, Web images are `1440×1000`, and Android images are `1080×2400`.

- [ ] **Step 2: Verify the filename contract**

Run:

```bash
for name in \
  overview-intelligence.webp \
  gallery-live-army.webp \
  gallery-simulator.webp \
  gallery-research.webp \
  gallery-score.webp \
  gallery-attendance.webp \
  gallery-player-teams.webp \
  android-battlefield.webp \
  android-teams.webp \
  android-simulator.webp; do
  grep -q "$name" docs/assets/screenshots/README.md
done
```

Expected: exit code `0`.

### Task 2: Rewrite the repository homepage

**Files:**
- Modify: `README.md`
- Reference: `static/dashboard.html`
- Reference: `astzb/app/src/main/java/com/local/stzb/core/navigation/AppDestination.kt`
- Reference: `astzb/app/build.gradle.kts`

**Interfaces:**
- Consumes: the screenshot contract from Task 1 and current Web/Android build entry points
- Produces: a user-facing README with stable anchors, launch commands, and no visible broken images

- [ ] **Step 1: Replace the old technical README**

Write the sections in this order:

```text
产品首屏
双端定位
Web 产品预览
核心价值
Web 功能画廊
Android 产品预览
双端能力矩阵
功能总览
快速开始
数据与隐私
技术架构
项目结构
验证
许可与联系
```

Use one full-width Web screenshot, a two-column Web screenshot table, and a three-column Android screenshot table.

- [ ] **Step 2: Keep launch commands executable**

Use these Web commands:

```bash
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
./gradlew -p battle-engine test installDist
python api_server.py
```

Use these Android commands:

```bash
cd astzb
bash check_android_env.sh
./gradlew :app:assembleDebug
```

Document Web URL `http://127.0.0.1:8080` and APK output `astzb/app/build/outputs/apk/debug/app-debug.apk`.

- [ ] **Step 3: Verify product language and links**

Run:

```bash
rg -n "PoC|精简版|部分迁移|附属端|dashboard/|0000005c_|user-attachments" README.md
```

Expected: no output and exit code `1`.

Run:

```bash
rg -n "Web.*Android|Android.*Web|overview-intelligence.webp|android-simulator.webp|http://127.0.0.1:8080|app-debug.apk" README.md
```

Expected: all positioning, screenshot, and launch terms are present.

### Task 3: Validate documentation and regression safety

**Files:**
- Verify: `README.md`
- Verify: `docs/assets/screenshots/README.md`
- Verify: `docs/superpowers/specs/2026-08-16-product-readme-redesign.md`

**Interfaces:**
- Consumes: Tasks 1–2
- Produces: evidence that documentation is internally consistent and existing behavior still passes

- [ ] **Step 1: Check visible local links and image references**

Run a small Python check that extracts visible Markdown links and image tags from `README.md`, ignores `http` and anchors, and asserts every repository-relative target exists. Assert that all ten screenshots are visible and present.

Expected: prints `README links OK`.

- [ ] **Step 2: Run focused documentation checks**

Run:

```bash
git diff --check
test "$(rg -o 'docs/assets/screenshots/[a-z-]+\.webp' README.md | sort -u | wc -l | tr -d ' ')" = "10"
```

Expected: both commands exit `0`.

- [ ] **Step 3: Run the existing Node regression**

Run:

```bash
node --test test/js/*.test.mjs
```

Expected: all tests pass.

- [ ] **Step 4: Run the existing Python and Chrome regression**

Run:

```bash
PYTHONPYCACHEPREFIX=/private/tmp/stzb-pycache \
  .venv/bin/python -m unittest discover -s test -v
```

Expected: all tests pass.

- [ ] **Step 5: Inspect the final diff without committing**

Run:

```bash
git status --short
git diff -- README.md docs/assets/screenshots/README.md \
  docs/superpowers/specs/2026-08-16-product-readme-redesign.md \
  docs/superpowers/plans/2026-08-16-product-readme-redesign.md
```

Expected: only the intended README redesign files appear in this review; pre-existing repository cleanup changes remain uncommitted and untouched.
