# 协议证据基础层第一阶段验收

验收日期：2026-08-19

客户端版本：9.2.2

## 目标

第一阶段交付可追溯、确定性、隐私安全的协议证据基础层。它必须覆盖当前 capture_new
中的全部命令，区分已捕获、客户端命名、字段化和业务批准状态，并向 Web 与 Android
提供同源契约。第一阶段不宣称所有命令都已经实现业务解析。

## 产物

| 产物 | 作用 |
|---|---|
| data/protocol/client-9.2.2/command-catalog.json | 94 个命令的 ID、名称、样例和结构摘要 |
| data/protocol/client-9.2.2/field-registry.json | 客户端确认字段和证据等级 |
| data/protocol/client-9.2.2/manifest.json | 文件哈希、大小和覆盖统计 |
| docs/verification/protocol-coverage-client-9.2.2.md | 人类可读覆盖报告 |
| astzb/app/src/main/assets/protocol_contract_client_9_2_2.json | Android 精简共享契约 |
| protocol/evidence/client-9.2.2/*.json | 人工审核的客户端源码锚点 |
| scripts/build_protocol_evidence.py | 只读生成器和 check 命令 |
| protocol_registry.py | Web 运行时只读契约加载器 |

## 覆盖结果

| 指标 | 结果 |
|---|---:|
| capture_new 命令目录 | 94 |
| 客户端命名命令 | 90 |
| CLIENT_CONFIRMED 命令 | 14 |
| 注册字段 | 28 |
| 业务批准字段 | 22 |
| Web typed/raw/unsupported | 12 / 82 / 0 |
| Android typed/raw/unsupported | 13 / 81 / 0 |

raw 命令仍由通用抓包层保存，不会被伪装为已完成业务解析。

## 完成门禁

### 1. 94 个命令全部进入目录

证据：test_all_capture_directories_are_cataloged 直接比较 capture_new 目录和生成目录，
并断言数量为 94。

结果：通过。

### 2. 已有专用解析命令都有 evidence

证据：test_all_existing_typed_parser_commands_have_evidence 覆盖个人/同盟战报、同盟成员、
玩家统计、武将记录、公告、聊天通知、系统通知、世界场景、地块、同盟建筑帮助和 DB
更新等当前专用入口。

结果：通过。

### 3. 未解析命令明确标记 raw/unknown

证据：command catalog 和覆盖报告分别记录 Web/Android 状态。当前无 unsupported，未
字段化命令均为 raw。

结果：通过。

### 4. Web/Android ID 与字段约定一致

证据：ProtocolRegistry 测试验证十六进制/十进制等价；Android ProtocolContractTest
验证 103、5026、5028、成员武勋和 WID 约定。

结果：通过。

### 5. 版本化产物无绝对路径或用户内容

证据：隐私测试扫描绝对根、password、sessionToken、passport、role_name、payload、
preview、rawJson 和 decodedText。Android asset 另外扫描 samplePaths/clientSources。

结果：通过。

### 6. 生成器 check 通过

命令：

    .venv/bin/python scripts/build_protocol_evidence.py \
      --capture-root capture_new \
      --client-root /Users/bytedance/stzb/stzb_9.2.2_out_branch_9.1.1776213/assets/decompiled \
      --evidence-root protocol/evidence/client-9.2.2 \
      --output-root data/protocol/client-9.2.2 \
      --report docs/verification/protocol-coverage-client-9.2.2.md \
      --android-contract astzb/app/src/main/assets/protocol_contract_client_9_2_2.json \
      --check

结果：protocol evidence is current。

### 7. Python 和 Android 契约测试通过

协议基础层 Python 测试：38 项通过。

受影响 Web 抓包、积分和考勤测试：26 项通过。

Android JVM 测试和 Debug 构建：BUILD SUCCESSFUL，65 tasks。

Debug APK：astzb/app/build/outputs/apk/debug/app-debug.apk。

### 8. 报告区分捕获、命名、字段化和业务使用

证据：覆盖报告分别显示捕获总数、客户端命名数、字段数、typed/raw/unsupported、结构
漂移和无效样例。报告明确说明 captured 不等于 typed。

结果：通过。

## Manifest 哈希

| 文件 | SHA-256 |
|---|---|
| command-catalog.json | 5be3b50b42c33a0505e719b95aa022e52616243d20cb0fe753b010eb9a2c9c5d |
| field-registry.json | 885f5f008b8f7f5050f73c6c8e03b97a1063ebc3ed4675be48e06d407537646f |
| protocol coverage report | 71541dff010bd18a2dd651fe4ecb67e6cb9c01a26cbfb5686961c4913ab92de0 |
| Android contract | d6c909a0b6934dc2b272609e757cd2963044b5cd5cfb2be83ea157c4e6d58ee2 |

## 已由证据纠正的语义

- 00000067 的 [10] 是 ValWuXun，[16] 是 HeadId，[17] 是头像框数据，[26] 是
  WeekWuXun，[27] 是 TotalWuXun。
- 0000008f/143 响应项为 memberId、groupId、groupName。
- 00000834/2100 是 NOTIFY_CHAT_MSG；00000898/2200 是 NOTIFY_SEND_NOTICE，二者不
  应视为同构通知。
- 5026/5028 都使用 31 槽世界场景契约；WID 约定为 x=wid/10000、y=wid%10000。
- Score Center 的成员武勋被 registry 强制绑定到 00000067 [][10]，不得回退到战报
  武勋。

## 未完成业务解析

当前仍有 Web 82 个 raw 命令和 Android 81 个 raw 命令。这些命令已经完成 ID、客户端
名称和结构追踪，但未建立业务字段模型。后续新增业务解析必须先补 evidence，再生成
registry/Android contract，最后进入业务表和界面。

## 下一阶段入口

下一阶段是战场模型，输入已具备：

- 5026/5028 命令证据；
- 31 槽结构真实样例；
- worldChunks、serverOrderId、realMarch、deletedArmies、clearChunks 和 blockInfo
  的字段证据；
- Web/Android 同源契约和生成门禁。

下一阶段应实现完整 5026/5028 typed 模型、增量持久化、clearChunks、realMarch 和历史
回放，不再直接维护无证据的数字槽位。
