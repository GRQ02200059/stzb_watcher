function shouldEmitRiskEvent({
  risk,
  selectedWid,
  wid,
  eventKey,
  lastEventKey,
} = {}) {
  return (
    risk?.level === 'high'
    && Number(selectedWid) === Number(wid)
    && Boolean(eventKey)
    && eventKey !== lastEventKey
  );
}

if (typeof module !== 'undefined' && module.exports) {
  module.exports = {shouldEmitRiskEvent};
}

(function () {
  'use strict';

  if (typeof window === 'undefined' || typeof document === 'undefined') return;

  const DEFAULT_BOUNDS = {rowUp: 1, rowDown: 20, colLeft: 1, colRight: 20};
  const FAVORITES_KEY = 'stzb.intelligence.favorites';
  const MODE_LABELS = {far: '全域热区', middle: '战区轮廓', near: '战术镜头'};
  const state = {
    bounds: {...DEFAULT_BOUNDS},
    summary: null,
    activeView: 'map',
    mode: 'near',
    tiles: [],
    buckets: [],
    radarBuckets: [],
    risks: new Map(),
    selectedWid: 0,
    detail: null,
    events: [],
    render: null,
    radarRender: null,
    drag: null,
    suppressMapClick: false,
    radarDrag: null,
    reloadTimer: null,
    streamReloadTimer: null,
    streamDirty: false,
    frame: null,
    aggregateBusyOwner: 0,
    detailLoading: false,
    navigationRevision: 0,
    navigationController: null,
    history: null,
    lastEmittedRiskKey: '',
    initialized: false,
    layerRevision: 0,
    sceneRows: {march: null, army: null, entity: null},
    sceneCompatibility: {march: null, army: null, entity: null},
    favorites: new Set(readFavorites()),
    layers: {ownership: true, freshness: true, paths: true, armies: true}
  };
  let aggregateLoader = null;
  let detailOwner = null;

  function getDetailOwner() {
    detailOwner ||= window.IntelligenceLoader
      ?.createIntelligenceDetailOwner?.();
    return detailOwner;
  }

  async function loadIntelligenceCenter(force) {
    if (!modulesReady()) {
      showState('地图模块加载中…');
      setTimeout(() => loadIntelligenceCenter(force), 80);
      return null;
    }
    aggregateLoader ||= createAggregateLoader();
    return aggregateLoader.load({force: Boolean(force)});
  }

  function createAggregateLoader() {
    return window.IntelligenceLoader.createIntelligenceLoaderCoordinator({
      captureContext: aggregateContext,
      isContextCurrent: isAggregateContextCurrent,
      hasContent: hasAggregateContent,
      perform: performAggregateLoad,
      commit: commitAggregateLoad,
      renderState: renderAggregateState,
      AbortControllerClass: AbortController,
    });
  }

  function aggregateContext() {
    return {
      activeView: state.activeView,
      bounds: {...state.bounds},
      selectedWid: Number(state.selectedWid) || 0,
      initialized: state.initialized,
    };
  }

  function isAggregateContextCurrent(snapshot) {
    return window.IntelligenceLoader.isIntelligenceContextCurrent(
      snapshot,
      aggregateContext(),
    );
  }

  function hasAggregateContent() {
    if (state.activeView === 'map') {
      return Boolean(
        state.summary
        && (state.tiles.length || state.buckets.length || state.radarBuckets.length)
      );
    }
    return Array.isArray(state.sceneRows[state.activeView]);
  }

  async function performAggregateLoad({context, signal, abort}) {
    const summary = await fetchJson(
      '/api/intelligence/world/summary',
      signal,
    );
    if (!summary?.ok) throw new Error(summary?.error || '世界状态尚未初始化');
    assertAggregateVersions(summary, []);
    if (!summary.dataBounds) {
      return {
        status: 'empty',
        message: '等待 5026 基线或已知地块数据',
        activeView: context.activeView,
        summary,
        events: [],
        navigation: null,
      };
    }
    const navigation = navigationForSummary(summary, context);
    const eventsPromise = fetchJson(
      '/api/intelligence/world/events?limit=8',
      signal,
    ).then(response => requireResponse(response, '世界事件加载失败'));
    if (context.activeView !== 'map') {
      const endpoint = {
        march: '/api/world/marches',
        army: '/api/world/armies',
        entity: '/api/world/entities',
      }[context.activeView];
      const [scene, events] = await Promise.all([
        fetchJson(endpoint, signal)
          .then(response => requireResponse(response, '场景情报加载失败')),
        eventsPromise,
      ]);
      assertAggregateVersions(summary, [
        {name: 'events', response: events},
      ]);
      const rows = context.activeView === 'march'
        ? (scene?.marches || [])
        : context.activeView === 'army'
          ? (scene?.armies || [])
          : (scene?.entities || []);
      const sceneCompatibility = window.IntelligenceLoader
        .createUnversionedSceneCompatibility(context.activeView, rows);
      return {
        status: summary.freshness === 'stale'
          ? 'stale'
          : rows.length ? 'success' : 'empty',
        message: summary.freshness === 'stale'
          ? '后端 WorldState 真值已陈旧，当前结果仅供核对'
          : rows.length ? '' : sceneEmptyMessage(context.activeView),
        activeView: context.activeView,
        summary,
        navigation,
        sceneCompatibility,
        events: events?.events || [],
      };
    }
    return performMapAggregate({
      context,
      navigation,
      summary,
      eventsPromise,
      signal,
      abort,
    });
  }

  function navigationForSummary(summary, context) {
    if (context.initialized) {
      return {
        bounds: {...context.bounds},
        selectedWid: Number(context.selectedWid) || 0,
        initialize: false,
      };
    }
    const fallback = mapState(
      summary.dataBounds,
      summary.focusWid,
      state.layers,
    );
    const restored = window.IntelligenceMapNavigation.parseMapState(
      location.hash,
      fallback
    );
    const initial = window.IntelligenceMapNavigation.isBoundsUseful(
      restored.bounds,
      summary.dataBounds
    ) ? restored : fallback;
    return {
      bounds: {...initial.bounds},
      selectedWid: Number(initial.selectedWid) || 0,
      layers: {...state.layers, ...initial.layers},
      layerRevision: state.layerRevision,
      initialize: true,
    };
  }

  async function performMapAggregate({
    navigation,
    summary,
    eventsPromise,
    signal,
    abort,
  }) {
    const mode = chooseMode(navigation.bounds);
    const mainPromise = mode === 'near'
      ? loadNearViewport(navigation.bounds, signal)
      : loadOverviewBuckets(navigation.bounds, signal);
    const radarPromise = loadRadarBuckets(summary.dataBounds, signal);
    const aggregateDetailOwner = navigation.selectedWid
      ? beginDetailRequest('aggregate', abort)
      : null;
    const detailPromise = navigation.selectedWid
      ? fetchJson(
        `/api/intelligence/world/tile/${navigation.selectedWid}`,
        signal,
      ).then(response => requireResponse(response, '地块详情加载失败'))
      : Promise.resolve(null);
    try {
      const [main, radar, events, detail] = await Promise.all([
        mainPromise,
        radarPromise,
        eventsPromise,
        detailPromise,
      ]);
      assertAggregateVersions(summary, [
        ...(main.versionedResponses || []),
        {name: 'radar', response: radar.response},
        {name: 'events', response: events},
        ...(detail ? [{name: 'detail', response: detail}] : []),
      ]);
      return {
        status: summary.freshness === 'stale' ? 'stale' : 'success',
        message: summary.freshness === 'stale'
          ? '后端 WorldState 真值已陈旧，地图保留最后观测证据'
          : '',
        activeView: 'map',
        summary,
        navigation,
        mode,
        ...main,
        radarBuckets: radar.buckets,
        events: events?.events || [],
        detail,
      };
    } finally {
      finishDetailRequest(aggregateDetailOwner);
    }
  }

  function chooseMode(bounds = state.bounds) {
    const canvas = document.getElementById('intel-map-canvas');
    return window.IntelligenceMapOverview.semanticMapMode(
      bounds,
      canvas?.clientWidth || 800,
      canvas?.clientHeight || 560
    );
  }

  async function loadNearViewport(bounds, signal) {
    const query = boundsQuery(bounds);
    const [viewport, risks] = await Promise.all([
      fetchJson(`/api/intelligence/world/viewport?${query}`, signal)
        .then(response => requireResponse(response, '视窗加载失败')),
      fetchJson(`/api/intelligence/world/risks?${query}`, signal)
        .then(response => requireResponse(response, '风险加载失败'))
    ]);
    const riskMap = new Map(
      (risks.risks || []).map(item => [Number(item.wid), item]),
    );
    const tiles = (viewport.tiles || []).map(tile => {
      const risk = riskMap.get(Number(tile.wid));
      return {
        ...tile,
        relation: risk?.ownership?.relation || 'unknown',
        armies: risk?.incomingArmyRelations?.enemy || 0
      };
    });
    return {
      tiles,
      buckets: [],
      risks: riskMap,
      versionedResponses: [
        {name: 'viewport', response: viewport},
        {name: 'risks', response: risks},
      ],
    };
  }

  async function loadOverviewBuckets(bounds, signal) {
    const canvas = document.getElementById('intel-map-canvas');
    const grid = window.IntelligenceMapOverview.bucketGridForBounds(
      bounds,
      canvas?.clientWidth || 800,
      canvas?.clientHeight || 560
    );
    const query = `${boundsQuery(bounds)}&bucketRows=${grid.bucketRows}&bucketCols=${grid.bucketCols}`;
    const overview = requireResponse(
      await fetchJson(`/api/intelligence/world/overview?${query}`, signal),
      '热区加载失败',
    );
    return {
      buckets: overview.buckets || [],
      tiles: [],
      risks: new Map(),
      versionedResponses: [
        {name: 'overview', response: overview},
      ],
    };
  }

  async function loadRadarBuckets(bounds, signal) {
    const grid = window.IntelligenceMapOverview.bucketGridForBounds(
      bounds,
      190,
      126,
      600
    );
    const query = `${boundsQuery(bounds)}&bucketRows=${grid.bucketRows}&bucketCols=${grid.bucketCols}`;
    const overview = requireResponse(
      await fetchJson(`/api/intelligence/world/overview?${query}`, signal),
      '全域雷达加载失败',
    );
    return {
      buckets: overview.buckets || [],
      response: overview,
    };
  }

  function assertAggregateVersions(summary, members) {
    return window.IntelligenceLoader.assertIntelligenceAggregateVersions(
      summary,
      members,
    );
  }

  function requireResponse(response, fallbackMessage) {
    return window.IntelligenceLoader.requireIntelligenceResponse(
      response,
      fallbackMessage,
    );
  }

  function commitAggregateLoad(result) {
    state.summary = result.summary;
    state.events = result.events || [];
    if (result.navigation) {
      state.bounds = {...result.navigation.bounds};
      state.selectedWid = Number(result.navigation.selectedWid) || 0;
      if (
        result.navigation.initialize
        && result.navigation.layerRevision === state.layerRevision
      ) {
        state.layers = {...result.navigation.layers};
      }
      if (result.navigation.initialize) {
        state.history = window.IntelligenceMapNavigation
          .createViewportHistory(currentMapState(), 30);
        state.initialized = true;
      }
    }
    if (!result.summary?.dataBounds) {
      state.tiles = [];
      state.buckets = [];
      state.radarBuckets = [];
      state.risks = new Map();
      state.selectedWid = 0;
      state.detail = null;
      renderEmpty();
      renderTimeline();
      updateMeta();
      renderDetail('risk');
      resetDetailHeading();
      renderDetailLoadState('empty', '选择地块查看情报', true);
      return;
    }
    if (result.activeView !== 'map') {
      state.sceneCompatibility[result.activeView] = result.sceneCompatibility;
      state.sceneRows[result.activeView] = result.sceneCompatibility?.rows || [];
      renderSceneRows(
        result.activeView,
        result.sceneCompatibility?.rows || [],
      );
      renderTimeline();
      updateMeta();
      return;
    }
    state.mode = result.mode;
    state.tiles = result.tiles || [];
    state.buckets = result.buckets || [];
    state.radarBuckets = result.radarBuckets || [];
    state.risks = result.risks || new Map();
    state.detail = result.detail?.ok ? result.detail : null;
    renderMainMap();
    renderRadar();
    renderTimeline();
    updateMeta();
    updateNavigationControls();
    renderDetail(state.detail ? 'risk' : 'risk');
    renderDetailLoadState(state.detail ? 'success' : 'empty');
    persistCurrentState();
  }

  function renderAggregateState(model) {
    const ownerToken = Number(model.ownerToken) || 0;
    if (model.busy) {
      state.aggregateBusyOwner = ownerToken;
    } else if (
      state.aggregateBusyOwner
      && state.aggregateBusyOwner !== ownerToken
    ) {
      return null;
    } else {
      state.aggregateBusyOwner = 0;
    }
    const view = state.activeView;
    const panel = document.getElementById(`intel-view-${view}`);
    const host = document.getElementById(
      view === 'map'
        ? 'intel-loader-state'
        : `intel-scene-${view}-status`,
    );
    if (model.busy) panel?.setAttribute('aria-busy', 'true');
    else panel?.removeAttribute('aria-busy');
    if (view === 'map' && model.kind === 'error') {
      showState(`情报加载失败：${model.message}`, true);
    }
    return window.HudSystem?.renderState(host, model);
  }

  function renderSceneRows(view, rows) {
    if (view === 'march') window.WorldScenePanel?.renderMarches(rows);
    else if (view === 'army') window.WorldScenePanel?.renderArmies(rows);
    else window.WorldScenePanel?.renderEntities(rows);
  }

  function loadSceneCompatibility(view, force = false) {
    const normalized = ['map', 'march', 'army', 'entity'].includes(view)
      ? view
      : 'march';
    if (state.activeView !== normalized) return openView(normalized);
    return loadIntelligenceCenter(force);
  }

  function sceneEmptyMessage(view) {
    return ({
      march: '暂无实时行军，可刷新等待新的 WorldState 增量',
      army: '暂无活跃部队，可刷新等待新的 WorldState 增量',
      entity: '暂无协议实体，可刷新等待新的 WorldState 增量',
    })[view] || '暂无情报数据';
  }

  function renderMainMap() {
    const canvas = document.getElementById('intel-map-canvas');
    if (!canvas) return;
    if (state.mode === 'near') {
      const selected = state.selectedWid;
      const tiles = state.tiles.map(tile => ({
        ...tile,
        selected: Number(tile.wid) === selected,
        favorite: state.favorites.has(Number(tile.wid)),
        risk: state.risks.get(Number(tile.wid))
      }));
      state.render = window.IntelligenceMap.drawIntelligenceMap(
        canvas,
        tiles,
        {bounds: state.bounds, layers: state.layers}
      );
    } else {
      state.render = window.IntelligenceMapOverview.drawOverviewMap(
        canvas,
        state.buckets,
        {bounds: state.bounds, mode: state.mode}
      );
    }
    updateSelectedRiskLock();
    showState(`视窗 ${formatBounds(state.bounds)} · ${MODE_LABELS[state.mode]}`);
  }

  function renderRadar() {
    const canvas = document.getElementById('intel-radar-canvas');
    if (!canvas) return;
    if (!state.summary?.dataBounds) {
      canvas.getContext('2d')?.clearRect(0, 0, canvas.width, canvas.height);
      state.radarRender = null;
      return;
    }
    state.radarRender = window.IntelligenceMapOverview.drawRadar(
      canvas,
      state.radarBuckets,
      {
        dataBounds: state.summary.dataBounds,
        viewBounds: state.bounds,
        selectedWid: state.selectedWid
      }
    );
  }

  async function openView(view) {
    const normalized = ['map', 'march', 'army', 'entity'].includes(view)
      ? view
      : 'map';
    aggregateLoader?.invalidate();
    if (normalized !== 'map') invalidateDetailRequest();
    state.activeView = normalized;
    document.querySelectorAll('[data-intel-view]').forEach(button => {
      const active = button.dataset.intelView === normalized;
      button.classList.toggle('active', active);
      button.setAttribute('aria-selected', String(active));
    });
    document.querySelectorAll('.intel-primary-view').forEach(panel => {
      const active = panel.id === `intel-view-${normalized}`;
      panel.classList.toggle('active', active);
      panel.hidden = !active;
    });
    return loadIntelligenceCenter();
  }

  function renderEmpty() {
    state.tiles = [];
    state.buckets = [];
    renderMainMap();
    renderRadar();
  }

  async function selectWid(wid, rerender = true) {
    const requestedWid = Number(wid) || 0;
    aggregateLoader?.invalidate();
    const changed = requestedWid !== Number(state.selectedWid);
    if (changed) clearDetail();
    const controller = new AbortController();
    const owner = beginDetailRequest(
      'standalone',
      () => controller.abort(),
    );
    state.selectedWid = requestedWid;
    if (rerender) {
      renderMainMap();
      renderRadar();
    }
    const hadDetail = Boolean(state.detail);
    state.detailLoading = true;
    renderDetailLoadState(hadDetail ? 'refreshing' : 'loading');
    try {
      const data = await fetchJson(
        `/api/intelligence/world/tile/${requestedWid}`,
        controller.signal,
      );
      if (
        !getDetailOwner()?.isCurrent(owner)
        || Number(requestedWid) !== Number(state.selectedWid)
      ) return null;
      state.detail = data?.ok ? data : null;
      renderDetail('risk');
      updateSelectedRiskLock();
      renderDetailLoadState(state.detail ? 'success' : 'empty');
      return state.detail;
    } catch (error) {
      if (error?.name === 'AbortError') return null;
      if (
        !getDetailOwner()?.isCurrent(owner)
        || Number(requestedWid) !== Number(state.selectedWid)
      ) return null;
      renderDetailLoadState(
        'error',
        error?.message || '地块详情加载失败',
        !hadDetail,
      );
      return null;
    } finally {
      if (finishDetailRequest(owner)) {
        state.detailLoading = false;
      }
    }
  }

  function invalidateDetailRequest({clear = false} = {}) {
    const invalidated = getDetailOwner()?.invalidate();
    state.detailLoading = false;
    document.getElementById('intel-detail-panel')?.removeAttribute('aria-busy');
    if (clear) clearDetail();
    return invalidated?.token || 0;
  }

  function beginDetailRequest(source, abort) {
    const owner = getDetailOwner()?.begin({source, abort});
    state.detailLoading = true;
    document.getElementById('intel-detail-panel')?.setAttribute('aria-busy', 'true');
    return owner;
  }

  function finishDetailRequest(owner) {
    if (!getDetailOwner()?.finish(owner)) return false;
    state.detailLoading = false;
    document.getElementById('intel-detail-panel')?.removeAttribute('aria-busy');
    return true;
  }

  function clearDetail() {
    state.detail = null;
    renderDetail('risk');
    resetDetailHeading();
    renderDetailLoadState('empty', '选择地块查看情报', true);
  }

  function renderDetailLoadState(kind, message, replace) {
    const panel = document.getElementById('intel-detail-panel');
    const host = document.getElementById('intel-detail-status');
    if (['loading', 'refreshing'].includes(kind)) {
      panel?.setAttribute('aria-busy', 'true');
    } else {
      panel?.removeAttribute('aria-busy');
    }
    return window.HudSystem?.renderState(host, {
      kind,
      message,
      replace,
      actionLabel: kind === 'error' ? '重试' : '',
      action: kind === 'error'
        ? () => selectWid(state.selectedWid, false)
        : undefined,
    });
  }

  function updateSelectedRiskLock() {
    const lock = document.getElementById('intel-risk-lock');
    const wid = Number(state.selectedWid) || 0;
    const risk = state.risks.get(wid) || (
      Number(state.detail?.tile?.wid) === wid ? state.detail?.risk : null
    );
    const hitArea = state.mode === 'near'
      ? state.render?.hitAreas?.find(item => Number(item.wid) === wid)
      : null;
    const riskKey = risk ? `risk:${wid}:${risk.level}` : '';
    const selectedHighRisk = shouldEmitRiskEvent({
      risk,
      selectedWid: state.selectedWid,
      wid,
      eventKey: riskKey,
      lastEventKey: '',
    });
    const activeRiskKey = selectedHighRisk ? riskKey : '';
    if (
      state.lastEmittedRiskKey
      && state.lastEmittedRiskKey !== activeRiskKey
    ) {
      window.HudSystem?.resolveEvent(state.lastEmittedRiskKey);
      state.lastEmittedRiskKey = '';
    }
    const locked = selectedHighRisk && Boolean(hitArea);
    lock?.classList.toggle('intel-risk-lock', locked);
    if (lock) {
      lock.hidden = !locked;
      if (locked) {
        lock.style.left = `${hitArea.x}px`;
        lock.style.top = `${hitArea.y}px`;
        lock.style.width = `${hitArea.size}px`;
        lock.style.height = `${hitArea.size}px`;
      }
    }
    if (!shouldEmitRiskEvent({
      risk,
      selectedWid: state.selectedWid,
      wid,
      eventKey: riskKey,
      lastEventKey: state.lastEmittedRiskKey,
    })) return;
    state.lastEmittedRiskKey = riskKey;
    window.HudSystem?.emit({
      type: 'intelligence:risk-detected',
      target: '#intel-detail-panel',
      domain: 'intelligence',
      severity: 'critical',
      message: `WID ${wid} 风险 ${risk.score}`,
      timestamp: Date.now(),
      dedupeKey: `risk:${wid}:${risk.level}`,
      cooldownMs: 10_000,
    });
  }

  function renderDetail(tab) {
    document.querySelectorAll('.intel-detail-tabs button').forEach(button => {
      button.classList.toggle('active', button.dataset.intelTab === tab);
    });
    const body = document.getElementById('intel-detail-body');
    if (!state.detail) {
      body.innerHTML = '<div class="intel-empty">选择热区或真实地块查看情报</div>';
      return;
    }
    const data = state.detail;
    const tile = data.tile;
    const risk = data.risk || {};
    const armies = data.incomingArmies || [];
    document.getElementById('intel-detail-title').textContent =
      `WID ${tile.wid} · ${tile.landLevel ? `土地 Lv.${tile.landLevel}` : (tile.name || '未知地块')}`;
    document.getElementById('intel-risk-pill').textContent =
      `风险 ${risk.score || 0} · ${risk.level || 'unknown'}`;
    if (tab === 'risk') {
      body.innerHTML = card(
        '风险解释',
        Object.entries(risk.components || {}).map(([key, value]) => row(key, value)).join('')
        + row('归属关系', risk.ownership?.relation || 'unknown')
        + row('置信度', `${Math.round((risk.confidence || 0) * 100)}%`)
        + row('未知分量', (risk.unknownComponents || []).join(', ') || '无')
      ) + card(
        '地块状态',
        row('名称', tile.name || '-')
        + row('等级', tile.landLevel ?? '未知')
        + row('资源种类', tile.resourceKind ?? '未知')
        + row('情报', tile.freshness)
        + `<button class="btn ${state.favorites.has(Number(tile.wid)) ? 'btn-primary' : ''}" type="button" data-intel-action="toggle-favorite">${state.favorites.has(Number(tile.wid)) ? '取消关注' : '关注地块'}</button>`
      );
    } else if (tab === 'armies') {
      body.innerHTML = armies.length
        ? armies.map(army => card(
          `部队 ${army.army_id}`,
          row('玩家', army.owner_name || army.user_id)
          + row('同盟', army.owner_union_name || '-')
          + row('目标 WID', army.wid_to)
          + row('到达', fmtTime(army.end_time))
        )).join('')
        : '<div class="intel-empty">暂无关联行军</div>';
    } else if (tab === 'battles') {
      renderBattleStats(body, data.battleStats || {});
    } else {
      body.innerHTML = card(
        '状态证据',
        row('WorldState', data.worldStateVersion)
        + row('完整性', data.completeness)
        + row('新鲜度', data.freshness)
        + row('最后基线', data.latestBaseline?.latest_baseline_order_id ?? '-')
        + row('最近增量', data.latestDelta?.version ?? '-')
      );
    }
  }

  function resetDetailHeading() {
    const title = document.getElementById('intel-detail-title');
    const pill = document.getElementById('intel-risk-pill');
    if (title) title.textContent = '未选择地块';
    if (pill) pill.textContent = '风险 --';
  }

  function renderBattleStats(body, stats) {
    const recent = stats.recentBattles || [];
    const lineups = stats.commonLineups || [];
    body.innerHTML =
      '<div class="research-section-title"><div><span class="evidence-badge evidence-stat">BATTLE STAT</span><h3>地块历史战报</h3></div></div>'
      + card(
        '历史样本',
        row('总样本', stats.sampleSize || 0)
        + row('攻方胜率', `${stats.attackWinRate || 0}%`)
        + row('胜 / 平 / 负', `${stats.attackWins || 0} / ${stats.attackDraws || 0} / ${stats.attackLosses || 0}`)
      )
      + card(
        '常见攻方阵容',
        lineups.length
          ? lineups.map((item, index) => `<button class="intel-lineup-link" type="button" data-intel-action="open-lineup" data-lineup-index="${index}"><b>${esc((item.names || []).join(' / ') || item.key)}</b><small>${item.sampleSize} 场</small></button>`).join('')
          : '<div class="intel-empty">暂无完整三人阵容</div>'
      )
      + card(
        '最近战报',
        recent.length
          ? recent.map(item => `<div class="intel-battle-row"><b>#${item.battle_id}</b><span>${esc(item.atk_name || '-')} → ${esc(item.def_name || '-')}</span></div>`).join('')
          : '<div class="intel-empty">暂无战报</div>'
      )
      + '<button class="btn" type="button" data-intel-action="open-battles">筛选该 WID 全部战报</button>';
  }

  function focusBucket(bucket) {
    if (!bucket) return;
    const wid = Number(bucket.focusWid || 0);
    const row = wid ? Math.floor(wid / 10000) : Math.round((bucket.rowUp + bucket.rowDown) / 2);
    const col = wid ? wid % 10000 : Math.round((bucket.colLeft + bucket.colRight) / 2);
    navigateTo(centeredBounds(row, col, 20), wid, true);
  }

  function moveFromRadar(point, commit = true) {
    if (!state.radarRender?.radarRect || !state.summary?.dataBounds) return;
    const world = window.IntelligenceMapOverview.radarToWorld(
      point.x,
      point.y,
      state.summary.dataBounds,
      state.radarRender.radarRect
    );
    const rows = state.bounds.rowDown - state.bounds.rowUp + 1;
    const cols = state.bounds.colRight - state.bounds.colLeft + 1;
    state.bounds = centeredBounds(world.row, world.col, Math.max(rows, cols));
    renderMainMap();
    renderRadar();
    if (commit) commitNavigation(true);
  }

  function navigateTo(bounds, selectedWid = state.selectedWid, push = true) {
    const nextSelectedWid = Number(selectedWid || 0);
    if (nextSelectedWid !== Number(state.selectedWid)) {
      invalidateDetailRequest({clear: true});
    }
    aggregateLoader?.invalidate();
    state.bounds = {...bounds};
    state.selectedWid = nextSelectedWid;
    if (push) commitNavigation(false);
    animateOrLoad();
  }

  function commitNavigation(load = true) {
    const next = currentMapState();
    state.history?.push(next);
    persistCurrentState();
    updateNavigationControls();
    if (load) loadIntelligenceCenter();
  }

  function animateOrLoad() {
    if (matchMedia('(prefers-reduced-motion: reduce)').matches) {
      loadIntelligenceCenter();
      return;
    }
    requestAnimationFrame(() => {
      renderMainMap();
      renderRadar();
      loadIntelligenceCenter();
    });
  }

  function goHome() {
    if (!state.summary?.suggestedBounds) return;
    navigateTo(state.summary.suggestedBounds, state.summary.focusWid, true);
  }

  function goBack() {
    if (!state.history?.canBack()) return;
    applyHistoryState(state.history.back());
  }

  function goForward() {
    if (!state.history?.canForward()) return;
    applyHistoryState(state.history.forward());
  }

  function applyHistoryState(next) {
    aggregateLoader?.invalidate();
    if (Number(next.selectedWid) !== Number(state.selectedWid)) {
      invalidateDetailRequest({clear: true});
    }
    state.bounds = {...next.bounds};
    state.selectedWid = next.selectedWid;
    state.layers = {...state.layers, ...next.layers};
    persistCurrentState();
    updateNavigationControls();
    animateOrLoad();
  }

  function zoomIn() {
    zoomMap(-1);
  }

  function zoomOut() {
    zoomMap(1);
  }

  function zoomMap(direction) {
    const center = boundsCenter(state.bounds);
    state.bounds = window.IntelligenceMap.zoomBounds(
      state.bounds,
      center.row,
      center.col,
      direction
    );
    commitNavigation(true);
  }

  async function locateWid(value) {
    const input = document.getElementById('intel-wid-input');
    const wid = Number(value ?? input?.value);
    if (!Number.isInteger(wid) || wid <= 0) {
      showState('请输入有效 WID', true);
      return;
    }
    const row = Math.floor(wid / 10000);
    const col = wid % 10000;
    const revision = ++state.navigationRevision;
    state.navigationController?.abort();
    const controller = new AbortController();
    state.navigationController = controller;
    try {
      const detail = await fetchJson(
        `/api/intelligence/world/tile/${wid}`,
        controller.signal,
      );
      if (
        revision !== state.navigationRevision
        || state.navigationController !== controller
      ) return null;
      if (!detail?.ok) {
        showState(`WID ${wid} 不在当前已知地块中`, true);
        return null;
      }
      if (input) input.value = String(wid);
      navigateTo(centeredBounds(row, col, 20), wid, true);
      return detail;
    } catch (error) {
      if (error?.name !== 'AbortError' && revision === state.navigationRevision) {
        showState(`WID ${wid} 定位失败：${error.message}`, true);
      }
      return null;
    } finally {
      if (state.navigationController === controller) {
        state.navigationController = null;
      }
    }
  }

  function renderTimeline() {
    const element = document.getElementById('intel-timeline');
    element.innerHTML = state.events.length
      ? state.events.map((event, index) => `<button class="intel-event" data-event-index="${index}"><strong>${esc(event.event_type)}</strong>v${event.state_version} · ${new Date(event.observed_at_ms).toLocaleTimeString('zh-CN', {hour12: false})}</button>`).join('')
      : '<div class="intel-empty">暂无 WorldState 变化事件</div>';
  }

  function updateMeta() {
    document.getElementById('intel-map-mode').textContent = MODE_LABELS[state.mode];
    document.getElementById('intel-state-meta').textContent =
      `WorldState v${state.summary.worldStateVersion} · ${state.summary.freshness} · ${state.summary.completeness}`;
    const freshness = document.getElementById('hud-world-freshness');
    if (freshness) {
      const status = state.summary.freshness === 'fresh' ? 'live' : 'degraded';
      freshness.textContent =
        `${String(state.summary.freshness || 'unknown').toUpperCase()} · v${state.summary.worldStateVersion}`;
      freshness.dataset.status = status;
    }
  }

  function updateNavigationControls() {
    document.getElementById('intel-map-back').disabled = !state.history?.canBack();
    document.getElementById('intel-map-forward').disabled = !state.history?.canForward();
  }

  function persistCurrentState() {
    const hash = window.IntelligenceMapNavigation.serializeMapState(currentMapState());
    history.replaceState(null, '', hash);
  }

  function currentMapState() {
    return {
      bounds: {...state.bounds},
      selectedWid: state.selectedWid,
      layers: {...state.layers}
    };
  }

  function mapState(bounds, selectedWid, layers = state.layers) {
    return {
      bounds: {...bounds},
      selectedWid: Number(selectedWid || 0),
      layers: {...layers}
    };
  }

  function boundsEqual(left, right) {
    return (
      Number(left?.rowUp) === Number(right?.rowUp)
      && Number(left?.rowDown) === Number(right?.rowDown)
      && Number(left?.colLeft) === Number(right?.colLeft)
      && Number(left?.colRight) === Number(right?.colRight)
    );
  }

  function scheduleReload(commit = true) {
    aggregateLoader?.invalidate();
    clearTimeout(state.reloadTimer);
    state.reloadTimer = setTimeout(() => {
      if (commit) commitNavigation(false);
      loadIntelligenceCenter();
    }, 140);
  }

  function scheduleRender() {
    if (state.frame) return;
    state.frame = requestAnimationFrame(() => {
      state.frame = null;
      renderMainMap();
      renderRadar();
    });
  }

  function bindMainCanvas(canvas) {
    canvas.addEventListener('click', event => {
      if (state.suppressMapClick) {
        state.suppressMapClick = false;
        return;
      }
      if (state.drag?.moved) return;
      const point = canvasPoint(canvas, event);
      if (state.mode === 'near') {
        const wid = window.IntelligenceMap.hitTestMap(point.x, point.y, state.render);
        if (!wid) return;
        if (event.shiftKey) toggleFavorite(wid);
        else selectWid(wid);
      } else {
        const bucket = window.IntelligenceMapOverview.hitTestBucket(
          point.x,
          point.y,
          state.render
        );
        if (bucket) focusBucket(bucket);
      }
    });
    canvas.addEventListener('dblclick', event => {
      if (state.mode === 'near') return;
      const point = canvasPoint(canvas, event);
      focusBucket(window.IntelligenceMapOverview.hitTestBucket(
        point.x,
        point.y,
        state.render
      ));
    });
    canvas.addEventListener('wheel', event => {
      event.preventDefault();
      const point = canvasPoint(canvas, event);
      const rows = state.bounds.rowDown - state.bounds.rowUp + 1;
      const cols = state.bounds.colRight - state.bounds.colLeft + 1;
      const row = state.bounds.rowUp + (point.y / canvas.clientHeight) * rows;
      const col = state.bounds.colLeft + (point.x / canvas.clientWidth) * cols;
      state.bounds = window.IntelligenceMap.zoomBounds(
        state.bounds,
        row,
        col,
        event.deltaY
      );
      scheduleRender();
      scheduleReload(true);
    }, {passive: false});
    canvas.addEventListener('pointerdown', event => {
      state.drag = {
        x: event.clientX,
        y: event.clientY,
        bounds: {...state.bounds},
        moved: false
      };
      canvas.setPointerCapture(event.pointerId);
    });
    canvas.addEventListener('pointermove', event => {
      if (!state.drag) return;
      const rows = state.drag.bounds.rowDown - state.drag.bounds.rowUp + 1;
      const cols = state.drag.bounds.colRight - state.drag.bounds.colLeft + 1;
      const dx = event.clientX - state.drag.x;
      const dy = event.clientY - state.drag.y;
      if (Math.abs(dx) + Math.abs(dy) > 5 && !state.drag.moved) {
        state.drag.moved = true;
        aggregateLoader?.invalidate();
      }
      state.bounds = window.IntelligenceMap.panBounds(
        state.drag.bounds,
        -(dy / canvas.clientHeight) * rows,
        -(dx / canvas.clientWidth) * cols
      );
      scheduleRender();
    });
    canvas.addEventListener('pointerup', event => {
      finishMainDrag(canvas, event, true);
    });
    canvas.addEventListener('pointercancel', event => {
      finishMainDrag(canvas, event, false);
    });
    canvas.addEventListener('lostpointercapture', event => {
      finishMainDrag(canvas, event, false);
    });
  }

  function bindRadar(canvas) {
    canvas.addEventListener('click', event => {
      moveFromRadar(canvasPoint(canvas, event), true);
    });
    canvas.addEventListener('dblclick', event => {
      const bucket = window.IntelligenceMapOverview.hitTestBucket(
        canvasPoint(canvas, event).x,
        canvasPoint(canvas, event).y,
        state.radarRender
      );
      if (bucket) focusBucket(bucket);
    });
    canvas.addEventListener('pointerdown', event => {
      aggregateLoader?.invalidate();
      clearTimeout(state.reloadTimer);
      state.radarDrag = {
        pointerId: event.pointerId,
        bounds: {...state.bounds},
      };
      canvas.setPointerCapture(event.pointerId);
      moveFromRadar(canvasPoint(canvas, event), false);
    });
    canvas.addEventListener('pointermove', event => {
      if (!state.radarDrag) return;
      moveFromRadar(canvasPoint(canvas, event), false);
      scheduleRender();
    });
    canvas.addEventListener('pointerup', event => {
      finishRadarDrag(canvas, event, true);
    });
    canvas.addEventListener('pointercancel', event => {
      finishRadarDrag(canvas, event, false);
    });
    canvas.addEventListener('lostpointercapture', event => {
      finishRadarDrag(canvas, event, false);
    });
  }

  function finishMainDrag(canvas, event, commit) {
    const drag = state.drag;
    if (!drag) return;
    state.drag = null;
    if (!commit) {
      state.bounds = {...drag.bounds};
      scheduleRender();
    }
    state.suppressMapClick = commit && drag.moved;
    try { canvas.releasePointerCapture(event.pointerId); } catch {}
    if (commit && drag.moved) scheduleReload(true);
  }

  function finishRadarDrag(canvas, event, commit) {
    const drag = state.radarDrag;
    if (!drag) return;
    state.radarDrag = null;
    if (!commit) {
      state.bounds = {...drag.bounds};
      scheduleRender();
    }
    try { canvas.releasePointerCapture(event.pointerId); } catch {}
    if (commit) commitNavigation(true);
  }

  function init() {
    const canvas = document.getElementById('intel-map-canvas');
    const radar = document.getElementById('intel-radar-canvas');
    if (!canvas || !radar) return;
    if (window.WorldScenePanel) {
      window.WorldScenePanel.load = loadSceneCompatibility;
    }
    bindMainCanvas(canvas);
    bindRadar(radar);
    document.querySelectorAll('[data-intel-view]').forEach(button => {
      button.addEventListener('click', () => openView(button.dataset.intelView));
      button.addEventListener('keydown', event => {
        if (!['ArrowLeft', 'ArrowRight'].includes(event.key)) return;
        const buttons = [...document.querySelectorAll('[data-intel-view]')];
        const current = buttons.indexOf(button);
        const delta = event.key === 'ArrowRight' ? 1 : -1;
        const next = buttons[(current + delta + buttons.length) % buttons.length];
        event.preventDefault();
        next.focus();
        openView(next.dataset.intelView);
      });
    });
    document.querySelectorAll('.intel-layer').forEach(button => {
      button.addEventListener('click', () => {
        const layer = button.dataset.layer;
        if (layer && layer !== 'all') {
          state.layers[layer] = !state.layers[layer];
          state.layerRevision += 1;
          button.classList.toggle('active', state.layers[layer]);
          state.history?.replace(currentMapState());
          renderMainMap();
        }
      });
    });
    document.querySelectorAll('.intel-detail-tabs button').forEach(button => {
      button.addEventListener('click', () => renderDetail(button.dataset.intelTab));
    });
    document.getElementById('intel-detail-body')?.addEventListener('click', event => {
      const button = event.target.closest('[data-intel-action]');
      if (!button) return;
      if (button.dataset.intelAction === 'toggle-favorite') {
        toggleFavorite(state.detail?.tile?.wid);
      } else if (button.dataset.intelAction === 'open-battles') {
        openBattles();
      } else if (button.dataset.intelAction === 'open-lineup') {
        const index = Number(button.dataset.lineupIndex);
        const lineups = state.detail?.battleStats?.commonLineups || [];
        if (Number.isSafeInteger(index) && lineups[index]) openLineup(lineups[index].key);
      }
    });
    document.getElementById('intel-timeline')?.addEventListener('click', event => {
      const button = event.target.closest('[data-event-index]');
      const index = Number(button?.dataset.eventIndex);
      const wid = Number(Number.isSafeInteger(index) ? state.events[index]?.entity_id : 0);
      if (wid) locateWid(wid);
    });
    document.getElementById('intel-wid-input')?.addEventListener('keydown', event => {
      if (event.key === 'Enter') locateWid();
    });
    document.getElementById('intel-radar-toggle')?.addEventListener('click', () => {
      document.getElementById('intel-radar')?.classList.toggle('is-collapsed');
    });
    document.addEventListener('visibilitychange', () => {
      if (document.visibilityState === 'hidden') {
        state.streamDirty = true;
        clearTimeout(state.reloadTimer);
        clearTimeout(state.streamReloadTimer);
        aggregateLoader?.invalidate();
        invalidateDetailRequest();
        state.navigationRevision += 1;
        state.navigationController?.abort();
        state.navigationController = null;
        return;
      }
      scheduleRender();
      if (
        state.streamDirty
        && document.getElementById('tab33')?.classList.contains('active')
      ) {
        state.streamDirty = false;
        loadIntelligenceCenter(true);
      }
    });
    window.addEventListener('stzb:tab-changed', event => {
      if (Number(event.detail?.tabId) !== 33) {
        clearTimeout(state.reloadTimer);
        clearTimeout(state.streamReloadTimer);
        aggregateLoader?.invalidate();
        invalidateDetailRequest();
        state.navigationRevision += 1;
        state.navigationController?.abort();
        state.navigationController = null;
      } else if (state.streamDirty && document.visibilityState === 'visible') {
        state.streamDirty = false;
        loadIntelligenceCenter(true);
      }
    });
    window.addEventListener('stzb:stream-event', event => {
      const type = String(event.detail?.type || '');
      if (![
        'world_snapshot_complete',
        'world_scene_delta',
        'world_state_delta',
      ].includes(type)) return;
      state.streamDirty = true;
      window.WorldScenePanel?.markDirty();
      if (
        document.visibilityState !== 'visible'
        || !document.getElementById('tab33')?.classList.contains('active')
      ) return;
      clearTimeout(state.streamReloadTimer);
      state.streamReloadTimer = setTimeout(() => {
        state.streamReloadTimer = null;
        state.streamDirty = false;
        loadIntelligenceCenter(true);
      }, 350);
    });
    window.addEventListener('resize', scheduleRender);
  }

  async function fetchJson(url, signal) {
    const response = await fetch(url, {cache: 'no-store', signal});
    const data = await response.json();
    if (!response.ok) throw new Error(data.error || `HTTP ${response.status}`);
    return data;
  }

  function modulesReady() {
    return window.IntelligenceLoader?.createIntelligenceLoaderCoordinator
      && window.IntelligenceMap
      && window.IntelligenceMapOverview
      && window.IntelligenceMapNavigation;
  }

  function centeredBounds(row, col, size) {
    const span = Math.max(5, Math.min(160, Number(size) || 20));
    const rowUp = Math.max(0, Math.round(row - span / 2));
    const colLeft = Math.max(0, Math.round(col - span / 2));
    return {
      rowUp,
      rowDown: rowUp + span - 1,
      colLeft,
      colRight: colLeft + span - 1
    };
  }

  function boundsCenter(bounds) {
    return {
      row: (bounds.rowUp + bounds.rowDown) / 2,
      col: (bounds.colLeft + bounds.colRight) / 2
    };
  }

  function boundsQuery(bounds) {
    return new URLSearchParams(bounds).toString();
  }

  function canvasPoint(canvas, event) {
    const rect = canvas.getBoundingClientRect();
    return {x: event.clientX - rect.left, y: event.clientY - rect.top};
  }

  function formatBounds(bounds) {
    return bounds
      ? `row ${bounds.rowUp}–${bounds.rowDown} / col ${bounds.colLeft}–${bounds.colRight}`
      : '未知';
  }

  function card(title, content) {
    return `<details class="hud-detail-section intel-info-card" open>
      <summary>${esc(title)}</summary>
      <div class="hud-detail-section-body">${content}</div>
    </details>`;
  }

  function row(label, value) {
    return `<div class="intel-row"><span>${esc(label)}</span><b>${esc(value)}</b></div>`;
  }

  function fmtTime(value) {
    return value
      ? new Date(Number(value) * 1000).toLocaleString('zh-CN', {hour12: false})
      : '-';
  }

  function showState(text, error = false) {
    const element = document.getElementById('intel-map-status');
    if (element) {
      element.textContent = text;
      element.classList.toggle('intel-error', error);
    }
  }

  function readFavorites() {
    try {
      return JSON.parse(localStorage.getItem(FAVORITES_KEY) || '[]')
        .map(Number)
        .filter(Boolean);
    } catch {
      return [];
    }
  }

  function toggleFavorite(wid) {
    wid = Number(wid);
    if (state.favorites.has(wid)) state.favorites.delete(wid);
    else state.favorites.add(wid);
    localStorage.setItem(FAVORITES_KEY, JSON.stringify([...state.favorites]));
    renderMainMap();
    if (state.detail?.tile?.wid === wid) renderDetail('risk');
  }

  function openBattles() {
    const button = [...document.querySelectorAll('nav button')].find(candidate =>
      String(candidate.getAttribute('onclick')).includes('switchTab(10,')
    );
    switchTab(10, button);
    const input = document.getElementById('ba-wid');
    if (input) {
      input.value = String(state.selectedWid);
      loadBattlesAll(1);
    }
  }

  function openLineup(key) {
    const button = [...document.querySelectorAll('nav button')].find(candidate =>
      String(candidate.getAttribute('onclick')).includes('switchTab(34,')
    );
    switchTab(34, button);
    window.ResearchCenter?.openLineup(key);
  }

  window.loadIntelligenceCenter = loadIntelligenceCenter;
  window.IntelligenceCenter = {
    load: loadIntelligenceCenter,
    openView,
    selectWid,
    locateWid,
    focusBucket,
    moveFromRadar,
    goHome,
    goBack,
    goForward,
    zoomIn,
    zoomOut,
    openBattles,
    openLineup,
    toggleFavorite,
    state
  };
  document.addEventListener('DOMContentLoaded', init);
})();
