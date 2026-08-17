# Client 9.2.2 Intelligence Snapshot

This directory is a versioned, read-only subset of `/Users/bytedance/stzb` for
STZB Watcher Web intelligence features.

Copied byte-for-byte:

- `hero_table.csv`
- `skill_table.csv`
- `skill_detail_table.csv`
- `skill_effect_table.csv`

Derived from reviewed protocol/config evidence:

- `world_scene_schema.json`
- `land_intelligence_rules.json`

No packet captures, account identifiers, player/alliance data, DLLs, APK output,
or decompiled source are included. Runtime code must use this directory and must
not read the external source root.

Regenerate/check with:

```bash
.venv/bin/python scripts/sync_intelligence_snapshot.py \
  --source-root /Users/bytedance/stzb \
  --output-root data/intelligence/client-9.2.2

.venv/bin/python scripts/sync_intelligence_snapshot.py \
  --source-root /Users/bytedance/stzb \
  --output-root data/intelligence/client-9.2.2 \
  --check
```
