# -*- coding: utf-8 -*-
code = r"""

function simAddHero(camp) {
  if (_simState[camp].length >= 3) { showToast('最多3名武将', 'var(--gold)'); return; }
  if (!_simHeroes || !_simHeroes.length) { showToast('武将数据未加载', 'var(--red)'); return; }
  _simState[camp].push({ id: _simHeroes[0].id, level: 40, up: 5, equip_skills: [0, 0] });
  _renderCards(camp);
}

function simRemoveHero(camp, idx) {
  if (_simState[camp].length <= 1) { showToast('至少保留1名武将', 'var(--gold)'); return; }
  _simState[camp].splice(idx, 1);
  _renderCards(camp);
}

function simChangeHero(camp, idx, hid) {
  _simState[camp][idx].id = hid;
  _simState[camp][idx].equip_skills = [0, 0];
  _renderCards(camp);
}

function simSetProp(camp, idx, key, val) { _simState[camp][idx][key] = val; }

function simSetSkillSlot(camp, idx, slot, sid) {
  while (_simState[camp][idx].equip_skills.length < 2)
    _simState[camp][idx].equip_skills.push(0);
  _simState[camp][idx].equip_skills[slot] = sid;
}

function _buildConfig() {
  const mapHero = h => ({
    id: h.id, level: h.level, up: h.up,
    equip_skills: (h.equip_skills || []).filter(s => s > 0),
    extra_attrs: {}
  });
  return {
    blue: { morale: +document.getElementById('sim-blue-morale').value || 100, heros: _simState.blue.map(mapHero) },
    red:  { morale: +document.getElementById('sim-red-morale').value  || 100, heros: _simState.red.map(mapHero) }
  };
}

async function runSimulate(repeat) {
  if (!_simState.blue.length || !_simState.red.length) { showToast('请配置双方阵容', 'var(--red)'); return; }
  const statusEl = document.getElementById('sim-status');
  statusEl.textContent = '模拟中...';
  statusEl.style.color = '#c8a044';
  ['sim-btn-single','sim-btn-100','sim-btn-1000'].forEach(id => {
    const el = document.getElementById(id); if (el) el.disabled = true;
  });
  const r = await apiFetch('/api/simulate', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({..._buildConfig(), repeat})
  });
  ['sim-btn-single','sim-btn-100','sim-btn-1000'].forEach(id => {
    const el = document.getElementById(id); if (el) el.disabled = false;
  });
  if (!r || !r.ok) {
    statusEl.textContent = '模拟失败: ' + (r && r.error || '未知错误');
    statusEl.style.color = '#e05050';
    showToast('模拟失败', 'var(--red)'); return;
  }
  statusEl.textContent = repeat === 1 ? '战斗结束' : `完成 ${repeat} 次`;
  statusEl.style.color = '#46b06e';
  if (repeat === 1) _renderSingleResult(r.result);
  else _renderMultiResult(r);
  _updateHPBars(r, repeat);
}

function _updateHPBars(r, repeat) {
  // 更新顶部血条
  const blueTotal = repeat === 1 ? (r.result.blue.total_arms + (r.result.blue.hurt_arms||0)) : 1;
  const redTotal  = repeat === 1 ? (r.result.red.total_arms  + (r.result.red.hurt_arms||0))  : 1;
  const blueArms  = repeat === 1 ? r.result.blue.total_arms : 0;
  const redArms   = repeat === 1 ? r.result.red.total_arms  : 0;
  const bPct = blueTotal > 0 ? Math.round(blueArms/blueTotal*100) : 0;
  const rPct = redTotal  > 0 ? Math.round(redArms/redTotal*100)   : 0;
  const bEl = document.getElementById('sim-blue-hpbar');
  const rEl = document.getElementById('sim-red-hpbar');
  const btEl = document.getElementById('sim-blue-hp-text');
  const rtEl = document.getElementById('sim-red-hp-text');
  if (bEl) bEl.style.width = bPct + '%';
  if (rEl) rEl.style.width = rPct + '%';
  if (btEl && repeat===1) btEl.textContent = blueArms + '/' + blueTotal;
  if (rtEl && repeat===1) rtEl.textContent = redArms  + '/' + redTotal;
}

function _renderSingleResult(res) {
  document.getElementById('sim-stat-box').style.display = 'block';
  document.getElementById('sim-log-box').style.display  = 'block';
  const isBlue = res.winner.includes('攻方');
  const isRed  = res.winner.includes('守方');
  const winColor = isBlue ? '#5aaaf0' : isRed ? '#e05050' : '#c8a044';
  const winLabel = isBlue ? '攻方胜利' : isRed ? '守方胜利' : '平  局';

  const renderSide = (side, label, color) => {
    const cards = side.heros.map(hero => {
      const total = hero.arms + (hero.hurt||0);
      const pct   = total > 0 ? Math.round(hero.arms / total * 100) : 0;
      const hdata = (_simHeroes||[]).find(h => h.name === hero.name) || {};
      const imgUrl = hdata.id ? _simImgUrl(hdata.id) : '';
      const alive  = hero.arms > 0;
      const cc     = SIM_CAMP_COLOR[hdata.camp] || color;
      return `<div style="position:relative;background:linear-gradient(160deg,#0d1520,#080c14);
        border:1.5px solid ${alive?cc:'#2a3a4a'};border-radius:5px;overflow:hidden;
        opacity:${alive?1:0.5};display:flex;gap:0;min-width:0">
        <div style="height:2px;position:absolute;top:0;left:0;right:0;background:${alive?cc:'#2a3a4a'}"></div>
        ${imgUrl?`<img src="${imgUrl}" style="width:42px;height:56px;object-fit:cover;object-position:left top;flex-shrink:0;margin-top:2px" onerror="this.style.display='none'">`:''}
        <div style="flex:1;padding:6px 8px;min-width:0">
          <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:3px">
            <span style="font-size:.75rem;font-weight:700;color:#e4dd9e">${esc(hero.pos)} · ${esc(hero.name)}</span>
            <span style="font-size:.6rem;padding:1px 5px;border-radius:2px;
              background:${alive?'#46b06e22':'#e0505022'};
              color:${alive?'#46b06e':'#e05050'}">${alive?'存活':'阵亡'}</span>
          </div>
          <div style="font-size:.65rem;color:#7a8a9a;margin-bottom:4px">
            兵力 <b style="color:#c0d8f0">${fmt(hero.arms)}</b>
            <span style="margin-left:8px">伤兵 <b style="color:#c8a044">${fmt(hero.hurt||0)}</b></span>
          </div>
          <div style="height:3px;background:#0a1020;border-radius:2px;overflow:hidden">
            <div style="height:100%;width:${pct}%;background:${cc};transition:width .8s ease"></div>
          </div>
        </div>
      </div>`;
    }).join('');
    return `<div style="flex:1;min-width:180px">
      <div style="font-size:.68rem;color:${color};margin-bottom:6px;letter-spacing:.1em
        ;display:flex;align-items:center;gap:5px">
        <span style="display:inline-block;width:3px;height:10px;background:${color};border-radius:1px"></span>${label}
      </div>
      <div style="display:flex;flex-direction:column;gap:5px">${cards}</div>
    </div>`;
  };

  document.getElementById('sim-stat-cards').innerHTML = `
    <div style="width:100%;display:flex;flex-direction:column;gap:8px">
      <div style="display:flex;align-items:center;justify-content:center;padding:10px;
        background:linear-gradient(135deg,${winColor}18,${winColor}08);
        border:1px solid ${winColor}44;border-radius:6px">
        <span style="font-size:1rem;font-weight:700;color:${winColor};
          letter-spacing:.3em;text-shadow:0 0 12px ${winColor}60">${winLabel}</span>
        <span style="font-size:.7rem;color:#7a8a9a;margin-left:14px">第${res.rounds_played}回合</span>
      </div>
      <div style="display:flex;gap:10px;flex-wrap:wrap">
        ${renderSide(res.blue,'⚔ 攻方','#5aaaf0')}
        ${renderSide(res.red, '🛡 守方','#e05050')}
      </div>
    </div>
  `;

  const logEl = document.getElementById('sim-log-content');
  logEl.innerHTML = (res.records||[]).map(line => {
    let c = '#4a5a6a';
    if (line.includes('====='))                                                           c = '#e4dd9e';
    else if (line.includes('发动')||line.includes('追击')||line.includes('效果'))        c = '#3ab8c8';
    else if (line.includes('损失')||line.includes('阵亡')||line.includes('混乱'))        c = '#e06060';
    else if (line.includes('恢复'))                                                       c = '#46b06e';
    else if (line.includes('【'))                                                         c = '#c0c8d0';
    return `<div style="color:${c};padding:1px 0;border-bottom:1px solid #0d1520">${esc(line)}</div>`;
  }).join('');
  logEl.scrollTop = 0;
}

function _renderMultiResult(r) {
  document.getElementById('sim-stat-box').style.display = 'block';
  document.getElementById('sim-log-box').style.display  = 'none';
  const topColor = r.blue_rate > r.red_rate ? '#5aaaf0' : r.red_rate > r.blue_rate ? '#e05050' : '#c8a044';
  document.getElementById('sim-stat-cards').innerHTML = `
    <div style="width:100%;display:flex;flex-direction:column;gap:8px">
      <div style="display:flex;gap:8px;flex-wrap:wrap">
        <div style="flex:1;min-width:100px;background:#0d1520;border:1px solid #5aaaf044;
          border-radius:5px;padding:10px;text-align:center">
          <div style="font-size:1.4rem;font-weight:700;color:#5aaaf0">${r.blue_rate}%</div>
          <div style="font-size:.65rem;color:#5a7a9a;margin-top:3px">攻方胜率 (${r.blue_wins}次)</div>
        </div>
        <div style="flex:1;min-width:100px;background:#0d1520;border:1px solid #e0505044;
          border-radius:5px;padding:10px;text-align:center">
          <div style="font-size:1.4rem;font-weight:700;color:#e05050">${r.red_rate}%</div>
          <div style="font-size:.65rem;color:#7a5a5a;margin-top:3px">守方胜率 (${r.red_wins}次)</div>
        </div>
        <div style="flex:1;min-width:100px;background:#0d1520;border:1px solid #c8a04444;
          border-radius:5px;padding:10px;text-align:center">
          <div style="font-size:1.4rem;font-weight:700;color:#c8a044">${r.draw_rate}%</div>
          <div style="font-size:.65rem;color:#7a7a5a;margin-top:3px">平局 (${r.draws}次)</div>
        </div>
      </div>
      <div style="background:#060a10;border-radius:4px;overflow:hidden;height:28px;display:flex;gap:1px">
        <div style="flex:${r.blue_wins||0.01};background:linear-gradient(90deg,#3a6eb0,#5aaaf0);
          display:flex;align-items:center;justify-content:center;
          font-size:.7rem;color:#fff;font-weight:700">
          ${r.blue_rate > 8 ? r.blue_rate+'%' : ''}
        </div>
        <div style="flex:${r.draws||0.01};background:linear-gradient(90deg,#806020,#c8a044);
          display:flex;align-items:center;justify-content:center;font-size:.65rem;color:#fff">
          ${r.draw_rate > 8 ? r.draw_rate+'%' : ''}
        </div>
        <div style="flex:${r.red_wins||0.01};background:linear-gradient(90deg,#c04040,#e05050);
          display:flex;align-items:center;justify-content:center;
          font-size:.7rem;color:#fff;font-weight:700">
          ${r.red_rate > 8 ? r.red_rate+'%' : ''}
        </div>
      </div>
      <div style="display:flex;justify-content:space-between;font-size:.62rem;color:#4a5a6a;padding:0 4px">
        <span style="color:#5aaaf0">⚔ 攻方</span><span>平局</span><span style="color:#e05050">守方 🛡</span>
      </div>
    </div>
  `;
}

function simToggleLog() {
  const box = document.getElementById('sim-log-content');
  const btn = document.getElementById('sim-log-toggle');
  if (!box || !btn) return;
  const hidden = box.style.display === 'none';
  box.style.display = hidden ? '' : 'none';
  btn.textContent = hidden ? '收起' : '展开';
}
"""
with open('static/sim.js', 'a', encoding='utf-8') as f:
    f.write(code)
print('part3 ok')

