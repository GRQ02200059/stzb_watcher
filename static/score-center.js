(function () {
  'use strict';

  const RULE_FIELDS = [
    ['battleWeight', '出战权重'],
    ['winWeight', '胜场权重'],
    ['drawWeight', '平局权重'],
    ['gongxunDivisor', '武勋除数'],
    ['mainCityWeight', '主力打城'],
    ['tearWeight', '拆迁出勤'],
    ['attendanceWeight', '普通出勤'],
  ];
  const state = {
    board: 'overall',
    data: null,
    rules: null,
    preview: null,
    selectedPlayer: '',
    boardRequestRevision: 0,
    playerRequestRevision: 0,
    previewRequestRevision: 0,
    ruleSaving: false,
    pendingRuleId: null,
    pendingRuleVersion: null,
    ruleTransactionPhase: 'idle',
    ruleTransactionRevision: 0,
    recalculating: false,
    adjustmentSaving: false,
    previousRows: null,
    loading: false,
    initialized: false,
  };

  async function load() {
    bindOnce();
    const requestRevision = ++state.boardRequestRevision;
    const query = currentQuery();
    const hadData = Boolean(state.data);
    state.loading = true;
    renderLoadState(hadData ? 'refreshing' : 'loading');
    try {
      const [data, rules] = await Promise.all([
        apiFetch(`/api/custom_scores?${query}`),
        apiFetch(`/api/custom_scores/rules?season=${encodeURIComponent(season())}`),
      ]);
      if (!data?.ok) throw new Error(data?.error || '积分中心加载失败');
      if (requestRevision !== state.boardRequestRevision) return null;
      state.data = data;
      state.rules = rules?.ok ? rules : state.rules;
      renderKpis(data.summary || {});
      renderBoard(data);
      renderRuleState();
      renderLoadState('success');
      return data;
    } catch (error) {
      if (requestRevision !== state.boardRequestRevision) return null;
      const message = error?.message || '积分中心加载失败';
      showToast(message, 'var(--danger)');
      renderLoadState('error', message, !hadData);
      return null;
    } finally {
      if (requestRevision === state.boardRequestRevision) {
        state.loading = false;
        document.getElementById('score-board')?.removeAttribute?.('aria-busy');
      }
    }
  }

  function renderLoadState(kind, message, replace) {
    return renderScoreState('board', kind, {
      message,
      replace,
      actionLabel: kind === 'error' ? '重试' : '',
      action: kind === 'error' ? load : undefined,
    });
  }

  function renderScoreState(surfaceName, kind, options = {}) {
    const surface = document.getElementById(`score-${surfaceName}${surfaceName === 'board' ? '' : '-surface'}`);
    const host = document.getElementById(`score-${surfaceName}-status`);
    if (['loading', 'refreshing'].includes(kind)) {
      surface?.setAttribute?.('aria-busy', 'true');
    } else {
      surface?.removeAttribute?.('aria-busy');
    }
    const action = typeof options.action === 'function'
      ? () => Promise.resolve().then(options.action).catch(error => {
        showToast(error?.message || '操作失败', 'var(--danger)');
      })
      : undefined;
    return window.HudSystem?.renderState(host, {
      kind,
      message: options.message,
      replace: options.replace,
      actionLabel: options.actionLabel || '',
      action,
    });
  }

  function setActionBusy(buttonId, busy, disabledWhenIdle = false) {
    const button = document.getElementById(buttonId);
    if (!button) return;
    button.disabled = busy || disabledWhenIdle;
    if (busy) button.setAttribute?.('aria-busy', 'true');
    else button.removeAttribute?.('aria-busy');
  }

  function setRuleControlsBusy(busy) {
    const controls = [
      document.getElementById('score-rule-name'),
      document.getElementById('score-rule-preset'),
      document.getElementById('score-rule-save'),
      document.getElementById('score-rule-close'),
      ...document.querySelectorAll('[data-score-rule-field]'),
    ];
    controls.forEach(control => {
      if (!control) return;
      control.disabled = busy;
      if (busy) control.setAttribute?.('aria-busy', 'true');
      else control.removeAttribute?.('aria-busy');
    });
  }

  function switchBoard(board) {
    state.board = ['overall', 'battle', 'siege'].includes(board) ? board : 'overall';
    document.querySelectorAll('[data-score-board]').forEach(button => {
      button.classList.toggle('active', button.dataset.scoreBoard === state.board);
    });
    load();
  }

  function renderKpis(summary) {
    const cards = [
      ['参评人数', summary.players || 0, '#38bdf8'],
      ['综合积分', summary.scoreTotal || 0, '#8b6cff'],
      ['战斗贡献', summary.battleTotal || 0, '#22d3ee'],
      ['攻城贡献', summary.siegeTotal || 0, '#f5b84b'],
      ['手动调整', summary.adjustmentTotal || 0, '#fb7185'],
    ];
    document.getElementById('score-kpis').innerHTML = cards.map(item =>
      `<div class="score-kpi" style="--score-color:${item[2]}"><span>${item[0]}</span><strong>${formatScore(item[1])}</strong></div>`
    ).join('');
  }

  function renderBoard(data) {
    const rows = data.rows || [];
    const previousRows = state.previousRows;
    const maximum = Math.max(1, ...rows.map(row => boardValue(row)));
    document.getElementById('score-board-meta').textContent =
      `赛季 ${data.seasonId} · 规则 v${data.ruleVersion || 0} · ${data.dataCompleteness === 'complete' ? '数据完整' : `缺失 ${data.missingSources?.join('、') || '部分来源'}`}`;
    document.getElementById('score-board-body').innerHTML = rows.length
      ? rows.map(row => {
        const value = boardValue(row);
        const percent = Math.max(4, Math.round(value / maximum * 100));
        const previous = previousRows?.get(row.playerName);
        const delta = previous ? Number(row.score || 0) - previous.score : 0;
        const completeness = normalizeDataCompleteness(row.dataCompleteness);
        const deltaState = delta > 0 ? "up" : delta < 0 ? "down" : "same";
        const deltaMarkup = deltaState !== 'same'
          ? `<span class="score-delta-marker" aria-label="${deltaState === 'up' ? '上升' : '下降'}">${deltaState === 'up' ? '↑ 上升' : '↓ 下降'} ${signed(delta)}</span>`
          : '';
        return `<tr class="score-player-row analysis-row" data-score-player="${escapeAttr(row.playerName)}">
          <td><span class="score-rank analysis-rank" data-rank="${row.rank}">${row.rank}</span></td>
          <td><b>${esc(row.playerName)}</b><small>${esc(row.playerUid || '')}</small></td>
          <td>${esc(row.unionName || '未知同盟')}<small>${esc(row.groupName || '')}</small></td>
          <td class="score-contribution"><span class="score-value">${formatScore(row.score)}</span>${deltaMarkup}<div class="score-bar"><i style="--score-pct:${percent}%"></i></div></td>
          <td class="score-value">${formatScore(row.battleScore)}</td>
          <td class="score-value">${formatScore(row.siegeScore)}</td>
          <td class="score-value">${signed(row.adjustmentScore)}</td>
          <td><span class="score-status hud-status-chip analysis-evidence ${completeness}" data-kind="history">${completeness === 'complete' ? '完整' : '需关注'}</span></td>
        </tr>`;
      }).join('')
      : '<tr><td colspan="8"><div class="intel-empty">暂无已确认积分。请先执行预览重算。</div></td></tr>';
    document.querySelectorAll('#score-board-body .analysis-row').forEach((row, index) => {
      const previous = previousRows?.get(rows[index]?.playerName);
      const delta = previous
        ? Number(rows[index]?.score || 0) - previous.score
        : 0;
      row.dataset.delta = delta > 0 ? "up" : delta < 0 ? "down" : "same";
      if (row.dataset.delta === "same") delete row.dataset.delta;
    });
    state.previousRows = null;
  }

  async function openPlayer(playerName) {
    const requestRevision = ++state.playerRequestRevision;
    const content = document.getElementById('score-player-content');
    const hadContent = Boolean(content?.innerHTML);
    const dialog = document.getElementById('score-player-dialog');
    if (dialog && !dialog.open) dialog.showModal();
    renderScoreState('player', hadContent ? 'refreshing' : 'loading');
    try {
      const data = await apiFetch(
        `/api/custom_scores/player/${encodeURIComponent(playerName)}?season=${encodeURIComponent(season())}`
      );
      if (!data?.ok) throw new Error(data?.error || '玩家积分详情加载失败');
      if (requestRevision !== state.playerRequestRevision) return null;
      const metrics = data.metrics || data.breakdown?.metrics || {};
      const components = data.breakdown?.components || {};
      content.innerHTML = `<div class="analysis-detail-head"><div><span class="hud-page-kicker">PLAYER SCORE TRACE</span>
        <h2>${esc(data.playerName)}</h2>
        <p>${esc(data.unionName || '未知同盟')} · 规则 v${data.rule?.version || '-'}</p></div>
        <div class="analysis-evidence-row"><span class="hud-status-chip analysis-evidence" data-kind="config">RULE CONFIG</span><span class="hud-status-chip analysis-evidence" data-kind="history">SCORE TRACE</span></div></div>
        <div class="score-detail-grid analysis-fact-grid">
          ${detailCard('综合积分', data.score)}
          ${detailCard('战斗贡献', data.battleScore)}
          ${detailCard('攻城贡献', data.siegeScore)}
          ${detailCard('出战 / 胜 / 平', `${metrics.battles || 0} / ${metrics.wins || 0} / ${metrics.draws || 0}`)}
          ${detailCard('武勋', metrics.gongxunTotal || 0)}
          ${detailCard('主力 / 拆迁 / 出勤', `${metrics.mainCityCnt || 0} / ${metrics.tearCnt || 0} / ${metrics.attendanceCnt || 0}`)}
        </div>
        <div class="score-formula-preview analysis-related">${Object.entries(components).map(([key, value]) => `${esc(key)} = ${formatScore(value)}`).join('<br>') || '暂无分量'}</div>
        <div class="research-actions"><button class="btn btn-primary" type="button" data-score-action="open-adjustment">添加奖惩</button></div>
        <h3>手动调整</h3>
        ${(data.adjustments || []).map(item => `<div class="score-adjustment-row"><span>${esc(item.reason)}</span><b class="${item.points >= 0 ? 'score-delta up' : 'score-delta down'}">${signed(item.points)}</b></div>`).join('') || '<div class="intel-empty">暂无调整</div>'}`;
      state.selectedPlayer = playerName;
      renderScoreState('player', 'success');
      return data;
    } catch (error) {
      if (requestRevision !== state.playerRequestRevision) return null;
      const message = error?.message || '玩家积分详情加载失败';
      showToast(message, 'var(--danger)');
      renderScoreState('player', 'error', {
        message,
        replace: !hadContent,
        actionLabel: '重试',
        action: () => openPlayer(playerName),
      });
      return null;
    } finally {
      if (requestRevision === state.playerRequestRevision) {
        document.getElementById('score-player-surface')?.removeAttribute?.('aria-busy');
      }
    }
  }

  function openRuleEditor() {
    renderScoreState('rule', 'idle');
    const presets = state.rules?.presets || {};
    const select = document.getElementById('score-rule-preset');
    select.innerHTML = Object.keys(presets).map(key =>
      `<option value="${escapeAttr(key)}">${esc(presetName(key))}</option>`
    ).join('');
    select.value = state.rules?.activeRule?.preset_key || 'alliance_contribution';
    renderRuleFields(
      state.rules?.activeRule?.config
      || presets[select.value]
      || {}
    );
    document.getElementById('score-rule-dialog').showModal();
  }

  function renderRuleFields(config) {
    document.getElementById('score-rule-fields').innerHTML = RULE_FIELDS.map(([key, label]) =>
      `<label>${label}<input type="number" step="0.1" data-score-rule-field="${key}" value="${Number(config[key] ?? 0)}"></label>`
    ).join('');
    updateFormulaPreview();
  }

  function updateFormulaPreview() {
    const rule = collectRule();
    document.getElementById('score-formula-preview').innerHTML =
      `战斗 = 出战×${rule.battleWeight} + 胜场×${rule.winWeight} + 平局×${rule.drawWeight} + 武勋÷${rule.gongxunDivisor}<br>`
      + `攻城 = 主力×${rule.mainCityWeight} + 拆迁×${rule.tearWeight} + 出勤×${rule.attendanceWeight}<br>`
      + `<b>示例：10战4胜2平、3000武勋、2主力1拆迁3出勤 = ${sampleScore(rule)}</b>`;
  }

  function clearPendingRuleTransaction() {
    state.pendingRuleId = null;
    state.pendingRuleVersion = null;
    state.ruleTransactionPhase = 'idle';
  }

  function retryRuleActivation() {
    if (!state.pendingRuleId) return null;
    return saveRule(true);
  }

  function isCurrentRuleTransaction(revision) {
    return revision === state.ruleTransactionRevision;
  }

  async function saveRule(activationOnly = false) {
    if (state.ruleSaving) return null;
    if (activationOnly && !state.pendingRuleId) return null;
    const transactionRevision = ++state.ruleTransactionRevision;
    state.ruleSaving = true;
    setRuleControlsBusy(true);
    renderScoreState('rule', 'loading', {message: '正在保存并激活规则…'});
    try {
      let result = null;
      if (!state.pendingRuleId) {
        state.ruleTransactionPhase = 'create';
        const config = collectRule();
        result = await apiFetch('/api/custom_scores/rules', {
          method: 'POST',
          headers: {'Content-Type': 'application/json'},
          body: JSON.stringify({
            season: season(),
            name: document.getElementById('score-rule-name').value.trim(),
            presetKey: document.getElementById('score-rule-preset').value,
            config,
          }),
        });
        if (!isCurrentRuleTransaction(transactionRevision)) return null;
        if (!result?.ok) throw new Error(result?.error || '规则保存失败');
        state.pendingRuleId = result.rule.id;
        state.pendingRuleVersion = result.rule.version;
      }
      if (!isCurrentRuleTransaction(transactionRevision)) return null;
      state.ruleTransactionPhase = 'activate';
      const activation = await apiFetch(
        `/api/custom_scores/rules/${state.pendingRuleId}/activate`,
        {method: 'POST'}
      );
      if (!isCurrentRuleTransaction(transactionRevision)) return null;
      if (!activation?.ok) throw new Error(activation?.error || '规则激活失败');
      const activatedVersion = state.pendingRuleVersion;
      clearPendingRuleTransaction();
      renderScoreState('rule', 'success', {message: `规则 v${activatedVersion} 已激活`});
      document.getElementById('score-rule-dialog').close();
      showToast(`规则 v${activatedVersion} 已激活`);
      await load();
      return result || {ok: true, rule: {id: null, version: activatedVersion}};
    } catch (error) {
      if (!isCurrentRuleTransaction(transactionRevision)) return null;
      const message = error?.message || '规则保存失败';
      if (!state.pendingRuleId) clearPendingRuleTransaction();
      showToast(message, 'var(--danger)');
      renderScoreState('rule', 'error', {
        message,
        replace: false,
        actionLabel: '重试',
        action: state.ruleTransactionPhase === 'activate'
          ? retryRuleActivation
          : saveRule,
      });
      return null;
    } finally {
      if (isCurrentRuleTransaction(transactionRevision)) {
        state.ruleSaving = false;
        setRuleControlsBusy(false);
        document.getElementById('score-rule-surface')?.removeAttribute?.('aria-busy');
      }
    }
  }

  async function preview() {
    const requestRevision = ++state.previewRequestRevision;
    const hadPreview = Boolean(state.preview?.previewToken);
    const dialog = document.getElementById('score-preview-dialog');
    if (!hadPreview) {
      document.getElementById('score-preview-summary').innerHTML = '';
      document.getElementById('score-preview-rows').innerHTML = '';
    }
    if (dialog && !dialog.open) dialog.showModal();
    setActionBusy('score-preview-open', true);
    setActionBusy('score-preview-confirm', true);
    renderScoreState('preview', hadPreview ? 'refreshing' : 'loading');
    try {
      const result = await apiFetch('/api/custom_scores/preview', {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify(requestBody()),
      });
      if (!result?.ok) throw new Error(result?.error || '预览失败');
      if (requestRevision !== state.previewRequestRevision) return null;
      state.preview = result;
      document.getElementById('score-preview-summary').innerHTML =
        `<div class="score-preview-grid analysis-fact-grid">${[
          ['参评人数', result.summary.players],
          ['综合积分', result.summary.scoreTotal],
          ['战斗贡献', result.summary.battleTotal],
          ['攻城贡献', result.summary.siegeTotal],
        ].map(item => `<div class="score-preview-item"><span>${item[0]}</span><b>${formatScore(item[1])}</b></div>`).join('')}</div>`;
      document.getElementById('score-preview-rows').innerHTML =
        (result.rows || []).slice(0, 30).map(row =>
          `<div class="score-adjustment-row"><span>#${row.newRank} ${esc(row.playerName)} <small>原 #${row.oldRank || '-'}</small></span><b class="score-delta ${row.scoreDelta >= 0 ? 'up' : 'down'}">${signed(row.scoreDelta)} · ${row.rankDelta >= 0 ? '↑' : '↓'}${Math.abs(row.rankDelta)}</b></div>`
        ).join('');
      renderScoreState('preview', 'success');
      return result;
    } catch (error) {
      if (requestRevision !== state.previewRequestRevision) return null;
      const message = error?.message || '预览失败';
      showToast(message, 'var(--danger)');
      renderScoreState('preview', 'error', {
        message,
        replace: !hadPreview,
        actionLabel: '重试',
        action: preview,
      });
      return null;
    } finally {
      if (requestRevision === state.previewRequestRevision) {
        setActionBusy('score-preview-open', false);
        setActionBusy(
          'score-preview-confirm',
          false,
          !state.preview?.previewToken
        );
        document.getElementById('score-preview-surface')?.removeAttribute?.('aria-busy');
      }
    }
  }

  async function confirmRecalculation() {
    if (state.recalculating) return null;
    if (!state.preview?.previewToken) {
      setActionBusy('score-preview-confirm', false);
      setActionBusy('score-preview-open', false);
      document.getElementById('score-preview-surface')?.removeAttribute?.('aria-busy');
      state.recalculating = false;
      return null;
    }
    state.recalculating = true;
    const previewToken = state.preview.previewToken;
    const payload = {...requestBody(), previewToken};
    setActionBusy('score-preview-confirm', true);
    setActionBusy('score-preview-open', true);
    renderScoreState('preview', 'refreshing', {
      message: '正在确认并写入积分…',
    });
    try {
      const previousRows = new Map(
        (state.data?.rows || []).map(row => [
          row.playerName,
          {score: Number(row.score || 0), rank: Number(row.rank || 0)},
        ])
      );
      const result = await apiFetch('/api/custom_scores/recalc', {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify(payload),
      });
      if (!result?.ok) {
        throw new Error(result?.error || '重算失败，请重新预览');
      }
      document.getElementById('score-preview-dialog').close();
      state.preview = null;
      showToast(`积分已更新：${result.updated} 人`);
      state.previousRows = previousRows;
      await load();
      window.HudSystem?.emit({
        type: "score:recalculated",
        target: "#score-board",
        domain: "analysis",
        severity: "success",
        value: Number(result.updated || 0),
        message: `已更新 ${Number(result.updated || 0)} 名成员`,
        timestamp: Date.now(),
        dedupeKey: `score:${result.ruleVersion}:${result.updated}`,
      });
      return result;
    } catch (error) {
      const message = error?.message || '重算失败，请重新预览';
      showToast(message, 'var(--danger)');
      renderScoreState('preview', 'error', {
        message,
        replace: false,
        actionLabel: '重试',
        action: confirmRecalculation,
      });
      return null;
    } finally {
      state.recalculating = false;
      setActionBusy('score-preview-confirm', false);
      setActionBusy('score-preview-open', false);
      document.getElementById('score-preview-surface')?.removeAttribute?.('aria-busy');
    }
  }

  function openAdjustment(playerName) {
    renderScoreState('adjustment', 'idle');
    document.getElementById('score-adjustment-player').value = playerName;
    document.getElementById('score-adjustment-points').value = '';
    document.getElementById('score-adjustment-reason').value = '';
    document.getElementById('score-adjustment-dialog').showModal();
  }

  async function addAdjustment() {
    if (state.adjustmentSaving) return null;
    state.adjustmentSaving = true;
    const playerName = document.getElementById('score-adjustment-player').value;
    setActionBusy('score-adjustment-save', true);
    renderScoreState('adjustment', 'loading', {
      message: '正在保存手动调整…',
    });
    try {
      const result = await apiFetch('/api/custom_scores/adjustments', {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({
          season: season(),
          playerName,
          points: Number(document.getElementById('score-adjustment-points').value),
          reason: document.getElementById('score-adjustment-reason').value.trim(),
        }),
      });
      if (!result?.ok) {
        throw new Error(result?.error || '调整保存失败');
      }
      document.getElementById('score-adjustment-dialog').close();
      showToast('手动调整已保存');
      await Promise.all([openPlayer(playerName), load()]);
      return result;
    } catch (error) {
      const message = error?.message || '调整保存失败';
      showToast(message, 'var(--danger)');
      renderScoreState('adjustment', 'error', {
        message,
        replace: false,
        actionLabel: '重试',
        action: addAdjustment,
      });
      return null;
    } finally {
      state.adjustmentSaving = false;
      setActionBusy('score-adjustment-save', false);
      document.getElementById('score-adjustment-surface')?.removeAttribute?.('aria-busy');
    }
  }

  function bindOnce() {
    if (state.initialized) return;
    state.initialized = true;
    document.querySelectorAll('[data-score-board]').forEach(button => {
      button.addEventListener('click', () => switchBoard(button.dataset.scoreBoard));
    });
    document.getElementById('score-board-body')?.addEventListener('click', event => {
      const player = event.target.closest('[data-score-player]')?.dataset.scorePlayer;
      if (player) openPlayer(player);
    });
    document.getElementById('score-player-content')?.addEventListener('click', event => {
      if (event.target.closest('[data-score-action="open-adjustment"]')) {
        openAdjustment(state.selectedPlayer);
      }
    });
    document.getElementById('score-rule-preset')?.addEventListener('change', event => {
      if (state.ruleSaving) return;
      clearPendingRuleTransaction();
      renderRuleFields(state.rules?.presets?.[event.target.value] || {});
    });
    document.getElementById('score-rule-name')?.addEventListener('input', () => {
      if (state.ruleSaving) return;
      clearPendingRuleTransaction();
    });
    document.getElementById('score-rule-fields')?.addEventListener('input', () => {
      if (state.ruleSaving) return;
      clearPendingRuleTransaction();
      updateFormulaPreview();
    });
    document.getElementById('score-rule-dialog')?.addEventListener('cancel', event => {
      if (state.ruleSaving) {
        event.preventDefault();
        return;
      }
      clearPendingRuleTransaction();
    });
    document.getElementById('score-rule-dialog')?.addEventListener('close', () => {
      if (state.ruleSaving) return;
      clearPendingRuleTransaction();
    });
    document.getElementById('score-date-preset')?.addEventListener('change', event => {
      applyDatePreset(event.target.value);
    });
  }

  function renderRuleState() {
    const active = state.rules?.activeRule;
    document.getElementById('score-rule-version').textContent =
      active ? `${active.name} · v${active.version}` : '尚未激活规则';
  }

  function currentQuery() {
    const params = new URLSearchParams({
      season: season(),
      board: state.board,
      union: document.getElementById('score-union')?.value || '',
      group: document.getElementById('score-group')?.value || '',
    });
    return params.toString();
  }

  function requestBody() {
    return {
      season: season(),
      union: document.getElementById('score-union')?.value || '',
      group: document.getElementById('score-group')?.value || '',
      startDate: document.getElementById('score-start-date')?.value || '',
      endDate: document.getElementById('score-end-date')?.value || '',
    };
  }

  function applyDatePreset(value) {
    const start = document.getElementById('score-start-date');
    const end = document.getElementById('score-end-date');
    if (!start || !end) return;
    if (!value || value === 'all') {
      start.value = '';
      end.value = '';
      return;
    }
    const today = new Date();
    let begin = new Date(today);
    if (value === '7d') begin.setDate(begin.getDate() - 6);
    else if (value === 'month') begin = new Date(today.getFullYear(), today.getMonth(), 1);
    start.value = localDate(begin);
    end.value = localDate(today);
  }

  function localDate(value) {
    const year = value.getFullYear();
    const month = String(value.getMonth() + 1).padStart(2, '0');
    const day = String(value.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  }

  function collectRule() {
    const rule = {};
    document.querySelectorAll('[data-score-rule-field]').forEach(input => {
      rule[input.dataset.scoreRuleField] = Number(input.value);
    });
    return rule;
  }

  function sampleScore(rule) {
    return formatScore(
      10 * rule.battleWeight + 4 * rule.winWeight + 2 * rule.drawWeight
      + 3000 / Math.max(1, rule.gongxunDivisor)
      + 2 * rule.mainCityWeight + rule.tearWeight + 3 * rule.attendanceWeight
    );
  }

  function boardValue(row) {
    return state.board === 'battle'
      ? row.battleScore
      : state.board === 'siege'
        ? row.siegeScore
        : row.score;
  }

  function season() {
    return document.getElementById('score-season')?.value.trim() || 'current';
  }

  function detailCard(label, value) {
    return `<div class="score-component"><span>${esc(label)}</span><b>${esc(value ?? '-')}</b></div>`;
  }

  function formatScore(value) {
    return Number(value || 0).toLocaleString('zh-CN', {maximumFractionDigits: 2});
  }

  function signed(value) {
    const number = Number(value || 0);
    return `${number > 0 ? '+' : ''}${formatScore(number)}`;
  }

  function presetName(key) {
    return ({
      alliance_contribution: '同盟综合贡献',
      season_reward: '赛季奖励分配',
      siege_priority: '打城排班优先',
    })[key] || key;
  }

  function normalizeDataCompleteness(value) {
    const normalized = String(value || '').trim().toLowerCase();
    if (['complete', 'partial', 'missing'].includes(normalized)) {
      return normalized;
    }
    return 'partial';
  }

  function escapeAttr(value) {
    return String(value || '').replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/'/g, '&#39;');
  }

  window.ScoreCenter = {
    load,
    switchBoard,
    openPlayer,
    openRuleEditor,
    saveRule,
    preview,
    confirmRecalculation,
    openAdjustment,
    addAdjustment,
    state,
  };
})();
