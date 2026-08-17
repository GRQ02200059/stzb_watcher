import { replayEventDetail } from "./simulator-analysis.mjs";
import { buildSimulationSkillChain } from "./research-skill-chain.mjs";
import { createResearchWorkbench } from "./research-workbench.mjs";

const fetchJson = (url) => (
  typeof window.apiFetch === "function"
    ? window.apiFetch(url)
    : fetch(url).then((response) => response.json())
);

const researchRequestOwners = new Map();
const researchRequestSurfaces = {
  detail: {
    hostId: "research-detail-status",
    surfaceId: "research-evidence-panel",
  },
  lineup: {
    hostId: "research-lineup-status",
    surfaceId: "research-stage",
  },
  matchup: {
    hostId: "research-matchup-status",
    surfaceId: "research-stage",
  },
  chain: {
    hostId: "research-chain-status",
    surfaceId: "research-stage",
  },
};

function renderRequestState(surface, model) {
  const config = researchRequestSurfaces[surface];
  if (!config) return null;
  const currentOwner = researchRequestOwners.get(surface);
  if (model.busy) {
    researchRequestOwners.set(surface, {
      token: model.ownerToken,
      busy: true,
    });
  } else if (
    currentOwner
    && currentOwner.token !== model.ownerToken
  ) {
    return null;
  } else {
    researchRequestOwners.set(surface, {
      token: model.ownerToken,
      busy: false,
    });
  }
  const target = document.getElementById(config.surfaceId);
  const targetBusy = [...researchRequestOwners].some(
    ([name, owner]) => (
      owner.busy
      && researchRequestSurfaces[name]?.surfaceId === config.surfaceId
    ),
  );
  if (targetBusy) target?.setAttribute("aria-busy", "true");
  else target?.removeAttribute("aria-busy");
  const action = typeof model.action === "function"
    ? () => Promise.resolve()
      .then(model.action)
      .catch((error) => {
        window.HudSystem?.toast({
          severity: "error",
          title: error?.message || "研究请求失败",
          dedupeKey: `research:${surface}:retry`,
        });
      })
    : undefined;
  return window.HudSystem?.renderState(
    document.getElementById(config.hostId),
    {
      kind: model.kind,
      message: model.message,
      replace: model.replace,
      actionLabel: model.actionLabel,
      action,
    },
  );
}

const workbench = createResearchWorkbench({
  documentRef: document,
  windowRef: window,
  fetchJson,
  storage: window.localStorage,
  setTimeoutFn: window.setTimeout.bind(window),
  clearTimeoutFn: window.clearTimeout.bind(window),
  nowFn: Date.now,
  renderState: window.HudSystem?.renderState,
  renderRequestState,
  buildSimulationSkillChain: (simulationResult) => (
    buildSimulationSkillChain(simulationResult, {
      replayEventDetailFn: replayEventDetail,
    })
  ),
  onSimulationEvidence: (detail) => {
    const lineupKey = detail.sourceContext.lineupKey;
    window.HudSystem?.emit({
      type: "simulation:completed",
      target: "#research-evidence-body",
      domain: "analysis",
      severity: "success",
      message: "模拟证据已回传研究工作台",
      timestamp: Date.now(),
      dedupeKey: `research-simulation:${lineupKey}`,
    });
  },
});

const openHero = async (id) => {
  if (workbench.state.libraryKind !== "hero") {
    workbench.setLibraryKind("hero");
  }
  return workbench.openHero(Number(id));
};

const openSkill = async (id) => {
  if (workbench.state.libraryKind !== "skill") {
    workbench.setLibraryKind("skill");
  }
  return workbench.openSkill(Number(id));
};

const openCardPack = async (id) => {
  if (workbench.state.libraryKind !== "card-pack") {
    workbench.setLibraryKind("card-pack");
  }
  return workbench.openCardPack(Number(id));
};

window.loadIntelligenceResearch = workbench.load;
window.ResearchCenter = {
  load: workbench.load,
  openHero,
  openSkill,
  openCardPack,
  openLineup: workbench.openLineup,
  sendToSimulator: workbench.sendToSimulator,
  sendHeroToSimulator: workbench.sendHeroToSimulator,
  setMode: workbench.setMode,
  loadTemplate: workbench.loadTemplate,
  listTemplates: workbench.listTemplates,
  saveTemplate: workbench.saveTemplate,
  renameTemplate: workbench.renameTemplate,
  deleteTemplate: workbench.deleteTemplate,
  exportTemplate: workbench.exportTemplate,
  importTemplate: workbench.importTemplate,
  openTemplateDialog: workbench.openTemplateDialog,
  state: workbench.state,
};

window.ResearchWorkbench = workbench;
document.addEventListener("DOMContentLoaded", workbench.bind);
