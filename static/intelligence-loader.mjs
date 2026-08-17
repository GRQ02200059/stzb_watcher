function contextKey(value) {
  return JSON.stringify(value);
}

const VERSION_MISMATCH_CODE = "INTELLIGENCE_VERSION_MISMATCH";

export function requireIntelligenceResponse(response, fallbackMessage) {
  if (!response?.ok) {
    throw new Error(
      response?.error || fallbackMessage || "战场情报加载失败",
    );
  }
  return response;
}

export function createUnversionedSceneCompatibility(view, rows) {
  return {
    kind: "scene-compatibility",
    view: String(view || ""),
    versioned: false,
    rows,
  };
}

export function assertIntelligenceAggregateVersions(summary, members = []) {
  const summaryVersion = summary?.worldStateVersion;
  if (summaryVersion === undefined || summaryVersion === null) {
    const error = new Error("WorldState 版本无效：summary 缺少版本");
    error.code = VERSION_MISMATCH_CODE;
    throw error;
  }
  for (const member of members) {
    const response = member?.response;
    if (
      response?.worldStateVersion === undefined
      || response?.worldStateVersion === null
    ) {
      const error = new Error(
        `WorldState 版本无效：${member?.name || "member"} 缺少版本`,
      );
      error.code = VERSION_MISMATCH_CODE;
      throw error;
    }
    if (String(response.worldStateVersion) !== String(summaryVersion)) {
      const error = new Error(
        `WorldState 版本不一致：summary v${summaryVersion} / `
          + `${member?.name || "member"} v${response.worldStateVersion}`,
      );
      error.code = VERSION_MISMATCH_CODE;
      throw error;
    }
  }
  return true;
}

export function createIntelligenceDetailOwner() {
  let sequence = 0;
  let current = null;

  function begin({ source, abort } = {}) {
    current?.abort?.();
    const owner = {
      token: ++sequence,
      source: String(source || "detail"),
      abort,
    };
    current = owner;
    return owner;
  }

  function isCurrent(owner) {
    return Boolean(
      current
      && owner
      && current.token === owner.token,
    );
  }

  function finish(owner) {
    if (!isCurrent(owner)) return false;
    current = null;
    return true;
  }

  function invalidate() {
    const invalidated = current;
    current = null;
    invalidated?.abort?.();
    return invalidated;
  }

  return {
    begin,
    finish,
    invalidate,
    isCurrent,
    get current() {
      return current;
    },
  };
}

export function isIntelligenceContextCurrent(snapshot, current) {
  return contextKey(snapshot) === contextKey(current);
}

export function intelligenceResultState(
  result = {},
  { hadContent = false, retry } = {},
) {
  const status = String(result.status || "success");
  const kind = status === "partial" ? "error" : status;
  const actionable = ["empty", "stale", "error", "partial"].includes(status);
  return {
    kind,
    message: String(result.message || ""),
    replace: status === "empty" ? !hadContent : false,
    busy: false,
    actionLabel: actionable ? "重试" : "",
    action: actionable ? retry : undefined,
  };
}

export function createIntelligenceLoaderCoordinator(options = {}) {
  const captureContext = options.captureContext ?? (() => ({}));
  const isContextCurrent = options.isContextCurrent ?? (() => true);
  const hasContent = options.hasContent ?? (() => false);
  const perform = options.perform ?? (async () => ({ status: "success" }));
  const commit = options.commit ?? (() => {});
  const renderState = options.renderState ?? (() => {});
  const AbortControllerClass =
    options.AbortControllerClass ?? globalThis.AbortController;
  let generation = 0;
  let ownerSequence = 0;
  let current = null;

  function load({ force = false, versionRetry = 0 } = {}) {
    const snapshot = captureContext();
    const key = contextKey(snapshot);
    if (
      !force
      && current?.key === key
      && !current.controller.signal.aborted
    ) {
      return current.promise;
    }

    current?.controller.abort();
    const requestGeneration = ++generation;
    const ownerToken = ++ownerSequence;
    const controller = new AbortControllerClass();
    const hadContent = Boolean(hasContent());
    const retry = () => load({ force: true });
    renderState({
      kind: hadContent ? "refreshing" : "loading",
      message: hadContent
        ? "正在刷新战场情报…"
        : "正在加载战场情报…",
      replace: !hadContent,
      busy: true,
      actionLabel: "",
      action: undefined,
      ownerToken,
    });

    let promise;
    promise = (async () => {
      try {
        const result = await perform({
          context: snapshot,
          signal: controller.signal,
          generation: requestGeneration,
          abort: () => controller.abort(),
        });
        if (
          requestGeneration !== generation
          || controller.signal.aborted
          || !isContextCurrent(snapshot)
        ) return null;
        commit(result, snapshot);
        renderState({
          ...intelligenceResultState(result, { hadContent, retry }),
          ownerToken,
        });
        return result;
      } catch (error) {
        const requestIsCurrent = (
          requestGeneration === generation
          && current?.promise === promise
          && isContextCurrent(snapshot)
        );
        if (
          !requestIsCurrent
          || controller.signal.aborted
          || error?.name === "AbortError"
        ) return null;
        controller.abort();
        if (
          error?.code === VERSION_MISMATCH_CODE
          && versionRetry < 1
        ) {
          return load({ force: true, versionRetry: versionRetry + 1 });
        }
        if (error?.code === VERSION_MISMATCH_CODE) {
          renderState({
            ...intelligenceResultState(
              {
                status: "stale",
                message: error.message,
              },
              { hadContent, retry },
            ),
            replace: false,
            ownerToken,
          });
          return null;
        }
        renderState({
          ...intelligenceResultState(
            {
              status: "error",
              message: error?.message || "战场情报加载失败",
            },
            { hadContent, retry },
          ),
          replace: !hadContent,
          ownerToken,
        });
        return null;
      } finally {
        if (
          requestGeneration === generation
          && current?.promise === promise
        ) {
          current = null;
        }
      }
    })();
    current = {
      key,
      promise,
      controller,
      generation: requestGeneration,
      ownerToken,
    };
    return promise;
  }

  function invalidate() {
    generation += 1;
    const invalidated = current;
    current = null;
    invalidated?.controller.abort();
    if (invalidated) {
      renderState({
        kind: "success",
        message: "",
        replace: false,
        busy: false,
        actionLabel: "",
        action: undefined,
        ownerToken: invalidated.ownerToken,
      });
    }
  }

  return {
    load,
    invalidate,
    get active() {
      return current;
    },
  };
}

if (typeof window !== "undefined") {
  window.IntelligenceLoader = Object.freeze({
    assertIntelligenceAggregateVersions,
    createIntelligenceDetailOwner,
    createIntelligenceLoaderCoordinator,
    createUnversionedSceneCompatibility,
    isIntelligenceContextCurrent,
    intelligenceResultState,
    requireIntelligenceResponse,
  });
}
