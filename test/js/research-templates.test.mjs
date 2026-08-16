import assert from "node:assert/strict";
import test from "node:test";

import {
  TEMPLATE_STORAGE_KEY,
  createResearchTemplateStore,
  parseResearchTemplate,
  serializeResearchTemplate,
} from "../../static/research-templates.mjs";

const lineup = (heroIds) => ({
  schemaVersion: 1,
  name: "张辽实验阵容",
  morale: 100,
  heroes: heroIds.map((id, position) => ({
    id,
    position,
    level: 50,
    up: 9,
    equip_skills: [200001 + position, 200101 + position],
  })),
});

const template = (heroIds = [100027, 100016, 100090]) => ({
  schemaVersion: 1,
  id: "template-1",
  name: "张辽实验",
  createdAt: 1000,
  updatedAt: 1000,
  lineup: lineup(heroIds),
});

const mutateTemplate = (mutate) => {
  const value = structuredClone(template());
  mutate(value);
  return value;
};

const fakeStorage = (initial = {}) => {
  const values = new Map(Object.entries(initial));
  return {
    getItem(key) {
      return values.has(key) ? values.get(key) : null;
    },
    setItem(key, value) {
      values.set(key, String(value));
    },
    removeItem(key) {
      values.delete(key);
    },
  };
};

test("template round trip preserves a complete experiment", () => {
  const original = template();

  assert.deepEqual(
    parseResearchTemplate(serializeResearchTemplate(original)),
    original,
  );
});

test("template parser rejects unsupported template schema", () => {
  assert.throws(
    () => parseResearchTemplate('{"schemaVersion":2}'),
    /unsupported schema/,
  );
});

test("template parser validates the complete lineup schema", () => {
  const invalidCases = [
    [
      "lineup schema",
      mutateTemplate((value) => {
        value.lineup.schemaVersion = 2;
      }),
      /lineup schema/,
    ],
    [
      "lineup name",
      mutateTemplate((value) => {
        value.lineup.name = 42;
      }),
      /lineup name/,
    ],
    [
      "morale lower bound",
      mutateTemplate((value) => {
        value.lineup.morale = -1;
      }),
      /morale/,
    ],
    [
      "morale upper bound",
      mutateTemplate((value) => {
        value.lineup.morale = 201;
      }),
      /morale/,
    ],
    [
      "exactly three heroes",
      mutateTemplate((value) => {
        value.lineup.heroes.pop();
      }),
      /exactly 3 heroes/,
    ],
    [
      "stable positions",
      mutateTemplate((value) => {
        value.lineup.heroes[1].position = 2;
      }),
      /position/,
    ],
    [
      "positive hero id",
      mutateTemplate((value) => {
        value.lineup.heroes[0].id = 0;
      }),
      /hero id/,
    ],
    [
      "duplicate hero",
      mutateTemplate((value) => {
        value.lineup.heroes[1].id = value.lineup.heroes[0].id;
      }),
      /duplicate hero/,
    ],
    [
      "level lower bound",
      mutateTemplate((value) => {
        value.lineup.heroes[0].level = 0;
      }),
      /level/,
    ],
    [
      "level upper bound",
      mutateTemplate((value) => {
        value.lineup.heroes[0].level = 51;
      }),
      /level/,
    ],
    [
      "up lower bound",
      mutateTemplate((value) => {
        value.lineup.heroes[0].up = -1;
      }),
      /hero up/,
    ],
    [
      "up upper bound",
      mutateTemplate((value) => {
        value.lineup.heroes[0].up = 10;
      }),
      /hero up/,
    ],
    [
      "exactly two equipment skills",
      mutateTemplate((value) => {
        value.lineup.heroes[0].equip_skills.pop();
      }),
      /equip_skills/,
    ],
    [
      "non-negative equipment skill ids",
      mutateTemplate((value) => {
        value.lineup.heroes[0].equip_skills[1] = -1;
      }),
      /equip_skills/,
    ],
  ];

  for (const [label, value, expected] of invalidCases) {
    assert.throws(
      () => parseResearchTemplate(JSON.stringify(value)),
      expected,
      label,
    );
  }
});

test("store saves renames loads and deletes deep copies", () => {
  const storage = fakeStorage();
  const store = createResearchTemplateStore(storage);
  const sourceLineup = lineup([100027, 100016, 100090]);
  const saved = store.save("张辽实验", sourceLineup, 1000);

  assert.equal(
    JSON.parse(storage.getItem(TEMPLATE_STORAGE_KEY)).schemaVersion,
    1,
  );
  sourceLineup.heroes[0].id = 1;
  saved.lineup.heroes[1].id = 2;
  const loaded = store.load(saved.id);
  loaded.lineup.heroes[2].id = 3;
  assert.deepEqual(
    store.load(saved.id).lineup.heroes.map((hero) => hero.id),
    [100027, 100016, 100090],
  );

  const renamed = store.rename(saved.id, "新版", 2000);
  renamed.name = "外部修改";
  assert.equal(store.list()[0].name, "新版");
  assert.equal(store.list()[0].updatedAt, 2000);

  assert.equal(store.remove(saved.id), true);
  assert.equal(store.remove(saved.id), false);
  assert.deepEqual(store.list(), []);
});

test("store normalizes names and uses a deterministic timestamp fallback id", () => {
  const store = createResearchTemplateStore(fakeStorage(), {
    crypto: {},
  });
  const longName = `  ${"阵".repeat(50)}  `;
  const astralName = "𠮷".repeat(50);

  const saved = store.save(longName, lineup([100027, 100016, 100090]), 1000);
  const unnamed = store.save("   ", lineup([100028, 100017, 100091]), 2000);
  const astral = store.save(astralName, lineup([100029, 100018, 100092]), 3000);

  assert.equal(saved.name, "阵".repeat(40));
  assert.equal(saved.id, "template-1000");
  assert.equal(unnamed.name, "未命名阵容");
  assert.equal(unnamed.id, "template-2000");
  assert.equal(astral.name, "𠮷".repeat(40));
  assert.equal(store.rename(saved.id, ` ${"新".repeat(50)} `, 3000).name, "新".repeat(40));
});

test("invalid template JSON is actionable and broken storage recovers empty", () => {
  assert.throws(
    () => parseResearchTemplate("{broken"),
    /invalid template JSON/,
  );

  const storage = fakeStorage({
    [TEMPLATE_STORAGE_KEY]: '{"schemaVersion":1,"templates":[',
  });
  const store = createResearchTemplateStore(storage);

  assert.deepEqual(store.list(), []);
  assert.match(store.lastError.message, /invalid template storage JSON/);
});

test("empty storage value is damaged rather than missing", () => {
  const missingStore = createResearchTemplateStore(fakeStorage());
  const emptyStore = createResearchTemplateStore(
    fakeStorage({ [TEMPLATE_STORAGE_KEY]: "" }),
  );

  assert.equal(missingStore.lastError, null);
  assert.deepEqual(emptyStore.list(), []);
  assert.ok(emptyStore.lastError);
  assert.match(emptyStore.lastError.message, /invalid template storage JSON/);
});

test("fallback ids stay unique and target one template at the same timestamp", () => {
  const store = createResearchTemplateStore(fakeStorage(), { crypto: {} });
  const first = store.save("第一队", lineup([100027, 100016, 100090]), 1000);
  const second = store.save("第二队", lineup([100028, 100017, 100091]), 1000);
  const third = store.save("第三队", lineup([100029, 100018, 100092]), 1000);

  assert.deepEqual(
    [first.id, second.id, third.id],
    ["template-1000", "template-1000-2", "template-1000-3"],
  );
  assert.equal(store.load(second.id).name, "第二队");

  store.rename(second.id, "仅改第二队", 2000);
  assert.deepEqual(
    store.list().map(({ id, name }) => [id, name]),
    [
      [first.id, "第一队"],
      [second.id, "仅改第二队"],
      [third.id, "第三队"],
    ],
  );

  assert.equal(store.remove(first.id), true);
  assert.equal(store.load(first.id), null);
  assert.equal(store.load(second.id).name, "仅改第二队");
  assert.equal(store.load(third.id).name, "第三队");
});

test("import replaces the same id only after validating the whole template", () => {
  const storage = fakeStorage();
  const store = createResearchTemplateStore(storage, { crypto: {} });
  const saved = store.save("原版", lineup([100027, 100016, 100090]), 1000);
  const second = store.save("第二版", lineup([100029, 100018, 100092]), 1500);
  const replacement = {
    ...saved,
    name: "导入版",
    updatedAt: 2000,
    lineup: lineup([100028, 100017, 100091]),
  };

  assert.throws(
    () =>
      store.import(
        JSON.stringify({
          ...replacement,
          lineup: lineup([100028, 100028, 100091]),
        }),
      ),
    /duplicate hero/,
  );
  assert.equal(store.load(saved.id).name, "原版");

  const imported = store.import(serializeResearchTemplate(replacement));
  imported.name = "外部修改";
  assert.deepEqual(
    store.list().map((template) => template.id),
    [saved.id, second.id],
  );
  assert.equal(store.load(saved.id).name, "导入版");
  assert.deepEqual(
    JSON.parse(storage.getItem(TEMPLATE_STORAGE_KEY)).templates,
    [replacement, second],
  );
});
