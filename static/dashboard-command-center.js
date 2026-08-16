(function () {
  'use strict';

  const SETTINGS_KEY = 'stzb.commandCenter.settings';
  const FAVORITES_KEY = 'stzb.commandCenter.favorites';
  const TIMELINE_LIMIT = 80;
  const ALERT_KINDS = ['convergence', 'arrival'];
  const DEFAULT_SETTINGS = {
    refresh: 10000,
    density: 'comfortable',
    motion: 'standard',
    sound: 'off',
    home: 33,
    intel: true
  };
  const state = {
    overview: null,
    settings: loadJson(SETTINGS_KEY, DEFAULT_SETTINGS),
    favorites: loadJson(FAVORITES_KEY, []),
    timeline: [],
    bufferedEvents: [],
    timelinePaused: false,
    selectedCommand: 0,
    refreshTimer: null,
    lastAlertIds: new Set()
  };
  const commands = [
    {label: '战场情报', hint: '风险地图、地块详情与时间线', tab: 33, keywords: 'intelligence risk map 情报 风险 地图'},
    {label: '团数据', hint: '团队与成员表现', tab: 24, keywords: 'team 团队'},
    {label: '打城考勤', hint: '攻城任务、出勤统计与导出', tab: 16, keywords: 'attendance siege 打城 考勤'},
    {label: '武将阵容', hint: '组合与胜率洞察', tab: 23, keywords: 'hero combo 武将'},
    {label: '州郡分布', hint: '州郡人数与势力', tab: 26, keywords: 'region 州郡'},
    {label: '玩家队伍', hint: '队伍配置检索', tab: 7, keywords: 'team army 玩家队伍'},
    {label: '战斗模拟', hint: '阵容推演', tab: 25, keywords: 'simulate simulator 模拟'},
    {label: '设置中心', hint: '刷新、密度与提醒', tab: 32, keywords: 'settings preference 设置'},
    {label: '战术检索', hint: '打开只读 Query Agent', action: openQueryAgent, keywords: 'agent query ai 检索'},
    {label: '切换紧凑模式', hint: '提高单屏信息密度', action: toggleDensity, keywords: 'compact density 紧凑'},
    {label: '刷新当前态势', hint: '立即重新聚合数据', action: function () { loadCommandCenterOverview(true); }, keywords: 'refresh reload 刷新'}
  ];

  function loadJson(key, fallback) {
    try {
      const value = JSON.parse(localStorage.getItem(key));
      return value && typeof value === 'object'
        ? (Array.isArray(fallback) ? value : Object.assign({}, fallback, value))
        : (Array.isArray(fallback) ? fallback.slice() : Object.assign({}, fallback));
    } catch (_) {
      return Array.isArray(fallback) ? fallback.slice() : Object.assign({}, fallback);
    }
  }

  function saveJson(key, value) {
    localStorage.setItem(key, JSON.stringify(value));
  }

  function escapeHtml(value) {
    if (typeof window.esc === 'function') return window.esc(value);
    return String(value == null ? '' : value)
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
  }

  function formatNumber(value) {
    return Number(value || 0).toLocaleString('zh-CN');
  }

  function formatTime(timestamp) {
    const value = Number(timestamp || 0);
    return value ? new Date(value * 1000).toLocaleTimeString('zh-CN', {hour12: false}) : '--:--';
  }

  function relativeTime(timestamp) {
    const normalized = window.DashboardRuntime?.normalizeTimestamp(timestamp) ??
      normalizeTimestampFallback(timestamp);
    const delta = normalized - Math.floor(Date.now() / 1000);
    if (!Number.isFinite(delta)) return '刚刚';
    if (delta > 0) {
      if (delta < 60) return `${delta} 秒后`;
      if (delta < 3600) return `${Math.ceil(delta / 60)} 分钟后`;
      return `${Math.ceil(delta / 3600)} 小时后`;
    }
    const ago = Math.abs(delta);
    if (ago < 60) return '刚刚';
    if (ago < 3600) return `${Math.floor(ago / 60)} 分钟前`;
    if (ago < 86400) return `${Math.floor(ago / 3600)} 小时前`;
    return `${Math.floor(ago / 86400)} 天前`;
  }

  function normalizeTimestampFallback(value) {
    const now = Date.now() / 1000;
    const numeric = Number(value);
    if (Number.isFinite(numeric) && String(value).trim() !== '') {
      return numeric > 1e12 ? numeric / 1000 : numeric;
    }
    const text = String(value || '').trim();
    if (/^\d{2}:\d{2}:\d{2}$/.test(text)) {
      const parts = text.split(':').map(Number);
      const date = new Date();
      date.setHours(parts[0], parts[1], parts[2], 0);
      return date.getTime() / 1000;
    }
    const parsed = Date.parse(text);
    return Number.isFinite(parsed) ? parsed / 1000 : now;
  }

  function tabButton(index) {
    return Array.from(document.querySelectorAll('nav button')).find(function (button) {
      return String(button.getAttribute('onclick') || '').includes(`switchTab(${index},`);
    });
  }

  function goToTab(index) {
    const button = tabButton(index);
    if (button && typeof window.switchTab === 'function') {
      window.switchTab(index, button);
      button.scrollIntoView({block: 'nearest'});
    }
  }

  function animateNumber(element, value) {
    if (!element) return;
    const target = Number(value || 0);
    const start = Number(element.dataset.value || 0);
    const animation = window.HudSystem?.animateValue(
      element,
      start,
      target,
      {
        duration: 320,
        formatter: function (current) {
          return formatNumber(Math.round(current));
        }
      }
    );
    if (!animation) {
      element.textContent = formatNumber(target);
      element.dataset.value = String(target);
      return Promise.resolve(target);
    }
    return animation.then(function () {
      element.dataset.value = String(target);
      return target;
    });
  }

  function renderKpis(metrics) {
    const grid = document.getElementById('cc-kpi-grid');
    if (!grid) return;
    const cards = [
      ['battlesToday', '今日战报', '实时交战强度', 'battle', 10],
      ['activeArmies', '活跃行军', '当前战场情报部队', 'army', 33],
      ['allianceMembers', '同盟成员', '当前档案成员数', 'member', 14],
      ['knownTiles', '已知地块', '世界状态覆盖', 'tile', 30],
      ['battlesTotal', '战报总量', '本区服历史积累', 'history', 10]
    ];
    grid.innerHTML = cards.map(function (card) {
      return `<button class="cc-kpi-card cc-kpi-card--${card[3]}" type="button" data-cc-tab="${card[4]}">
        <span class="cc-kpi-label">${card[1]}</span>
        <strong class="cc-kpi-value" data-metric="${card[0]}">0</strong>
        <span class="cc-kpi-meta">${card[2]} <b>↗</b></span>
      </button>`;
    }).join('');
    cards.forEach(function (card) {
      animateNumber(grid.querySelector(`[data-metric="${card[0]}"]`), metrics[card[0]]);
    });
  }

  function renderBattles(rows) {
    const list = document.getElementById('cc-battle-list');
    if (!list) return;
    if (!rows.length) {
      list.innerHTML = '<div class="cc-empty">还没有战报。保持抓包运行后，新战报会自动出现。</div>';
      return;
    }
    list.innerHTML = rows.slice(0, 8).map(function (row) {
      const result = Number(row.result) === 1 ? '胜利' : Number(row.result) === 2 ? '失利' : (row.result_desc || '战斗');
      const tone = Number(row.result) === 1 ? 'success' : Number(row.result) === 2 ? 'danger' : 'neutral';
      return `<button class="cc-battle-row" type="button" data-battle-id="${Number(row.battle_id)}">
        <span class="cc-result cc-result--${tone}">${escapeHtml(result)}</span>
        <span class="cc-battle-match"><strong>${escapeHtml(row.atk_name || '未知')}</strong><em>VS</em><strong>${escapeHtml(row.def_name || '未知')}</strong>
          <small>${escapeHtml(row.atk_union || '无同盟')} · WID ${escapeHtml(row.wid || '-')}</small></span>
        <span class="cc-battle-reward">+${formatNumber(row.atk_gongxun)}<small>武勋</small></span>
        <time>${escapeHtml(row.time_str || relativeTime(row.time))}</time>
      </button>`;
    }).join('');
  }

  function renderArmies(rows) {
    const list = document.getElementById('cc-army-list');
    if (!list) return;
    if (!rows.length) {
      list.innerHTML = '<div class="cc-empty">暂无活跃行军。战场情报数据到达后将在此展示。</div>';
      return;
    }
    list.innerHTML = rows.slice(0, 8).map(function (row) {
      const urgent = Number(row.end_time || 0) - Date.now() / 1000 < 300;
      return `<button class="cc-army-row${urgent ? ' is-urgent' : ''}" type="button" data-army-id="${Number(row.army_id)}">
        <span class="cc-army-route"><b>${escapeHtml(row.wid_from || '?')}</b><i>→</i><b>${escapeHtml(row.wid_to || '?')}</b></span>
        <span><strong>${escapeHtml(row.owner_name || `队伍 ${row.army_id}`)}</strong><small>${escapeHtml(row.owner_union_name || '未归属')} · ${escapeHtml(row.target_name || '未知目标')}</small></span>
        <time>${relativeTime(row.end_time)}</time>
      </button>`;
    }).join('');
  }

  function renderAlerts(alerts) {
    const list = document.getElementById('cc-alert-list');
    const count = document.getElementById('cc-alert-count');
    if (!list || !count) return;
    count.textContent = String(alerts.length);
    list.replaceChildren();
    if (!alerts.length) {
      const empty = document.createElement('div');
      empty.className = 'cc-empty cc-empty--success';
      empty.textContent = '链路正常，当前没有需要处理的战术预警。';
      list.append(empty);
      return;
    }
    const ALERT_LEVELS = new Set(['info', 'success', 'warning', 'danger', 'critical']);
    alerts.forEach(function (alert, index) {
      const button = document.createElement('button');
      const level = ALERT_LEVELS.has(alert.level) ? alert.level : 'info';
      const signal = document.createElement('span');
      const copy = document.createElement('span');
      const title = document.createElement('strong');
      const message = document.createElement('small');
      const action = document.createElement('b');
      button.type = 'button';
      button.className = `cc-alert cc-alert--${level}`;
      button.dataset.alertIndex = String(index);
      signal.className = 'cc-alert-signal';
      title.textContent = String(alert.title || '');
      message.textContent = String(alert.message || '');
      action.textContent = '查看';
      copy.append(title, message);
      button.append(signal, copy, action);
      list.append(button);
    });
    announceNewAlerts(alerts);
  }

  function announceNewAlerts(alerts) {
    alerts.forEach(function (alert) {
      if (state.lastAlertIds.has(alert.id)) return;
      state.lastAlertIds.add(alert.id);
      if (state.settings.sound === 'all' || (state.settings.sound === 'danger' && alert.level === 'danger')) {
        playAlertTone(alert.level);
      }
    });
  }

  function playAlertTone(level) {
    try {
      const context = new (window.AudioContext || window.webkitAudioContext)();
      const oscillator = context.createOscillator();
      const gain = context.createGain();
      oscillator.frequency.value = level === 'danger' ? 660 : 440;
      gain.gain.setValueAtTime(0.0001, context.currentTime);
      gain.gain.exponentialRampToValueAtTime(0.05, context.currentTime + 0.02);
      gain.gain.exponentialRampToValueAtTime(0.0001, context.currentTime + 0.18);
      oscillator.connect(gain).connect(context.destination);
      oscillator.start();
      oscillator.stop(context.currentTime + 0.2);
    } catch (_) {}
  }

  function renderProfile(profile, freshness) {
    const summary = document.getElementById('cc-profile-summary');
    if (!summary) return;
    const identity = [profile.roleName, profile.serverName].filter(Boolean).join(' · ') || '本机档案';
    summary.innerHTML = `<span class="cc-live-dot"></span>${escapeHtml(identity)} · 数据生成于 ${formatTime(freshness.generatedAt)}`;
  }

  function staleAgeBucket(timestamp) {
    const normalized = normalizeTimestampFallback(timestamp);
    const age = Math.max(0, Math.floor(Date.now() / 1000) - normalized);
    if (age < 3600) return 'hour';
    if (age < 86400) return 'day';
    return `${Math.floor(age / 86400)}d`;
  }

  function emitStaleData(component, staleMessage, timestamp) {
    const ageBucket = staleAgeBucket(timestamp);
    window.HudSystem?.emit({
      type: "data:stale",
      target: "#hud-health-grid",
      domain: "system",
      severity: "warning",
      message: staleMessage,
      timestamp: Date.now(),
      dedupeKey: `stale:${component}:${ageBucket}`,
    });
  }

  function reportOverviewStaleness(data) {
    const alert = (data.alerts || []).find(function (item) {
      return item.kind === 'stale_data';
    });
    if (!alert) return;
    window.markStreamStale?.();
    emitStaleData(
      alert.entityId || 'overview',
      alert.message || '指挥中心数据已陈旧',
      alert.lastUpdatedAt || data.freshness?.generatedAt
    );
  }

  function reportHealthStaleness(health) {
    Object.entries(health?.components || {}).forEach(function (entry) {
      const componentName = entry[0];
      const component = entry[1] || {};
      if (component.status === 'stale') {
        window.markStreamStale?.();
        emitStaleData(
          componentName,
          component.detail || `${component.label || componentName} 数据已陈旧`,
          component.updatedAt || component.timestamp
        );
      }
    });
  }

  async function loadCommandCenterOverview(force) {
    const active = document.getElementById('tab31')?.classList.contains('active');
    if (!force && !active && state.overview) return state.overview;
    try {
      const response = await fetch('/api/command-center/overview', {cache: 'no-store'});
      const data = window.DashboardRuntime?.parseJsonResponse
        ? await window.DashboardRuntime.parseJsonResponse(response, '/api/command-center/overview')
        : await parseOverviewResponseFallback(response);
      if (!data.ok) throw new Error(data.error || `HTTP ${response.status}`);
      state.overview = data;
      renderKpis(data.metrics || {});
      renderBattles(data.battles || []);
      renderArmies(data.armies || []);
      renderAlerts(data.alerts || []);
      renderProfile(data.profile || {}, data.freshness || {});
      reportOverviewStaleness(data);
      scheduleRefresh();
      return data;
    } catch (error) {
      ['cc-battle-list', 'cc-army-list', 'cc-alert-list'].forEach(function (id) {
        const node = document.getElementById(id);
        if (!node) return;
        const errorBox = document.createElement('div');
        const retry = document.createElement('button');
        errorBox.className = 'cc-error';
        errorBox.append(document.createTextNode(`态势加载失败：${error.message} `));
        retry.type = 'button';
        retry.dataset.ccAction = 'retry-overview';
        retry.textContent = '重试';
        errorBox.append(retry);
        node.replaceChildren(errorBox);
      });
      return null;
    }
  }

  async function parseOverviewResponseFallback(response) {
    const contentType = response.headers.get('content-type') || '';
    const text = await response.text();
    if (/text\/html/i.test(contentType) || /^\s*</.test(text)) {
      throw new Error(`后端接口不可用（${response.status}），当前后端可能仍是旧版本，请停止并重新启动后端。`);
    }
    let data;
    try {
      data = JSON.parse(text);
    } catch (_) {
      throw new Error('后端返回了无效 JSON，请检查后端日志并重启服务。');
    }
    if (!response.ok) throw new Error(data.message || data.error || `HTTP ${response.status}`);
    return data;
  }

  function scheduleRefresh() {
    clearInterval(state.refreshTimer);
    const delay = Number(state.settings.refresh || 0);
    if (delay > 0) state.refreshTimer = setInterval(function () {
      if (document.visibilityState === 'visible') loadCommandCenterOverview();
    }, delay);
  }

  function eventCategory(type) {
    if (/battle/.test(type)) return 'battle';
    if (/world/.test(type)) return 'world';
    if (/monitor|field|queue|march/.test(type)) return 'monitor';
    return 'system';
  }

  function eventTitle(type, data) {
    if (type === 'battle') return `${data.atk_name || '未知'} ${data.result_desc || '完成战斗'} ${data.def_name || data.def_union || ''}`.trim();
    if (type === 'world_snapshot_complete') return '世界场景全量快照已更新';
    if (type === 'world_scene_delta') return '世界场景增量已应用';
    if (type === 'battle_monitor_13a4') return `战场监控更新 · ${data.team_count || data.count || 0} 支队伍`;
    if (type === 'announcement') return `新公告 · ${data.title || ''}`;
    return data.message || data.title || type.replace(/_/g, ' ');
  }

  function stableBattleKey(data, timestamp) {
    const businessId = data?.battle_id ?? data?.battleId ?? data?.id;
    if (businessId != null && String(businessId)) {
      return `battle:${String(businessId)}`;
    }
    return `battle:${[
      normalizeTimestampFallback(timestamp),
      data?.atk_uid || data?.atk_name || '',
      data?.def_uid || data?.def_name || data?.def_union || '',
      data?.wid || data?.wid_code || '',
      data?.result ?? data?.result_desc ?? '',
    ].join(':')}`;
  }

  function pushTimeline(type, data, timestamp) {
    const normalizedTime = window.DashboardRuntime?.normalizeTimestamp(timestamp) ??
      normalizeTimestampFallback(timestamp);
    const item = {
      id: `${Date.now()}:${Math.random()}`,
      type: type,
      category: eventCategory(type),
      title: eventTitle(type, data || {}),
      data: data || {},
      time: normalizedTime
    };
    if (state.timelinePaused) {
      state.bufferedEvents.unshift(item);
      updateBufferBadge();
      return;
    }
    state.timeline.unshift(item);
    state.timeline = state.timeline.slice(0, TIMELINE_LIMIT);
    const eventTarget = renderTimeline(item.id);
    if (item.category === 'battle') {
      const battleKey = stableBattleKey(data, timestamp);
      window.HudSystem?.emit({
        type: "battle:report-arrived",
        target: eventTarget,
        domain: "analysis",
        severity: "info",
        timestamp: Date.now(),
        dedupeKey: battleKey,
        cooldownMs: 2_000,
      });
    }
  }

  function renderTimeline(eventId) {
    const list = document.getElementById('cc-timeline-list');
    if (!list) return null;
    const filter = document.getElementById('cc-timeline-filter')?.value || 'all';
    const rows = state.timeline.filter(function (item) { return filter === 'all' || item.category === filter; });
    list.innerHTML = rows.length ? rows.slice(0, 30).map(function (item) {
      return `<div class="cc-timeline-item cc-timeline-item--${item.category}" data-event-id="${escapeHtml(item.id)}">
        <span class="cc-timeline-node"></span><div><strong>${escapeHtml(item.title)}</strong>
        <small>${escapeHtml(item.type)} · ${relativeTime(item.time)}</small></div></div>`;
    }).join('') : '<div class="cc-empty">等待实时事件…</div>';
    if (!eventId) return null;
    return Array.from(list.children).find(function (row) {
      return row.dataset.eventId === eventId;
    }) || null;
  }

  function toggleTimelinePause() {
    state.timelinePaused = !state.timelinePaused;
    const button = document.getElementById('cc-timeline-pause');
    if (button) {
      button.textContent = state.timelinePaused ? '恢复' : '暂停';
      button.classList.toggle('is-paused', state.timelinePaused);
    }
    if (!state.timelinePaused) flushTimeline();
  }

  function updateBufferBadge() {
    const buffer = document.getElementById('cc-timeline-buffer');
    if (!buffer) return;
    buffer.hidden = !state.bufferedEvents.length;
    const button = buffer.querySelector('button');
    if (button) button.textContent = `${state.bufferedEvents.length} 条新事件，查看最新`;
  }

  function flushTimeline() {
    state.timeline = state.bufferedEvents.concat(state.timeline).slice(0, TIMELINE_LIMIT);
    state.bufferedEvents = [];
    updateBufferBadge();
    renderTimeline();
  }

  function connectTimeline() {
    window.addEventListener('stzb:stream-event', function (event) {
      const detail = event.detail || {};
      pushTimeline(detail.type || 'system', detail.data || {}, detail.timestamp || detail.ts);
    });
  }

  function favoriteLabel(item) {
    const names = {wid: 'WID', army: '队伍', player: '玩家', battle: '战报'};
    return `${names[item.type] || item.type} · ${item.label || item.id}`;
  }

  function renderFavorites() {
    const list = document.getElementById('cc-favorites-list');
    if (!list) return;
    list.replaceChildren();
    if (!state.favorites.length) {
      const empty = document.createElement('div');
      empty.className = 'cc-empty';
      empty.textContent = '收藏 WID、队伍、玩家或战报，快速追踪关键对象。';
      list.append(empty);
      return;
    }
    state.favorites.forEach(function (item, index) {
      const row = document.createElement('div');
      const open = document.createElement('button');
      const label = document.createElement('span');
      const note = document.createElement('small');
      const remove = document.createElement('button');
      row.className = 'cc-favorite';
      open.type = 'button';
      open.dataset.favoriteIndex = String(index);
      label.textContent = favoriteLabel(item);
      note.textContent = String(item.note || '点击定位');
      open.append(label, note);
      remove.className = 'cc-favorite-remove';
      remove.type = 'button';
      remove.dataset.removeFavorite = String(index);
      remove.setAttribute('aria-label', `移除 ${item.label || item.id}`);
      remove.textContent = '×';
      row.append(open, remove);
      list.append(row);
    });
  }

  function addFavorite(type, id, label, note) {
    const normalized = {type: String(type), id: String(id), label: String(label || id), note: String(note || '')};
    if (!normalized.id || state.favorites.some(function (item) { return item.type === normalized.type && item.id === normalized.id; })) return;
    state.favorites.unshift(normalized);
    state.favorites = state.favorites.slice(0, 30);
    saveJson(FAVORITES_KEY, state.favorites);
    renderFavorites();
  }

  function promptFavorite() {
    const input = window.prompt('输入要关注的 WID、队伍 ID、战报 ID，或玩家名');
    if (!input || !input.trim()) return;
    const value = input.trim();
    const type = /^\d+$/.test(value) ? (value.length >= 5 ? 'wid' : 'battle') : 'player';
    addFavorite(type, value, value, '手动关注');
  }

  function openFavorite(index) {
    const item = state.favorites[index];
    if (!item) return;
    if (item.type === 'battle' && typeof window.showBattleDetail === 'function') window.showBattleDetail(Number(item.id));
    else if (item.type === 'army') {
      goToTab(27);
      setTimeout(function () {
        const input = document.getElementById('bm-search-input');
        if (input) { input.value = item.id; window.searchBattleMonitor?.(); }
      }, 80);
    } else if (item.type === 'wid') {
      goToTab(33);
      setTimeout(function () {
        window.IntelligenceCenter?.openView('map');
        window.IntelligenceCenter?.locateWid(Number(item.id));
      }, 80);
    } else {
      openQueryAgent(`${item.type === 'player' ? '查询玩家 ' : '查询 '} ${item.id}`);
    }
  }

  function applySettings() {
    document.body.dataset.density = state.settings.density;
    document.body.dataset.motion = state.settings.motion;
    window.HudSystem?.setMotionLevel(state.settings.motion);
    document.body.classList.toggle('cc-hide-intel', !state.settings.intel);
    scheduleRefresh();
  }

  function notifySettingSaved(key, changedSettingLabel) {
    window.HudSystem?.toast({
      severity: "success",
      title: "设置已保存",
      message: changedSettingLabel,
      source: "设置中心",
      timestamp: Date.now(),
      dedupeKey: `setting:${key}`,
    });
  }

  function loadCommandCenterSettings() {
    Promise.resolve(window.HudSystem?.loadHealth()).then(reportHealthStaleness);
    const map = {
      'cc-setting-refresh': String(state.settings.refresh),
      'cc-setting-density': state.settings.density,
      'cc-setting-motion': state.settings.motion,
      'cc-setting-sound': state.settings.sound
    };
    Object.keys(map).forEach(function (id) {
      const element = document.getElementById(id);
      if (element) element.value = map[id];
    });
    const intel = document.getElementById('cc-setting-intel');
    if (intel) intel.checked = Boolean(state.settings.intel);
    const apiToken = document.getElementById('cc-setting-api-token');
    if (apiToken) apiToken.value = sessionStorage.getItem('stzb.apiToken') || '';
  }

  function bindSettings() {
    const controls = {
      'cc-setting-refresh': ['refresh', Number, '自动刷新频率'],
      'cc-setting-density': ['density', String, '信息密度'],
      'cc-setting-motion': ['motion', String, '界面动效'],
      'cc-setting-sound': ['sound', String, '声音提醒']
    };
    Object.keys(controls).forEach(function (id) {
      document.getElementById(id)?.addEventListener('change', function (event) {
        const config = controls[id];
        state.settings[config[0]] = config[1](event.target.value);
        saveJson(SETTINGS_KEY, state.settings);
        applySettings();
        notifySettingSaved(config[0], config[2]);
      });
    });
    document.getElementById('cc-setting-intel')?.addEventListener('change', function (event) {
      state.settings.intel = event.target.checked;
      saveJson(SETTINGS_KEY, state.settings);
      applySettings();
      notifySettingSaved('intel', '右侧情报栏');
    });
    document.getElementById('cc-setting-api-token')?.addEventListener('change', function (event) {
      const token = event.target.value.trim();
      if (token) sessionStorage.setItem('stzb.apiToken', token);
      else sessionStorage.removeItem('stzb.apiToken');
      notifySettingSaved('api-token', token ? '写操作 Token' : '写操作 Token 已清除');
    });
    document.getElementById('cc-settings-reset')?.addEventListener('click', function () {
      state.settings = Object.assign({}, DEFAULT_SETTINGS);
      saveJson(SETTINGS_KEY, state.settings);
      sessionStorage.removeItem('stzb.apiToken');
      loadCommandCenterSettings();
      applySettings();
      notifySettingSaved('reset', '本地偏好已重置');
    });
  }

  function toggleDensity() {
    state.settings.density = state.settings.density === 'compact' ? 'comfortable' : 'compact';
    saveJson(SETTINGS_KEY, state.settings);
    loadCommandCenterSettings();
    applySettings();
    notifySettingSaved('density', '信息密度');
  }

  function openQueryAgent(prefill) {
    if (typeof window.toggleQueryAgent === 'function') window.toggleQueryAgent(true);
    const input = document.getElementById('query-agent-input');
    if (input) {
      if (prefill) input.value = prefill;
      input.focus();
    }
  }

  function filteredCommands(query) {
    const normalized = query.trim().toLowerCase();
    const base = !normalized ? commands : commands.filter(function (command) {
      return `${command.label} ${command.hint} ${command.keywords || ''}`.toLowerCase().includes(normalized);
    });
    if (normalized && (/^\d{5,}$/.test(normalized) || /队伍|玩家|战报|wid/i.test(normalized))) {
      return [{label: `战术检索：${query}`, hint: '使用只读 Agent 查询并提供页面动作', action: function () { openQueryAgent(query); }, keywords: query}].concat(base);
    }
    return base;
  }

  function renderCommands() {
    const input = document.getElementById('cc-command-input');
    const results = document.getElementById('cc-command-results');
    if (!input || !results) return;
    const rows = filteredCommands(input.value);
    state.selectedCommand = Math.max(0, Math.min(state.selectedCommand, rows.length - 1));
    results.innerHTML = rows.length ? rows.map(function (command, index) {
      return `<button type="button" role="option" aria-selected="${index === state.selectedCommand}"
        class="cc-command${index === state.selectedCommand ? ' is-selected' : ''}" data-command-index="${index}">
        <span><strong>${escapeHtml(command.label)}</strong><small>${escapeHtml(command.hint)}</small></span><kbd>↵</kbd></button>`;
    }).join('') : '<div class="cc-empty">没有匹配命令</div>';
    results._commands = rows;
  }

  function executeCommand(index) {
    const results = document.getElementById('cc-command-results');
    const command = results?._commands?.[index];
    if (!command) return;
    closePalette();
    if (command.tab != null) goToTab(command.tab);
    else command.action?.();
  }

  function openPalette() {
    const dialog = document.getElementById('cc-command-dialog');
    if (!dialog) return;
    if (!dialog.open) dialog.showModal();
    const input = document.getElementById('cc-command-input');
    state.selectedCommand = 0;
    input.value = '';
    renderCommands();
    requestAnimationFrame(function () { input.focus(); });
  }

  function closePalette() {
    const dialog = document.getElementById('cc-command-dialog');
    if (dialog?.open) dialog.close();
  }

  function bindPalette() {
    const dialog = document.getElementById('cc-command-dialog');
    const input = document.getElementById('cc-command-input');
    input?.addEventListener('input', function () { state.selectedCommand = 0; renderCommands(); });
    input?.addEventListener('keydown', function (event) {
      const rows = document.getElementById('cc-command-results')?._commands || [];
      if (event.key === 'ArrowDown') { event.preventDefault(); state.selectedCommand = Math.min(rows.length - 1, state.selectedCommand + 1); renderCommands(); }
      else if (event.key === 'ArrowUp') { event.preventDefault(); state.selectedCommand = Math.max(0, state.selectedCommand - 1); renderCommands(); }
      else if (event.key === 'Enter') { event.preventDefault(); executeCommand(state.selectedCommand); }
    });
    dialog?.addEventListener('click', function (event) { if (event.target === dialog) closePalette(); });
    document.addEventListener('keydown', function (event) {
      if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k') {
        event.preventDefault();
        dialog?.open ? closePalette() : openPalette();
      }
    });
  }

  function handleClick(event) {
    const tab = event.target.closest('[data-cc-tab]');
    if (tab) goToTab(Number(tab.dataset.ccTab));
    const battle = event.target.closest('[data-battle-id]');
    if (battle) window.showBattleDetail?.(Number(battle.dataset.battleId));
    const army = event.target.closest('[data-army-id]');
    if (army) addFavorite('army', army.dataset.armyId, `队伍 ${army.dataset.armyId}`, '来自活跃行军');
    const alert = event.target.closest('[data-alert-index]');
    if (alert) {
      const alertIndex = Number(alert.dataset.alertIndex);
      const alertModel = Number.isSafeInteger(alertIndex) ? state.overview?.alerts?.[alertIndex] : null;
      if (alertModel?.entityType === 'army') addFavorite('army', alertModel.entityId, `队伍 ${alertModel.entityId}`, '来自战术预警');
      else if (alertModel?.entityType === 'wid') addFavorite('wid', alertModel.entityId, `WID ${alertModel.entityId}`, '来自战术预警');
    }
    const action = event.target.closest('[data-cc-action]');
    if (action?.dataset.ccAction === 'retry-overview') loadCommandCenterOverview(true);
    const favorite = event.target.closest('[data-favorite-index]');
    if (favorite) openFavorite(Number(favorite.dataset.favoriteIndex));
    const remove = event.target.closest('[data-remove-favorite]');
    if (remove) {
      state.favorites.splice(Number(remove.dataset.removeFavorite), 1);
      saveJson(FAVORITES_KEY, state.favorites);
      renderFavorites();
    }
    const command = event.target.closest('[data-command-index]');
    if (command) executeCommand(Number(command.dataset.commandIndex));
  }

  function init() {
    applySettings();
    loadCommandCenterSettings();
    bindSettings();
    bindPalette();
    renderFavorites();
    renderTimeline();
    connectTimeline();
    document.addEventListener('click', handleClick);
    document.getElementById('cc-timeline-filter')?.addEventListener('change', renderTimeline);
    if (document.getElementById('tab31')?.classList.contains('active')) loadCommandCenterOverview(true);
  }

  window.loadCommandCenterOverview = loadCommandCenterOverview;
  window.loadCommandCenterSettings = loadCommandCenterSettings;
  window.CommandCenter = {
    openPalette: openPalette,
    closePalette: closePalette,
    toggleTimelinePause: toggleTimelinePause,
    flushTimeline: flushTimeline,
    addFavorite: addFavorite,
    promptFavorite: promptFavorite,
    goToTab: goToTab,
    state: state
  };
  document.addEventListener('DOMContentLoaded', init);
})();
