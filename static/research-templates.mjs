const TEMPLATE_SCHEMA_VERSION = 1;

export const TEMPLATE_STORAGE_KEY = "stzb.research.lineup-templates.v1";

const cloneJsonValue = (value) => JSON.parse(JSON.stringify(value));

const normalizeName = (name) => {
  const normalized =
    typeof name === "string"
      ? Array.from(name.trim()).slice(0, 40).join("")
      : "";
  return normalized || "未命名阵容";
};

const isIntegerInRange = (value, minimum, maximum) =>
  Number.isInteger(value) && value >= minimum && value <= maximum;

const validateLineup = (lineup) => {
  if (!lineup || typeof lineup !== "object" || Array.isArray(lineup)) {
    throw new Error("invalid lineup: expected an object");
  }
  if (lineup.schemaVersion !== TEMPLATE_SCHEMA_VERSION) {
    throw new Error(
      `unsupported lineup schema: expected ${TEMPLATE_SCHEMA_VERSION}, received ${String(lineup.schemaVersion)}`,
    );
  }
  if (typeof lineup.name !== "string") {
    throw new Error("invalid lineup name: expected a string");
  }
  if (!isIntegerInRange(lineup.morale, 0, 200)) {
    throw new Error("invalid lineup morale: expected an integer from 0 to 200");
  }
  if (!Array.isArray(lineup.heroes) || lineup.heroes.length !== 3) {
    throw new Error("invalid lineup: expected exactly 3 heroes");
  }

  lineup.heroes.forEach((hero, position) => {
    if (!hero || typeof hero !== "object" || Array.isArray(hero)) {
      throw new Error(`invalid hero at position ${position}: expected an object`);
    }
    if (hero.position !== position) {
      throw new Error(
        `invalid hero position: expected ${position}, received ${String(hero.position)}`,
      );
    }
    if (!Number.isInteger(hero.id) || hero.id <= 0) {
      throw new Error("invalid hero id: expected a positive integer");
    }
    if (!isIntegerInRange(hero.level, 1, 50)) {
      throw new Error("invalid hero level: expected an integer from 1 to 50");
    }
    if (!isIntegerInRange(hero.up, 0, 9)) {
      throw new Error("invalid hero up: expected an integer from 0 to 9");
    }
    if (
      !Array.isArray(hero.equip_skills) ||
      hero.equip_skills.length !== 2 ||
      hero.equip_skills.some(
        (skillId) => !Number.isInteger(skillId) || skillId < 0,
      )
    ) {
      throw new Error(
        "invalid hero equip_skills: expected exactly 2 non-negative integers",
      );
    }
  });

  const heroIds = lineup.heroes.map((hero) => hero.id);
  if (new Set(heroIds).size !== heroIds.length) {
    throw new Error("duplicate hero in lineup");
  }

  return cloneJsonValue(lineup);
};

const validateTemplate = (template) => {
  if (template?.schemaVersion !== TEMPLATE_SCHEMA_VERSION) {
    throw new Error(
      `unsupported schema: expected ${TEMPLATE_SCHEMA_VERSION}, received ${String(template?.schemaVersion)}`,
    );
  }

  if (!template || typeof template !== "object" || Array.isArray(template)) {
    throw new Error("invalid template: expected an object");
  }
  if (typeof template.id !== "string" || !template.id.trim()) {
    throw new Error("invalid template: id is required");
  }
  if (!Number.isFinite(template.createdAt) || !Number.isFinite(template.updatedAt)) {
    throw new Error("invalid template: createdAt and updatedAt must be numbers");
  }

  return {
    ...template,
    id: template.id.trim(),
    name: normalizeName(template.name),
    lineup: validateLineup(template.lineup),
  };
};

const parseJson = (text, context) => {
  try {
    return JSON.parse(text);
  } catch (error) {
    throw new Error(`invalid ${context} JSON: ${error.message}`, { cause: error });
  }
};

export const parseResearchTemplate = (text) =>
  validateTemplate(parseJson(text, "template"));

export const serializeResearchTemplate = (template) =>
  JSON.stringify(validateTemplate(cloneJsonValue(template)));

export const createResearchTemplateStore = (storage, options = {}) => {
  const cryptoRef = options.crypto ?? globalThis.crypto;
  const initialText = storage.getItem(TEMPLATE_STORAGE_KEY);
  let templates = [];
  let lastError = null;

  if (initialText !== null) {
    try {
      const stored = parseJson(initialText, "template storage");
      if (stored?.schemaVersion !== TEMPLATE_SCHEMA_VERSION) {
        throw new Error(
          `unsupported storage schema: expected ${TEMPLATE_SCHEMA_VERSION}, received ${String(stored?.schemaVersion)}`,
        );
      }
      if (!Array.isArray(stored.templates)) {
        throw new Error("invalid template storage: templates must be an array");
      }
      templates = stored.templates.map((template) => validateTemplate(template));
    } catch (error) {
      lastError = error;
    }
  }

  const persist = (nextTemplates = templates) => {
    storage.setItem(
      TEMPLATE_STORAGE_KEY,
      JSON.stringify({
        schemaVersion: TEMPLATE_SCHEMA_VERSION,
        templates: nextTemplates,
      }),
    );
  };

  const createFallbackId = (now) => {
    const baseId = `template-${now}`;
    if (!templates.some((template) => template.id === baseId)) {
      return baseId;
    }

    let suffix = 2;
    while (
      templates.some((template) => template.id === `${baseId}-${suffix}`)
    ) {
      suffix += 1;
    }
    return `${baseId}-${suffix}`;
  };

  return {
    get lastError() {
      return lastError;
    },
    save(name, lineup, now) {
      const template = validateTemplate({
        schemaVersion: TEMPLATE_SCHEMA_VERSION,
        id: cryptoRef?.randomUUID?.() ?? createFallbackId(now),
        name: normalizeName(name),
        createdAt: now,
        updatedAt: now,
        lineup: cloneJsonValue(lineup),
      });
      templates.push(template);
      persist();
      return cloneJsonValue(template);
    },
    load(id) {
      const template = templates.find((item) => item.id === id);
      return template ? cloneJsonValue(template) : null;
    },
    list() {
      return cloneJsonValue(templates);
    },
    rename(id, name, now) {
      const index = templates.findIndex((item) => item.id === id);
      if (index < 0) {
        return null;
      }
      templates[index] = {
        ...templates[index],
        name: normalizeName(name),
        updatedAt: now,
      };
      persist();
      return cloneJsonValue(templates[index]);
    },
    remove(id) {
      const nextTemplates = templates.filter((item) => item.id !== id);
      if (nextTemplates.length === templates.length) {
        return false;
      }
      templates = nextTemplates;
      persist();
      return true;
    },
    import(text) {
      const template = parseResearchTemplate(text);
      const existingIndex = templates.findIndex((item) => item.id === template.id);
      const nextTemplates = [...templates];
      if (existingIndex < 0) {
        nextTemplates.push(template);
      } else {
        nextTemplates[existingIndex] = template;
      }
      persist(nextTemplates);
      templates = nextTemplates;
      return cloneJsonValue(template);
    },
  };
};
