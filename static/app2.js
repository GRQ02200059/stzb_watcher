// Battle Monitor
let _bmHistory = [];
let _bmSeenKeys = new Set();
let _bmSearchText = '';
const BM_HISTORY_LIMIT = 80;

function battleMonitorKey(r){
  return [
    r?.latest_file || r?.source_file || '',
    r?.updated_at || '',
    r?.plain_text || r?.packet_text || r?.raw_text || '',
    r?.packet?.marker || 0,
  ].join('||');
}

function battleMonitorRowsHtml(items){
  const nowSec = Math.floor(Date.now() / 1000);
  const fmtTs = (ts)=> ts ? new Date(ts * 1000).toLocaleTimeString('zh-CN', {hour12:false}) : '-';
  const fmtRemain = (ts)=>{
    if(!ts) return '-';
    const diff = ts - nowSec;
    if(diff <= 0) return '已到达';
    const m = Math.floor(diff / 60);
    const s = diff % 60;
    return `${m}分${String(s).padStart(2,'0')}秒`;
  };
  const moveTypeText = (t)=> ({0:'待命',1:'驻守',2:'回撤',3:'调动',4:'行军',5:'集结'}[Number(t)] || `类型${t||0}`);
  const skillMap = typeof SKILL_CFG!=='undefined' ? SKILL_CFG : (typeof SKILL_MAP!=='undefined' ? SKILL_MAP : {});
  const renderHeroList = (ids)=> (Array.isArray(ids) ? ids : []).map(hid=>{
    const hcfg = (typeof HERO_CFG!=='undefined') ? (HERO_CFG[hid]||HERO_CFG[String(hid)]||{}) : {};
    return esc(hcfg.name || `武将${hid}`);
  }).join(' / ') || '-';

  if(!items.length){
    return `<div style='color:var(--text2);padding:14px 2px'>最新 13a4 已捕获，但未在战报库里匹配到队伍阵容</div>`;
  }

  const rows = items.map(item=>{
    const ownerName = esc(item.owner_name || '未知');
    const ownerUnion = esc(item.owner_union || '');
    const fromText = item.from_xy ? `<span style='color:var(--text2)'>( ${esc(item.from_xy||'')} )</span>`.replace('( ','(').replace(' )',')') : '-';
    const toText = item.to_xy ? `<span style='color:var(--text2)'>( ${esc(item.to_xy||'')} )</span>`.replace('( ','(').replace(' )',')') : '-';
    const currentText = item.current_xy ? `<span style='color:var(--text2)'>( ${esc(item.current_xy||'')} )</span>`.replace('( ','(').replace(' )',')') : '-';
    const lineup = item.lineup || {};
    const teamStats = item.team_stats || {};
    const teamMatchups = item.team_matchups || {};
    const recentBattles = Array.isArray(teamMatchups.recent_battles) ? teamMatchups.recent_battles : [];
    const favoredMatchups = Array.isArray(teamMatchups.favored) ? teamMatchups.favored : [];
    const counteredMatchups = Array.isArray(teamMatchups.countered) ? teamMatchups.countered : [];
    const teamBattles = Number(teamStats.battles || 0);
    const teamWinRate = Number(teamStats.win_rate || 0);
    const teamWins = Number(teamStats.wins || 0);
    const teamDraws = Number(teamStats.draws || 0);
    const teamLoses = Number(teamStats.loses || 0);
    const teamRateColor = teamBattles <= 0 ? 'var(--text2)' : (teamWinRate >= 60 ? 'var(--green)' : teamWinRate >= 40 ? 'var(--gold)' : 'var(--red)');
    const recentHtml = recentBattles.length ? recentBattles.map(rb=>`<div style='display:grid;grid-template-columns:20px minmax(0,1fr) auto;gap:8px;align-items:center;margin-top:4px'>
      <span style='color:${rb.result_text === '胜' ? 'var(--green)' : rb.result_text === '负' ? 'var(--red)' : 'var(--gold)'};text-align:left'>${rb.result_text}</span>
      <span style='min-width:0;color:#c7d3df;white-space:nowrap;overflow:hidden;text-overflow:ellipsis'>${esc(rb.opponent_name || '未知对手')} · ${renderHeroList(rb.opponent_hero_ids)}</span>
      <span style='color:var(--text2);white-space:nowrap'>${esc((rb.time_str || '').slice(5) || '-')}</span>
    </div>`).join('') : `<div style='margin-top:4px;color:var(--text2)'>暂无最近战绩</div>`;
    const favoredHtml = favoredMatchups.length ? favoredMatchups.map(m=>`<div style='margin-top:4px;color:#c7d3df'>${renderHeroList(m.opponent_hero_ids)} <span style='color:var(--green)'>${m.wins}胜</span><span style='color:var(--text2)'> / ${m.total}战 · ${m.win_rate}%</span></div>`).join('') : `<div style='margin-top:4px;color:var(--text2)'>暂无明显克制阵容</div>`;
    const counteredHtml = counteredMatchups.length ? counteredMatchups.map(m=>`<div style='margin-top:4px;color:#c7d3df'>${renderHeroList(m.opponent_hero_ids)} <span style='color:var(--red)'>${m.loses}负</span><span style='color:var(--text2)'> / ${m.total}战 · ${m.win_rate}%</span></div>`).join('') : `<div style='margin-top:4px;color:var(--text2)'>暂无明显被克制阵容</div>`;
    const teamInsightHtml = `<div style='padding:12px 14px;border:1px solid #223446;border-radius:10px;background:linear-gradient(180deg,#0d1723 0%,#0a111a 100%);height:100%;box-sizing:border-box'>
      <div style='display:grid;grid-template-columns:minmax(240px,1.18fr) repeat(2,minmax(165px,1fr));gap:12px;align-items:start'>
        <div>
          <div style='font-size:.75rem;color:var(--gold);letter-spacing:.08em'>最近几场战绩</div>
          <div style='margin-top:6px;font-size:.72rem;line-height:1.5'>${recentHtml}</div>
        </div>
        <div>
          <div style='font-size:.75rem;color:var(--green);letter-spacing:.08em'>更克制的阵容</div>
          <div style='margin-top:6px;font-size:.72rem;line-height:1.5'>${favoredHtml}</div>
        </div>
        <div>
          <div style='font-size:.75rem;color:var(--red);letter-spacing:.08em'>更被克制的阵容</div>
          <div style='margin-top:6px;font-size:.72rem;line-height:1.5'>${counteredHtml}</div>
        </div>
      </div>
    </div>`;
    const heroesHtml = (lineup.heroes||[]).length
      ? `<div style='display:flex;gap:10px;flex-wrap:wrap;align-items:stretch'>${lineup.heroes.map(h=>{
          const hcfg = (typeof HERO_CFG!=='undefined') ? (HERO_CFG[h.hero_id]||HERO_CFG[String(h.hero_id)]||{}) : {};
          const hname = esc(hcfg.name || `武将${h.hero_id}`);
          const country = esc(hcfg.country || '');
          const htype = esc(hcfg.type || '');
          const iconId = hcfg.iconId || h.hero_id;
          const imgUrl = `https://g0.gph.netease.com/ngsocial/community/stzb/cn/cards/cut/card_medium_${iconId}.jpg?gameid=g10`;
          const starCount = Math.max(0, Number(h.star||0));
          const stars = starCount > 0 ? '★'.repeat(starCount) : '—';
          const starColor = starCount >= 5 ? 'var(--gold)' : 'var(--text2)';
          const starText = `进阶${starCount}`;
          const skillHtml = (h.skills||[]).length
            ? h.skills.map(s=>{
                const sc = skillMap[String(s.skill_id)] || skillMap[s.skill_id] || {};
                const sname = esc(sc.name || `技能${s.skill_id}`);
                return `<div style='display:flex;align-items:center;gap:4px;color:#8fd3ff;font-size:.72rem;line-height:1.45'><span style='color:#5b6f86'>▸</span><span>${sname}${s.level?` Lv${s.level}`:''}</span></div>`;
              }).join('')
            : `<div style='color:var(--text2);font-size:.7rem'>无战法</div>`;
          return `<div style='flex:1 1 180px;max-width:220px;min-width:180px;background:linear-gradient(180deg,#101a27 0%,#0c1420 100%);border:1px solid #2a3d52;border-radius:10px;padding:10px 10px 9px 10px;box-shadow:inset 0 1px 0 #ffffff08'>
            <div style='display:flex;align-items:flex-start;gap:10px'>
              <img src='${imgUrl}' style='width:54px;height:54px;border-radius:8px;object-fit:cover;object-position:left top;border:1px solid #3c4e62;background:#0a1018;flex-shrink:0' onerror='this.style.display="none"'>
              <div style='flex:1;min-width:0'>
                <div style='display:flex;align-items:flex-start;justify-content:space-between;gap:8px'>
                  <div style='min-width:0'>
                    <div style='font-size:1rem;font-weight:700;color:#d8c89a;line-height:1.1;white-space:nowrap;overflow:hidden;text-overflow:ellipsis'>${hname}</div>
                    <div style='font-size:.72rem;color:#e36b6b;margin-top:3px'>${country}${country&&htype?' · ':''}${htype} Lv.${h.level||0}</div>
                  </div>
                  <div style='font-size:.76rem;color:${starColor};white-space:nowrap;flex-shrink:0;text-align:right'>
                    <div>${stars}</div>
                    <div style='font-size:.66rem;color:var(--text2);margin-top:2px'>${starText}</div>
                  </div>
                </div>
                <div style='margin-top:7px;border-top:1px solid #213246;padding-top:6px;display:flex;flex-direction:column;gap:2px'>${skillHtml}</div>
              </div>
            </div>
          </div>`;
        }).join('')}</div>`
      : `<span style='color:var(--text2)'>未匹配到武将战法</span>`;

    return `<div style='padding:12px 0;border-top:1px solid #16202c'>
      <div style='display:flex;align-items:flex-start;justify-content:space-between;gap:12px;flex-wrap:wrap'>
        <div>
          <div style='display:flex;align-items:center;gap:10px;flex-wrap:wrap'>
            <span style='font-family:Share Tech Mono,monospace;color:var(--gold);font-size:.96rem'>队伍 ${item.team_id||''}</span>
            <span style='color:var(--cyan);font-size:.74rem'>${moveTypeText(item.move_type)}</span>
            <span style='color:var(--text2);font-size:.72rem'>主体ID ${item.subject_id||0}</span>
          </div>
          <div style='margin-top:6px'><b>${ownerName}</b><span style='color:var(--text2);font-size:.72rem'> #${item.owner_uid||''}</span>${ownerUnion?` <span style='color:var(--text2)'>· ${ownerUnion}</span>`:''}</div>
        </div>
        <div style='text-align:right'>
          <div style='font-size:.72rem;color:var(--text2)'>到达 ${fmtTs(item.arrive_time)}</div>
          <div style='color:var(--gold);font-size:.72rem'>${fmtRemain(item.arrive_time)}</div>
        </div>
      </div>
      <div style='display:grid;grid-template-columns:repeat(2,minmax(220px,1fr)) 1.1fr;gap:10px 14px;align-items:stretch;font-size:.74rem;margin-top:10px'>
        <div style='display:flex;flex-direction:column;justify-content:center;padding:6px 10px;border:1px solid ${teamBattles > 0 ? '#32465a' : '#233243'};border-radius:10px;background:${teamBattles > 0 ? '#132131' : '#101923'};min-height:46px'>
          <span style='color:var(--text2);font-size:.66rem;letter-spacing:.04em'>队伍胜率</span>
          <span style='color:${teamRateColor};margin-top:4px;font-weight:700'>${teamBattles > 0 ? `${teamWinRate}%` : '-'}</span>
        </div>
        <div style='display:flex;flex-direction:column;justify-content:center;padding:6px 10px;border:1px solid #1d2a39;border-radius:10px;background:#0e1724;min-height:46px'>
          <span style='color:var(--text2);font-size:.66rem;letter-spacing:.04em'>到达 / 剩余</span>
          <span style='margin-top:4px'><span style='font-family:Share Tech Mono,monospace'>${fmtTs(item.arrive_time)}</span><span style='color:var(--text2)'> · </span><span style='color:var(--gold)'>${fmtRemain(item.arrive_time)}</span></span>
        </div>
        <div style='display:flex;flex-direction:column;justify-content:center;padding:6px 10px;border:1px solid #1d2a39;border-radius:10px;background:#0e1724;min-height:46px'>
          <span style='color:var(--text2);font-size:.66rem;letter-spacing:.04em'>战绩摘要</span>
          <span style='color:var(--text);margin-top:4px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis'>${teamBattles > 0 ? `${teamWins}胜/${teamDraws}平/${teamLoses}负 · ${teamBattles}战` : '暂无队伍战报'}</span>
        </div>
      </div>
      <div style='display:grid;grid-template-columns:repeat(4,minmax(140px,1fr));gap:10px;margin-top:10px'>
        <div><div style='color:var(--text2);font-size:.68rem'>出发地块</div><div style='font-family:Share Tech Mono,monospace'>${fromText}</div></div>
        <div><div style='color:var(--text2);font-size:.68rem'>目标地块</div><div style='font-family:Share Tech Mono,monospace'>${toText}</div></div>
        <div><div style='color:var(--text2);font-size:.68rem'>要塞位置</div><div style='font-family:Share Tech Mono,monospace'>${currentText}</div></div>
        <div><div style='color:var(--text2);font-size:.68rem'>武将数 / 速度</div><div style='font-family:Share Tech Mono,monospace'>${(lineup.heroes||[]).length} / ${item.speed||0}</div></div>
      </div>
      <div style='margin-top:10px'>
        <div style='color:var(--text2);font-size:.68rem;margin-bottom:4px'>武将与战法</div>
        <div>${heroesHtml}</div>
      </div>
      ${teamInsightHtml}
    </div>`;
  }).join('');

  return rows;
}

function renderBattleMonitorStream(){
  const stream = document.getElementById('bm-stream');
  const countEl = document.getElementById('bm-count');
  if(!stream) return;
  const q = _bmSearchText.trim().toLowerCase();
  const filteredHistory = !_bmHistory.length ? [] : _bmHistory.filter(item=>{
    if(!q) return true;
    const lead = item.lead || {};
    const text = [
      item.summaryText || '',
      item.file || '',
      item.plainText || '',
      lead.owner_name || '',
      lead.owner_union || '',
      lead.to_xy || '',
      ...(item.items || []).flatMap(x=>[
        String(x.team_id || ''),
        String(x.owner_name || ''),
        String(x.owner_union || ''),
        String(x.owner_uid || ''),
        String(x.to_xy || ''),
        String(x.from_xy || ''),
      ])
    ].join(' ').toLowerCase();
    return text.includes(q);
  });
  if(countEl) countEl.textContent = `${filteredHistory.length} / ${_bmHistory.length} 条监控记录`;
  if(!filteredHistory.length){
    stream.innerHTML = `<div class='feed-item'><div class='feed-time'>--:--:--</div><div class='feed-body' style='color:var(--text2)'>${_bmHistory.length ? '没有匹配到搜索结果' : '暂无监控历史'}</div></div>`;
    return;
  }
  stream.innerHTML = filteredHistory.map((item, idx)=>{
    const tm = item.updated_at ? item.updated_at.split(' ').pop() : '--:--:--';
    const lead = item.lead || {};
    const leadName = esc(lead.owner_name || '未知');
    const leadUnion = esc(lead.owner_union || '');
    const leadTarget = lead.to_wid ? `${lead.to_wid}${lead.to_xy ? ` (${esc(lead.to_xy)})` : ''}` : '-';
    const leadRemain = lead.arrive_time ? lead.remain_text : '-';
    return `<div style='margin-bottom:12px;border:1px solid var(--border);border-radius:8px;background:linear-gradient(135deg,var(--panel),var(--panel2));overflow:hidden'>
      <div style='padding:12px 14px;display:flex;align-items:center;justify-content:space-between;gap:12px;background:#0b1320;flex-wrap:wrap'>
        <div style='display:flex;align-items:center;gap:12px;flex-wrap:wrap'>
          <span style='font-family:var(--font-mono);color:var(--gold)'>${tm}</span>
          <b>${esc(item.summaryText || '无数据')}</b>
          <span style='color:var(--text2);font-size:.72rem;overflow:hidden;text-overflow:ellipsis;white-space:nowrap'>${esc(item.file || '')}</span>
        </div>
        <div style='display:flex;align-items:center;gap:14px;flex-wrap:wrap;font-size:.74rem'>
          <span><span style='color:var(--text2)'>玩家</span> <b>${leadName}</b></span>
          <span><span style='color:var(--text2)'>同盟</span> <span style='color:var(--cyan)'>${leadUnion || '-'}</span></span>
          <span><span style='color:var(--text2)'>目标</span> <span style='font-family:var(--font-mono)'>${leadTarget}</span></span>
          <span><span style='color:var(--text2)'>剩余</span> <span style='color:var(--gold)'>${leadRemain}</span></span>
        </div>
      </div>
      <div style='padding:12px 14px'>
        <div style='color:var(--text2);font-size:.76rem;line-height:1.6;word-break:break-all;white-space:normal;margin-bottom:10px'>${esc(item.plainText || '') || '<span style="color:var(--muted)">无 plain 文本</span>'}</div>
        ${battleMonitorRowsHtml(item.items || [])}
      </div>
    </div>`;
  }).join('');
}

function pushBattleMonitorHistory(r){
  if(!r || !r.ok) return;
  const key = battleMonitorKey(r);
  if(!key || _bmSeenKeys.has(key)) return;
  _bmSeenKeys.add(key);
  const plainText = (r.plain_text || r.packet_text || r.raw_text || '').trim();
  const items = (r.items || []).slice().sort((a,b)=>{
    const at = Number(a.arrive_time||0), bt = Number(b.arrive_time||0);
    if(at && bt) return at - bt;
    if(at) return -1;
    if(bt) return 1;
    return Number(a.team_id||0) - Number(b.team_id||0);
  });
  const teams = r.summary?.teams || items.length || 0;
  const members = r.summary?.matched_battles ?? r.summary?.matched_members ?? 0;
  const marker = r.packet?.marker || 0;
  const nowSec = Math.floor(Date.now() / 1000);
  const lead = items[0] ? {
    owner_name: items[0].owner_name || '',
    owner_union: items[0].owner_union || '',
    to_wid: items[0].to_wid || 0,
    to_xy: items[0].to_xy || '',
    arrive_time: items[0].arrive_time || 0,
    remain_text: !items[0].arrive_time ? '-' : ((items[0].arrive_time - nowSec) <= 0 ? '已到达' : `${Math.floor((items[0].arrive_time - nowSec)/60)}分${String((items[0].arrive_time - nowSec)%60).padStart(2,'0')}秒`),
  } : {};
  _bmHistory.unshift({
    key,
    updated_at: r.updated_at || '',
    file: r.latest_file || r.source_file || '',
    plainText,
    items,
    lead,
    summaryText: `标记 ${marker} · ${teams} 支队伍 · 阵容 ${members} 条`,
  });
  if(_bmHistory.length > BM_HISTORY_LIMIT){
    const removed = _bmHistory.splice(BM_HISTORY_LIMIT);
    removed.forEach(x=>_bmSeenKeys.delete(x.key));
  }
  renderBattleMonitorStream();
}

function searchBattleMonitor(){
  _bmSearchText = (document.getElementById('bm-search-input')?.value || '').trim();
  renderBattleMonitorStream();
}

function clearBattleMonitorSearch(){
  _bmSearchText = '';
  const input = document.getElementById('bm-search-input');
  if(input) input.value = '';
  renderBattleMonitorStream();
}

function ensureBattleMonitorControls(){
  const countEl = document.getElementById('bm-count');
  if(!countEl || document.getElementById('bm-search-wrap')) return;
  const wrap = document.createElement('div');
  wrap.id = 'bm-search-wrap';
  wrap.style.cssText = 'display:flex;gap:8px;align-items:center;flex-wrap:wrap;margin:8px 0 10px';
  wrap.innerHTML = `
    <input id='bm-search-input' placeholder='搜索队伍ID / 玩家 / 同盟 / 坐标' style='min-width:260px;padding:6px 10px;background:var(--panel2);border:1px solid var(--border);color:var(--text);border-radius:6px'>
    <button class='btn' onclick='searchBattleMonitor()'>搜索</button>
    <button class='btn' onclick='clearBattleMonitorSearch()'>清空</button>
  `;
  countEl.parentNode.insertBefore(wrap, countEl.nextSibling);
  const input = document.getElementById('bm-search-input');
  if(input){
    input.addEventListener('input', ()=>{
      _bmSearchText = input.value.trim();
      renderBattleMonitorStream();
    });
    input.addEventListener('keydown', (e)=>{
      if(e.key === 'Enter') searchBattleMonitor();
    });
  }
}

function renderBattleMonitor(r){
  if(!r || !r.ok) return;
  document.getElementById('bm-team-count').textContent = r.summary?.teams || 0;
  document.getElementById('bm-member-count').textContent = r.summary?.matched_battles ?? r.summary?.matched_members ?? 0;
  document.getElementById('bm-marker').textContent = r.packet?.marker || 0;
  document.getElementById('bm-state').textContent = Array.isArray(r.packet?.state) && r.packet.state.length ? r.packet.state.join('/') : '-';
  document.getElementById('bm-updated').textContent = r.updated_at ? `更新时间 ${r.updated_at}` : '实时事件';
  const plainText = (r.plain_text || r.packet_text || r.raw_text || '').trim();
  document.getElementById('bm-file').textContent = plainText || r.latest_file || r.source_file || '';
}

function fmtBm13Ts(ts){
  if(!ts) return '-';
  try{return new Date(Number(ts) * 1000).toLocaleTimeString('zh-CN',{hour12:false});}catch{return String(ts||'-');}
}

function renderBattleMonitor13a2(r){
  if(!r || !r.ok) return;
  window._bm13Data = r;
  const filterText = (document.getElementById('bm13-team-filter')?.value || '').trim().toLowerCase();
  const rows = (r.items || []).filter(item=>{
    if(!filterText) return true;
    return String(item.team_id||'').includes(filterText) || String(item.owner_name||'').toLowerCase().includes(filterText) || String(item.owner_uid||'').includes(filterText);
  });
  document.getElementById('bm13-team-count').textContent = r.summary?.teams || 0;
  document.getElementById('bm13-subject-count').textContent = r.summary?.subjects || 0;
  document.getElementById('bm13-cell-count').textContent = r.summary?.cells || 0;
  document.getElementById('bm13-marker').textContent = r.packet?.marker || 0;
  document.getElementById('bm13-updated').textContent = r.updated_at ? `更新时间 ${r.updated_at}` : '';
  const area = Array.isArray(r.packet?.area_range) ? r.packet.area_range : [];
  document.getElementById('bm13-range').textContent = area.length >= 4 ? `范围 ${area.join(' , ')}` : (r.latest_file || '');
  document.getElementById('bm13-count').textContent = `显示 ${rows.length} / ${r.items?.length || 0} 支队伍`;

  const stream = document.getElementById('bm13-stream');
  if(!stream) return;
  if(!rows.length){
    stream.innerHTML = `<div class='feed-item'><div class='feed-time'>--:--:--</div><div class='feed-body' style='color:var(--text2)'>暂无匹配队伍</div></div>`;
    return;
  }

  const nowSec = Math.floor(Date.now() / 1000);
  const fmtRemain = (ts)=>{
    if(!ts) return '-';
    const diff = Number(ts) - nowSec;
    if(diff <= 0) return '已到达';
    const m = Math.floor(diff / 60);
    const s = diff % 60;
    return `${m}分${String(s).padStart(2,'0')}秒`;
  };

  stream.innerHTML = rows.map(item=>{
    const lineup = item.lineup || {};
    const teamStats = item.team_stats || {};
    const teamMatchups = item.team_matchups || {};
    const recentBattles = Array.isArray(teamMatchups.recent_battles) ? teamMatchups.recent_battles : [];
    const favoredMatchups = Array.isArray(teamMatchups.favored) ? teamMatchups.favored : [];
    const counteredMatchups = Array.isArray(teamMatchups.countered) ? teamMatchups.countered : [];
    const teamBattles = Number(teamStats.battles || 0);
    const teamWinRate = Number(teamStats.win_rate || 0);
    const teamWins = Number(teamStats.wins || 0);
    const teamDraws = Number(teamStats.draws || 0);
    const teamLoses = Number(teamStats.loses || 0);
    const teamRateColor = teamBattles <= 0 ? 'var(--text2)' : (teamWinRate >= 60 ? 'var(--green)' : teamWinRate >= 40 ? 'var(--gold)' : 'var(--red)');
    const renderHeroList = (ids)=> (Array.isArray(ids) ? ids : []).map(hid=>{
      const hcfg = (typeof HERO_CFG!=='undefined') ? (HERO_CFG[hid]||HERO_CFG[String(hid)]||{}) : {};
      return esc(hcfg.name || `武将${hid}`);
    }).join(' / ') || '-';
    const recentHtml = recentBattles.length ? recentBattles.map(rb=>`<div style='display:grid;grid-template-columns:20px minmax(0,1fr);gap:8px;align-items:center;margin-top:4px'><span style='color:${rb.result_text === '胜' ? 'var(--green)' : rb.result_text === '负' ? 'var(--red)' : 'var(--gold)'};text-align:left'>${rb.result_text}</span><span style='min-width:0;color:#c7d3df;white-space:nowrap;overflow:hidden;text-overflow:ellipsis'>${esc(rb.opponent_name || '未知对手')} · ${renderHeroList(rb.opponent_hero_ids)}</span></div>`).join('') : `<div style='margin-top:4px;color:var(--text2)'>暂无最近战绩</div>`;
    const favoredHtml = favoredMatchups.length ? favoredMatchups.map(m=>`<div style='margin-top:4px;color:#c7d3df'>${renderHeroList(m.opponent_hero_ids)} <span style='color:var(--green)'>${m.wins}胜</span><span style='color:var(--text2)'> / ${m.total}战 · ${m.win_rate}%</span></div>`).join('') : `<div style='margin-top:4px;color:var(--text2)'>暂无明显克制阵容</div>`;
    const counteredHtml = counteredMatchups.length ? counteredMatchups.map(m=>`<div style='margin-top:4px;color:#c7d3df'>${renderHeroList(m.opponent_hero_ids)} <span style='color:var(--red)'>${m.loses}负</span><span style='color:var(--text2)'> / ${m.total}战 · ${m.win_rate}%</span></div>`).join('') : `<div style='margin-top:4px;color:var(--text2)'>暂无明显被克制阵容</div>`;
    const teamInsightHtml = `<div style='padding:12px 14px;border:1px solid #223446;border-radius:10px;background:linear-gradient(180deg,#0d1723 0%,#0a111a 100%);height:100%;box-sizing:border-box'>
      <div style='display:grid;grid-template-columns:minmax(320px,1.4fr) minmax(130px,.8fr) minmax(130px,.8fr);gap:10px;align-items:start'>
        <div>
          <div style='font-size:.75rem;color:var(--gold);letter-spacing:.08em'>最近几场战绩</div>
          <div style='margin-top:6px;font-size:.72rem;line-height:1.5'>${recentHtml}</div>
        </div>
        <div>
          <div style='font-size:.75rem;color:var(--green);letter-spacing:.08em'>更克制的阵容</div>
          <div style='margin-top:6px;font-size:.72rem;line-height:1.5'>${favoredHtml}</div>
        </div>
        <div>
          <div style='font-size:.75rem;color:var(--red);letter-spacing:.08em'>更被克制的阵容</div>
          <div style='margin-top:6px;font-size:.72rem;line-height:1.5'>${counteredHtml}</div>
        </div>
      </div>
    </div>`;
    const ownerName = esc(item.owner_name || '未知');
    const fromText = item.from_xy ? `<span style='color:var(--text2)'>(${esc(item.from_xy||'')})</span>` : '-';
    const toText = item.to_xy ? `<span style='color:var(--text2)'>(${esc(item.to_xy||'')})</span>` : '-';
    const currentText = (item.fortress_xy || item.current_xy) ? `<span style='color:var(--text2)'>(${esc(item.fortress_xy || item.current_xy || '')})</span>` : '-';
    const heroesHtml = (lineup.heroes||[]).length
      ? `<div style='display:flex;gap:10px;flex-wrap:wrap;align-items:stretch'>${(lineup.heroes||[]).map(h=>{
          const hcfg = (typeof HERO_CFG!=='undefined') ? (HERO_CFG[h.hero_id]||HERO_CFG[String(h.hero_id)]||{}) : {};
          const skillMap = typeof SKILL_CFG!=='undefined' ? SKILL_CFG : (typeof SKILL_MAP!=='undefined' ? SKILL_MAP : {});
          const hname = esc(hcfg.name || `武将${h.hero_id}`);
          const country = esc(hcfg.country || '');
          const htype = esc(hcfg.type || '');
          const iconId = hcfg.iconId || h.hero_id;
          const imgUrl = `https://g0.gph.netease.com/ngsocial/community/stzb/cn/cards/cut/card_medium_${iconId}.jpg?gameid=g10`;
          const starCount = Math.max(0, Number(h.star||0));
          const stars = starCount > 0 ? '★'.repeat(starCount) : '—';
          const starColor = starCount >= 5 ? 'var(--gold)' : 'var(--text2)';
          const starText = `进阶${starCount}`;
          const skillHtml = (h.skills||[]).length
            ? h.skills.map(s=>{
                const sc = skillMap[String(s.skill_id)] || skillMap[s.skill_id] || {};
                const sname = esc(sc.name || `技能${s.skill_id}`);
                return `<div style='display:flex;align-items:center;gap:4px;color:#8fd3ff;font-size:.72rem;line-height:1.45'><span style='color:#5b6f86'>▸</span><span>${sname}${s.level?` Lv${s.level}`:''}</span></div>`;
              }).join('')
            : `<div style='color:var(--text2);font-size:.7rem'>无战法</div>`;
          return `<div style='flex:1 1 180px;max-width:220px;min-width:180px;background:linear-gradient(180deg,#101a27 0%,#0c1420 100%);border:1px solid #2a3d52;border-radius:10px;padding:10px 10px 9px 10px;box-shadow:inset 0 1px 0 #ffffff08'>
            <div style='display:flex;align-items:flex-start;gap:10px'>
              <img src='${imgUrl}' style='width:54px;height:54px;border-radius:8px;object-fit:cover;object-position:left top;border:1px solid #3c4e62;background:#0a1018;flex-shrink:0' onerror='this.style.display="none"'>
              <div style='flex:1;min-width:0'>
                <div style='display:flex;align-items:flex-start;justify-content:space-between;gap:8px'>
                  <div style='min-width:0'>
                    <div style='font-size:1rem;font-weight:700;color:#d8c89a;line-height:1.1;white-space:nowrap;overflow:hidden;text-overflow:ellipsis'>${hname}</div>
                    <div style='font-size:.72rem;color:#e36b6b;margin-top:3px'>${country}${country&&htype?' · ':''}${htype} Lv.${h.level||0}</div>
                  </div>
                  <div style='font-size:.76rem;color:${starColor};white-space:nowrap;flex-shrink:0;text-align:right'>
                    <div>${stars}</div>
                    <div style='font-size:.66rem;color:var(--text2);margin-top:2px'>${starText}</div>
                  </div>
                </div>
                <div style='margin-top:7px;border-top:1px solid #213246;padding-top:6px;display:flex;flex-direction:column;gap:2px'>${skillHtml}</div>
              </div>
            </div>
          </div>`;
        }).join('')}</div>`
      : `<span style='color:var(--text2)'>未匹配到武将战法</span>`;

    return `<details open style='margin-bottom:12px;border:1px solid var(--border);border-radius:8px;background:linear-gradient(135deg,var(--panel),var(--panel2));overflow:visible'>
      <summary style='list-style:none;cursor:pointer;padding:12px 14px;display:flex;align-items:center;justify-content:space-between;gap:12px;background:#0b1320;flex-wrap:wrap'>
        <div style='display:flex;align-items:center;gap:12px;flex-wrap:wrap'>
          <span style='font-family:var(--font-mono);color:var(--gold)'>队伍 ${item.team_id || ''}</span>
          <b>${ownerName}</b>
          <span style='color:var(--cyan);font-size:.74rem'>${esc(item.move_type_text || '-')}</span>
          <span style='color:var(--text2);font-size:.72rem'>主体ID ${item.subject_id || 0}</span>
          <span style='color:var(--text2);font-size:.72rem'>主城位置 ${esc(item.home_xy || '-')}</span>
        </div>
        <div style='display:grid;grid-template-columns:repeat(5,minmax(110px,1fr));gap:10px 14px;align-items:stretch;flex:1;min-width:min(100%,620px);font-size:.74rem'>
          <div style='display:flex;flex-direction:column;justify-content:center;padding:6px 10px;border:1px solid #1d2a39;border-radius:10px;background:#0e1724;min-height:46px'>
            <span style='color:var(--text2);font-size:.66rem;letter-spacing:.04em'>到达</span>
            <span style='font-family:var(--font-mono);margin-top:4px'>${fmtBm13Ts(item.arrive_time)}</span>
          </div>
          <div style='display:flex;flex-direction:column;justify-content:center;padding:6px 10px;border:1px solid #1d2a39;border-radius:10px;background:#0e1724;min-height:46px'>
            <span style='color:var(--text2);font-size:.66rem;letter-spacing:.04em'>剩余</span>
            <span style='color:var(--gold);margin-top:4px'>${fmtRemain(item.arrive_time)}</span>
          </div>
          <div style='display:flex;flex-direction:column;justify-content:center;padding:6px 10px;border:1px solid #1d2a39;border-radius:10px;background:#0e1724;min-height:46px'>
            <span style='color:var(--text2);font-size:.66rem;letter-spacing:.04em'>势力</span>
            <span style='color:var(--cyan);margin-top:4px'>${fmt(item.power || 0)}</span>
          </div>
          <div style='display:flex;flex-direction:column;justify-content:center;padding:6px 10px;border:1px solid ${teamBattles > 0 ? '#32465a' : '#233243'};border-radius:10px;background:${teamBattles > 0 ? '#132131' : '#101923'};min-height:46px'>
            <span style='color:var(--text2);font-size:.66rem;letter-spacing:.04em'>队伍胜率</span>
            <span style='color:${teamRateColor};margin-top:4px;font-weight:700'>${teamBattles > 0 ? `${teamWinRate}%` : '-'}</span>
          </div>
          <div style='display:flex;flex-direction:column;justify-content:center;padding:6px 10px;border:1px solid #1d2a39;border-radius:10px;background:#0e1724;min-height:46px'>
            <span style='color:var(--text2);font-size:.66rem;letter-spacing:.04em'>战绩摘要</span>
            <span style='color:var(--text);margin-top:4px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis'>${teamBattles > 0 ? `${teamWins}胜/${teamDraws}平/${teamLoses}负 · ${teamBattles}战` : '暂无队伍战报'}</span>
          </div>
        </div>
      </summary>
      <div style='padding:12px 14px'>
        <div style='display:grid;grid-template-columns:repeat(3,minmax(140px,1fr));gap:10px;margin-top:2px'>
          <div><div style='color:var(--text2);font-size:.68rem'>出发地块</div><div style='font-family:Share Tech Mono,monospace'>${fromText}</div></div>
          <div><div style='color:var(--text2);font-size:.68rem'>目标地块</div><div style='font-family:Share Tech Mono,monospace'>${toText}</div></div>
          <div><div style='color:var(--text2);font-size:.68rem'>要塞位置</div><div style='font-family:Share Tech Mono,monospace'>${currentText}</div></div>
        </div>
        <div style='display:grid;grid-template-columns:minmax(0,1.12fr) minmax(460px,.88fr);gap:8px;align-items:start;margin-top:10px'>
          <div>
            <div style='color:var(--text2);font-size:.68rem;margin-bottom:4px'>武将与战法</div>
            <div>${heroesHtml}</div>
          </div>
          <div>
            ${teamInsightHtml}
          </div>
        </div>
      </div>
    </details>`;
  }).join('');
}

async function loadBattleMonitor13a2(){
  const r = await apiFetch('/api/battle_monitor_13a2');
  renderBattleMonitor13a2(r);
}

function loadAncientChinaMapDemo(){
  const svg = document.getElementById('ancient-map-svg');
  if(!svg) return;
  const wrap = document.getElementById('ancient-map-wrap');
  if(wrap && !document.getElementById('ancient-map-loading')){
    wrap.insertAdjacentHTML('beforeend', `<div id="ancient-map-loading" style="position:absolute;inset:0;display:flex;align-items:center;justify-content:center;color:#e6d1a0;font-size:.86rem;letter-spacing:.14em;background:var(--surface-overlay);border:1px solid var(--border-subtle);z-index:3">正在展卷汉末十三州…</div>`);
  }
  const tz = [
    {name:'幽州', fill:'#3d3023', c:[117.5,41.3], poly:[[113,39],[117,39],[121.8,39.5],[124.8,42],[126.5,46.5],[121.5,49],[114,48],[111,44],[113,39]]},
    {name:'并州', fill:'#4a3828', c:[112.6,38.1], poly:[[108,34.5],[112.5,34.5],[115.8,36],[116.8,40.5],[114,42.5],[110,43],[107.5,40],[108,34.5]]},
    {name:'冀州', fill:'#58412d', c:[115.4,37.3], poly:[[112.2,34.6],[119,34.8],[120.6,37.8],[119.3,41.2],[116.5,42.4],[113.6,41],[112.2,34.6]]},
    {name:'青州', fill:'#654934', c:[119.9,36.8], poly:[[118.2,35],[121,35],[122.5,36],[123.2,38.5],[121.4,39.8],[119.2,38.8],[118.2,35]]},
    {name:'徐州', fill:'#72513a', c:[118.8,33.6], poly:[[116.2,31],[120.8,31],[121.8,34.7],[118.5,35.2],[116.4,34.1],[116.2,31]]},
    {name:'兖州', fill:'#805942', c:[115.6,35.7], poly:[[113.2,34.1],[117,34.2],[118.2,35.8],[117.2,37.2],[114.5,37.6],[113.2,34.1]]},
    {name:'豫州', fill:'#8d6349', c:[113.7,33.8], poly:[[110.8,31.2],[116,31.2],[117.1,34],[115.1,35.1],[111.2,34.6],[110.8,31.2]]},
    {name:'司隶', fill:'#9b6d52', c:[109.8,34.3], poly:[[107.2,33.2],[111.2,33.2],[111.5,35.6],[108.6,36.2],[107.2,33.2]]},
    {name:'雍州', fill:'#a97a5c', c:[103.8,35.2], poly:[[95,32],[107.5,32],[108.8,35.8],[106.8,39.6],[102,40.5],[96.5,39.2],[93.8,35.5],[95,32]]},
    {name:'凉州', fill:'#b78969', c:[95.8,39.1], poly:[[80,34],[95,34],[97,39.5],[95.5,42.8],[88,46.2],[80.5,44.5],[78.2,39.2],[80,34]]},
    {name:'扬州', fill:'#946848', c:[119.2,29.8], poly:[[116.3,25.2],[122.5,25.1],[122.6,31.2],[117.8,31.4],[116.3,25.2]]},
    {name:'荆州', fill:'#7b563d', c:[112.4,29.8], poly:[[107.5,26.5],[116.8,26.5],[117.2,31.4],[111.5,31.8],[108.3,30.2],[107.5,26.5]]},
    {name:'益州', fill:'#694a35', c:[103.2,29.1], poly:[[97,22.3],[108.8,22.3],[108.8,31.5],[103.5,32.2],[98.5,30.2],[97,22.3]]},
    {name:'交州', fill:'#5b4030', c:[109.1,22.4], poly:[[104.2,19.3],[113.5,19.3],[113.2,24.5],[108.6,25.9],[104.2,24],[104.2,19.3]]}
  ];
  const rivers = [
    [[97,33],[101,33.4],[106,31.4],[111.5,30.7],[118.5,31.2],[121,31.2]],
    [[95,35.5],[102,36.2],[109,37.2],[118,37.8]],
    [[111,23],[113,23.2],[115,23.2]]
  ];
  Promise.resolve({json:()=>Promise.resolve({features:[]})})
    .then(r => r.json())
    .then(geo => {
      const features = Array.isArray(geo.features) ? geo.features : [];
      const points = [];
      const walk = (coords)=>{
        if(!Array.isArray(coords)) return;
        if(typeof coords[0] === 'number' && typeof coords[1] === 'number') return void points.push(coords);
        coords.forEach(walk);
      };
      features.forEach(f=> walk(f.geometry && f.geometry.coordinates));
      tz.forEach(z => walk(z.poly));
      if(!points.length) return;
      let minX=Infinity,minY=Infinity,maxX=-Infinity,maxY=-Infinity;
      points.forEach(([x,y])=>{ if(x<minX)minX=x; if(x>maxX)maxX=x; if(y<minY)minY=y; if(y>maxY)maxY=y; });
      const viewW=1200, viewH=760, pad=54;
      const sx=(viewW-pad*2)/(maxX-minX), sy=(viewH-pad*2)/(maxY-minY), scale=Math.min(sx,sy);
      const ox=(viewW-(maxX-minX)*scale)/2, oy=(viewH-(maxY-minY)*scale)/2;
      const project=([x,y])=>[ox+(x-minX)*scale, viewH-(oy+(y-minY)*scale)];
      const ringToPath=(ring)=>ring.map((pt,i)=>{ const [px,py]=project(pt); return `${i===0?'M':'L'}${px.toFixed(2)},${py.toFixed(2)}`; }).join(' ')+' Z';
      const toPath=(coords)=>{
        if(!Array.isArray(coords)||!coords.length) return '';
        if(typeof coords[0][0] === 'number') return ringToPath(coords);
        if(typeof coords[0][0][0] === 'number') return coords.map(ringToPath).join(' ');
        return coords.map(poly => Array.isArray(poly) ? poly.map(ringToPath).join(' ') : '').join(' ');
      };
      const riverPath = rivers.map(line => line.map((pt,i)=>{ const [px,py]=project(pt); return `${i===0?'M':'L'}${px.toFixed(2)},${py.toFixed(2)}`; }).join(' ')).join(' ');
      const cityMarkers = [
        {name:'洛阳', c:[112.45,34.62]}, {name:'长安', c:[108.94,34.34]}, {name:'成都', c:[104.06,30.67]},
        {name:'邺城', c:[114.48,36.61]}, {name:'襄阳', c:[112.14,32.04]}, {name:'建业', c:[118.78,32.04]},
        {name:'蓟城', c:[116.4,39.9]}, {name:'临淄', c:[118.31,36.82]}, {name:'番禺', c:[113.27,23.13]}
      ];
      svg.innerHTML = `
        <defs>
          <filter id='paperNoise'><feTurbulence type='fractalNoise' baseFrequency='0.82' numOctaves='2' stitchTiles='stitch'/><feColorMatrix type='saturate' values='0'/><feComponentTransfer><feFuncA type='table' tableValues='0 0 .04 .07'/></feComponentTransfer></filter>
          <filter id='goldGlow'><feDropShadow dx='0' dy='0' stdDeviation='4' flood-color='#c8a044' flood-opacity='.18'/></filter>
          <linearGradient id='riverGrad' x1='0%' y1='0%' x2='100%' y2='0%'><stop offset='0%' stop-color='#214a61'/><stop offset='50%' stop-color='#4d86a6'/><stop offset='100%' stop-color='#214a61'/></linearGradient>
          <linearGradient id='regionStroke' x1='0%' y1='0%' x2='100%' y2='100%'><stop offset='0%' stop-color='#e2c57a'/><stop offset='100%' stop-color='#7e5a27'/></linearGradient>
        </defs>
        <rect x='0' y='0' width='1200' height='760' fill='#17110d'/>
        <rect x='0' y='0' width='1200' height='760' fill='#f0dfb8' opacity='.05' filter='url(#paperNoise)'/>
        <g opacity='.14'>
          ${features.map(f => `<path d="${toPath(f.geometry && f.geometry.coordinates)}" fill="none" stroke="#8f7a52" stroke-width="0.8" stroke-opacity=".22"></path>`).join('')}
        </g>
        <path d='${riverPath}' stroke='url(#riverGrad)' stroke-width='5' fill='none' stroke-linecap='round' opacity='.7'/>
        <g filter='url(#goldGlow)'>
          ${tz.map(z => {
            const path = ringToPath(z.poly);
            const [cx,cy] = project(z.c);
            return `<g class="han-region" data-name="${z.name}"><path d="${path}" fill="${z.fill}" fill-opacity=".82" stroke="url(#regionStroke)" stroke-width="2.2"></path><text x="${cx.toFixed(2)}" y="${cy.toFixed(2)}" text-anchor="middle" dominant-baseline="middle" fill="#f0ddb0" font-size="24" letter-spacing="4">${z.name}</text></g>`;
          }).join('')}
        </g>
        <g opacity='.38'>
          ${tz.map(z => `<path d="${ringToPath(z.poly)}" fill="none" stroke="#f6e7bc" stroke-opacity=".08" stroke-width="1"></path>`).join('')}
        </g>
        <g>
          ${cityMarkers.map(city => { const [cx,cy]=project(city.c); return `<g><circle cx="${cx.toFixed(2)}" cy="${cy.toFixed(2)}" r="4.8" fill="#d8bf7a" stroke="#3b2910" stroke-width="1.8"></circle><circle cx="${cx.toFixed(2)}" cy="${cy.toFixed(2)}" r="10.8" fill="none" stroke="#d8bf7a" stroke-opacity=".24" stroke-width="1"></circle><text x="${(cx+10).toFixed(2)}" y="${(cy-8).toFixed(2)}" fill="#e7d4a0" font-size="14" letter-spacing="1">${city.name}</text></g>`; }).join('')}
        </g>
        <g opacity='.16'><text x='600' y='710' text-anchor='middle' fill='#b89657' font-size='74' letter-spacing='18'>大 汉 十 三 州</text></g>`;
      svg.querySelectorAll('.han-region').forEach(el => {
        el.addEventListener('mouseenter', () => {
          const p = el.querySelector('path');
          if(p){ p.setAttribute('fill-opacity', '.97'); p.setAttribute('stroke-width', '2.9'); }
        });
        el.addEventListener('mouseleave', () => {
          const p = el.querySelector('path');
          if(p){ p.setAttribute('fill-opacity', '.82'); p.setAttribute('stroke-width', '2.2'); }
        });
      });
    })
    .catch(err => {
      svg.innerHTML = `<text x='50%' y='50%' text-anchor='middle' dominant-baseline='middle' fill='#d8bf7a' font-size='20'>地图加载失败：${String(err && err.message || err)}</text>`;
    })
    .finally(() => {
      const loading = document.getElementById('ancient-map-loading');
      if(loading) loading.remove();
    });
}

async function loadBattleMonitor(forcePush=true){
  ensureBattleMonitorControls();
  const r = await apiFetch('/api/battle_monitor');
  renderBattleMonitor(r);
  if(forcePush || document.getElementById('tab27')?.classList.contains('active')) pushBattleMonitorHistory(r);
}

// Rankings v2
const RNK_PERIOD_LABEL = {'24h':'24小时', 'week':'本周', 'season':'赛季'};
const RNK_DIM_LABEL    = {player:'玩家', union:'联盟', zone:'州'};
const RNK_METRIC_LABEL = {wuxun:'武勋', battles:'出战', power:'势力值'};
const RNK_MEDAL = ['🥇','🥈','🥉'];

async function loadRanking(){
  const pEl = document.getElementById('rnk-period');
  const dEl = document.getElementById('rnk-dim');
  const mEl = document.getElementById('rnk-metric');
  if(!pEl||!dEl||!mEl) return;
  const p = pEl.value;
  const d = dEl.value;
  const m = mEl.value;
  const data = await apiFetch(`/api/ranking_v2?period=${p}&dim=${d}&metric=${m}`);
  if(!data) return;

  const titleEl = document.getElementById('rnk-title');
  const countEl = document.getElementById('rnk-count');
  const thGroup = document.getElementById('rnk-th-group');
  const thVal   = document.getElementById('rnk-th-val');
  if(titleEl) titleEl.textContent = `🏆 ${RNK_PERIOD_LABEL[p]||p} ${RNK_DIM_LABEL[d]||d} ${RNK_METRIC_LABEL[m]||m}榜`;
  if(countEl) countEl.textContent = `共${data.length}条`;
  if(thGroup) thGroup.textContent = d==='player'?'联盟':'';
  if(thVal)   thVal.textContent   = RNK_METRIC_LABEL[m]||m;

  const b = document.getElementById('rnk-body');
  if(b){
    b.innerHTML='';
    data.forEach((r,i)=>{
      const medal = i<3 ? RNK_MEDAL[i] : '';
      const cls   = i===0?'rank-1':i===1?'rank-2':i===2?'rank-3':'';
      const valFmt = m==='wuxun'||m==='power' ? fmt(r.value) : r.value;
      const wrStyle = r.win_rate>=60?'color:var(--green)':r.win_rate>=40?'color:var(--gold)':'color:var(--red)';
      b.innerHTML += `<tr>
        <td class='${cls}' style='font-family:Share Tech Mono,monospace'>${medal||r.rank}</td>
        <td><b>${esc(r.name)}</b></td>
        <td style='color:var(--text2);font-size:.72rem'>${esc(r.group_name||'')}</td>
        <td class='${cls}' style='font-family:Share Tech Mono,monospace'>${valFmt}</td>
        <td>${r.battles}</td>
        <td>${r.city_cnt}</td>
        <td style='${wrStyle}'>${r.win_rate}%</td>
      </tr>`;
    });
  }

  const bars = document.getElementById('rnk-bars');
  if(bars){
    bars.innerHTML='';
    const top15 = data.slice(0,15);
    const maxV  = top15.length ? (top15[0].value||1) : 1;
    const barColor = m==='wuxun'?'var(--gold)':m==='power'?'var(--cyan)':'var(--blue)';
    top15.forEach((r,i)=>{
      const pct = Math.round((r.value/maxV)*100);
      const medal = i<3?RNK_MEDAL[i]:'';
      bars.innerHTML += `<div class='bar-row'>
        <div class='bar-label'>${medal}${esc(r.name)}</div>
        <div class='bar-track'><div class='bar-fill' style='width:${pct}%;background:${barColor}'></div></div>
        <div class='bar-val'>${m==='wuxun'||m==='power'?fmt(r.value):r.value}</div>
      </div>`;
    });
  }
}

// Wuxun
async function loadWuxun(){
  const p=document.getElementById('wx-period').value;
  const s=document.getElementById('wx-scope').value;
  const data=await apiFetch(`/api/wuxun_stats?period=${p}&scope=${s}`);
  const b=document.getElementById('wx-body');b.innerHTML='';
  const bars=document.getElementById('wx-bars');bars.innerHTML='';
  const max=data&&data[0]?data[0].total_wx||1:1;
  (data||[]).forEach((r,i)=>{
    const cls=i===0?'rank-1':i===1?'rank-2':i===2?'rank-3':'';
    b.innerHTML+=`<tr><td class='${cls}'>${i+1}</td><td>${esc(r.name)}</td><td class='${cls}'>${fmt(r.total_wx)}</td><td>${r.battles}</td><td>${r.city_battles}</td><td>${r.main_city_battles}</td></tr>`;
    if(i<15){const pct=Math.round((r.total_wx/max)*100);
      bars.innerHTML+=`<div class='bar-row'><div class='bar-label'>${esc(r.name)}</div><div class='bar-track'><div class='bar-fill' style='width:${pct}%;background:var(--gold)'></div></div><div class='bar-val'>${fmt(r.total_wx)}</div></div>`;}
  });
}

// Power
async function loadPower(){
  const p=document.getElementById('pw-period').value;
  const s=document.getElementById('pw-scope').value;
  const data=await apiFetch(`/api/power_stats?period=${p}&scope=${s}`);
  const b=document.getElementById('pw-body');b.innerHTML='';
  (data||[]).forEach((r,i)=>{
    const cls=i===0?'rank-1':i===1?'rank-2':i===2?'rank-3':'';
    b.innerHTML+=`<tr><td class='${cls}'>${i+1}</td><td>${esc(r.name)}</td><td class='${cls}'>${fmt(r.max_power)}</td><td>${fmt(r.total_power)}</td><td>${r.battles}</td></tr>`;
  });
}

// Attendance
async function loadAttendance(){
  const p=document.getElementById('att-period').value;
  const u=document.getElementById('att-union').value;
  const data=await apiFetch(`/api/attendance?period=${p}&union=${encodeURIComponent(u)}`);
  const b=document.getElementById('att-body');b.innerHTML='';
  (data||[]).forEach((r,i)=>{
    const wr=r.total_battles?Math.round(r.wins/r.total_battles*100):0;
    b.innerHTML+=`<tr><td>${i+1}</td><td>${esc(r.player_name)}</td><td>${esc(r.union_name||'')}</td><td>${r.total_battles}</td><td>${r.city_battles}</td><td>${r.main_city}</td><td>${r.field_battles}</td><td>${fmt(r.total_wx)}</td><td>${r.wins}(${wr}%)</td></tr>`;
  });
}

// Schedule
async function loadSchedule(){
  const data=await apiFetch('/api/schedule');
  const b=document.getElementById('sch-body');b.innerHTML='';
  (data||[]).forEach(r=>{
    b.innerHTML+=`<tr><td>${esc(r.session_id)}</td><td>${r.slot_index+1}</td><td>${r.wid}</td><td>${esc(r.wid_code||'')}</td><td>+${r.slot_index*3}min</td><td>${esc(r.assigned_group||'-')}</td><td>${esc(r.notes||'')}</td></tr>`;
  });
}
async function generateSchedule(){
  const sid=document.getElementById('sch-session').value||new Date().toISOString().slice(0,16).replace(/[^0-9]/g,'');
  const interval=parseInt(document.getElementById('sch-interval').value)||3;
  const r=await apiFetch('/api/schedule/generate',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({session_id:sid,interval})});
  if(r&&r.ok){showToast(`排表生成: ${r.slots}个格子`);loadSchedule();}
}

// Analysis
const FT_MAP=STZB_META.fightTypes;
const FT_COLOR=STZB_META.fightTypeColors;

function anaBar(label,cnt,maxV,color,extra=''){
  const pct=Math.round(cnt/maxV*100);
  return `<div class='bar-row'><div class='bar-label' style='min-width:72px'>${label}</div><div class='bar-track'><div class='bar-fill' style='width:${pct}%;background:${color}'></div></div><div class='bar-val'>${cnt}${extra}</div></div>`;
}

async function loadAnalysis(){
  const p=document.getElementById('ana-period').value;
  const data=await apiFetch('/api/battle_analysis?period='+p);
  if(!data)return;

  // 更新时间
  const tu=document.getElementById('ana-update-time');
  if(tu) tu.textContent='更新于 '+new Date().toLocaleTimeString('zh-CN',{hour12:false});

  // 核心卡片
  const s=data.summary||{};
  const total=s.total||0;
  const atkWr=total?Math.round((s.atk_wins||0)/total*100):0;
  const nightPct=total?Math.round((s.night_cnt||0)/total*100):0;
  // 夜战胜率
  const nd=data.night_day||[];
  const nightR=nd.find(r=>r.in_night===1)||{cnt:0,atk_wins:0};
  const nWr=nightR.cnt?Math.round(nightR.atk_wins/nightR.cnt*100):0;
  document.getElementById('ana-c-total').textContent=fmt(total);
  const wrEl=document.getElementById('ana-c-wr');
  wrEl.textContent=atkWr+'%';
  wrEl.style.color=atkWr>=50?'var(--green)':'var(--red)';
  document.getElementById('ana-c-night').textContent=nightPct+'%';
  const nwrEl=document.getElementById('ana-c-nwr');
  nwrEl.textContent=nWr+'%';
  nwrEl.style.color=nWr>=50?'var(--green)':'var(--red)';
  document.getElementById('ana-c-unions').textContent=s.union_cnt||0;
  document.getElementById('ana-c-players').textContent=s.player_cnt||0;

  // 24小时热力柱状图
  const hourEl=document.getElementById('ana-hour');hourEl.innerHTML='';
  const hours=data.by_hour||[];
  // 补全0-23小时
  const hourMap={}; hours.forEach(r=>{hourMap[r.hour]=r.cnt;});
  const allHours=Array.from({length:24},(_,i)=>String(i).padStart(2,'0'));
  const maxH=Math.max(1,...Object.values(hourMap));
  allHours.forEach(h=>{
    const cnt=hourMap[h]||0;
    const pct=Math.round(cnt/maxH*100);
    // 颜色根据活跃度渐变：低=青，高=金
    const hue=cnt===0?'var(--text2)':pct>70?'var(--gold)':pct>40?'var(--cyan)':'var(--blue)';
    hourEl.innerHTML+=anaBar(h+':00',cnt,Math.max(1,maxH),hue);
  });

  // 夜战 vs 白天
  const nightEl=document.getElementById('ana-night');nightEl.innerHTML='';
  const dayR=nd.find(r=>r.in_night===0)||{cnt:0,atk_wins:0};
  const maxND=Math.max(1,dayR.cnt,nightR.cnt);
  const dWr=dayR.cnt?Math.round(dayR.atk_wins/dayR.cnt*100):0;
  nightEl.innerHTML=`
    <div style='display:grid;grid-template-columns:1fr 1fr;gap:16px;margin-bottom:16px'>
      <div style='text-align:center;padding:14px;background:var(--panel2);border-radius:6px;border:1px solid var(--border)'>
        <div style='font-size:1.6rem;color:var(--gold);font-weight:700'>${dayR.cnt}</div>
        <div style='font-size:.78rem;color:var(--text2);margin-top:2px'>☀️ 白天战斗</div>
        <div style='font-size:.85rem;color:${dWr>=50?'var(--green)':'var(--red)'};margin-top:4px'>胜率 ${dWr}%</div>
      </div>
      <div style='text-align:center;padding:14px;background:var(--panel2);border-radius:6px;border:1px solid var(--border)'>
        <div style='font-size:1.6rem;color:var(--purple);font-weight:700'>${nightR.cnt}</div>
        <div style='font-size:.78rem;color:var(--text2);margin-top:2px'>🌙 夜战</div>
        <div style='font-size:.85rem;color:${nWr>=50?'var(--green)':'var(--red)'};margin-top:4px'>胜率 ${nWr}%</div>
      </div>
    </div>
    ${anaBar('☀️ 白天',dayR.cnt,maxND,'var(--gold)')}
    ${anaBar('🌙 夜战',nightR.cnt,maxND,'var(--purple)',nightR.cnt>dayR.cnt?' 🔥':'')}  `;

  // 战力段位分布
  const powerEl=document.getElementById('ana-power');
  if(powerEl){
    powerEl.innerHTML='';
    const pd=data.power_dist||[];
    const maxP=Math.max(1,...pd.map(r=>r.cnt));
    const tierColor={'1000w+':'var(--red)','800w+':'var(--gold)','600w+':'var(--cyan)','400w+':'var(--green)','200w+':'var(--blue)','200w以下':'var(--text2)'};
    pd.forEach(r=>{
      powerEl.innerHTML+=anaBar(r.tier,r.cnt,maxP,tierColor[r.tier]||'var(--blue)');
    });
  }

  // 对阵联盟
  const ub=document.getElementById('ana-union');ub.innerHTML='';
  (data.vs_union||[]).forEach(r=>{
    const wr=r.total?Math.round(r.our_wins/r.total*100):0;
    const wrColor=wr>=60?'var(--green)':wr>=40?'var(--gold)':'var(--red)';
    const barW=wr+'%';
    ub.innerHTML+=`<tr>
      <td>${esc(r.def_union)}</td>
      <td>${r.total}</td>
      <td style='color:var(--green)'>${r.our_wins}</td>
      <td><div style='display:flex;align-items:center;gap:6px'>
        <div style='flex:1;height:6px;background:var(--panel2);border-radius:3px'><div style='width:${barW};height:6px;background:${wrColor};border-radius:3px'></div></div>
        <span style='color:${wrColor};min-width:36px;text-align:right'>${wr}%</span>
      </div></td>
      <td style='color:var(--red)'>${r.their_wins}</td>
    </tr>`;
  });

  // 最活跃玩家
  const tb=document.getElementById('ana-top');tb.innerHTML='';
  (data.top_players||[]).forEach((r,i)=>{
    const wr=r.battles?Math.round((r.wins||0)/r.battles*100):0;
    const wrColor=wr>=60?'var(--green)':wr>=40?'var(--gold)':'var(--red)';
    const medal=i===0?'🥇':i===1?'🥈':i===2?'🥉':(i+1);
    tb.innerHTML+=`<tr>
      <td>${medal}</td>
      <td><b>${esc(r.atk_name)}</b></td>
      <td style='color:var(--text2);font-size:.72rem'>${esc(r.atk_union||'')}</td>
      <td>${r.battles}</td>
      <td style='color:${wrColor}'>${wr}%</td>
      <td style='color:var(--cyan);font-size:.8rem'>${fmt(r.max_power||0)}</td>
    </tr>`;
  });
}

// Teams
async function loadTeams(){
  const sEl=document.getElementById('team-side');
  const s=sEl?sEl.value:'atk';
  const data=await apiFetch(`/api/heroes/combos?side=${s}`);
  const b=document.getElementById('team-body');if(!b)return;b.innerHTML='';
  if(!data||!data.length){
    b.innerHTML=`<tr><td colspan=6 style='color:var(--text2);text-align:center;padding:20px'>暂无武将数据</td></tr>`;
    const hint=document.getElementById('team-hint');
    if(hint) hint.textContent='胜率口径：胜=1，平=0.5，负=0';
    return;
  }
  const hint=document.getElementById('team-hint');
  if(hint) hint.textContent='胜率口径：胜=1，平=0.5，负=0';
  (data||[]).forEach((r,i)=>{
    const cls=i<3?`rank-${i+1}`:'';
    const hname = r.hero_name||'';
    let hcfg={};
    if(typeof HERO_CFG!=='undefined'){
      const found=Object.values(HERO_CFG).find(h=>h.name===hname);
      if(found)hcfg=found;
    }
    const iconId=hcfg.iconId||0;
    const country=hcfg.country||'';
    const htype=hcfg.type||'';
    const countryColor={'魏':'var(--blue)','蜀':'var(--green)','吴':'var(--red)','汉':'var(--gold)','晋':'var(--purple)','群':'var(--text2)'}[country]||'var(--text2)';
    const imgUrl=iconId?`https://g0.gph.netease.com/ngsocial/community/stzb/cn/cards/cut/card_medium_${iconId}.jpg?gameid=g10`:
      `https://g0.gph.netease.com/ngsocial/community/stzb/cn/cards/cut/card_medium_100021.jpg?gameid=g10`;
    const level = r.max_level||0;
    const heroHtml=`<div style='display:inline-flex;flex-direction:column;align-items:center;margin:0 4px'>
      <div style='position:relative;width:48px;height:48px'>
        <img src='${imgUrl}' style='width:48px;height:48px;object-fit:cover;object-position:left top;border-radius:4px;border:1px solid var(--border)' onerror='this.style.display="none"'>
        <span style='position:absolute;bottom:0;right:0;font-size:.55rem;background:#0008;padding:0 2px;border-radius:2px;color:${countryColor}'>${htype}</span>
        ${level?`<span style='position:absolute;top:0;left:0;font-size:.55rem;background:#c8a04499;padding:0 3px;border-radius:2px;color:#fff;font-weight:700'>${level}</span>`:''}
      </div>
      <span style='font-size:.65rem;color:var(--text);max-width:52px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap'>${esc(hname)}</span>
    </div>`;
    const wrStyle=r.win_rate>=60?'color:var(--green)':r.win_rate>=40?'color:var(--gold)':'color:var(--red)';
    const loses=(r.cnt||0)-(r.wins||0)-(r.draws||0);
    b.innerHTML+=`<tr>
      <td class='${cls}' style='font-family:Share Tech Mono,monospace'>${i<3?['🥇','🥈','🥉'][i]:(i+1)}</td>
      <td><div style='display:flex;align-items:flex-end;flex-wrap:wrap;gap:2px'>${heroHtml}</div></td>
      <td style='font-family:Share Tech Mono,monospace'>${r.cnt}</td>
      <td style='color:var(--green)'>${r.wins||0}</td>
      <td style='color:var(--text2)'>${r.draws||0}</td>
      <td style='${wrStyle}'>${r.win_rate||0}%<span style='color:var(--text2);font-size:.72rem'>（${r.wins||0}胜 / ${r.draws||0}平 / ${loses>0?loses:0}负）</span></td>
    </tr>`;
  });
}

function exportTeamsCSV(){
  const rows=[['排名','武将组合','使用次数','胜场','平局','胜率']];
  document.querySelectorAll('#team-body tr').forEach((tr,i)=>{
    const cells=[...tr.querySelectorAll('td')];
    if(cells.length>=5){
      // 武将组合取 title 里的文字拼接
      const heroSpans=[...cells[1].querySelectorAll('span:last-child')];
      const heroStr=heroSpans.map(s=>s.textContent).join('+');
      rows.push([cells[0].textContent.replace(/[🥇🥈🥉]/,'').trim()||String(i+1), heroStr, cells[2].textContent, cells[3].textContent, cells[4].textContent, cells[5].textContent]);
    }
  });
  const csv=rows.map(r=>r.map(c=>`"${String(c).replace(/"/g,'""')}"`).join(',')).join('\n');
  const blob=new Blob(['\uFEFF'+csv],{type:'text/csv;charset=utf-8'});
  const a=document.createElement('a');a.href=URL.createObjectURL(blob);
  a.download='队伍组合统计.csv';a.click();
  showToast('已导出 CSV');
}

// Player Team Query (照搬 stzbHelper Team.vue)
async function loadPlayerTeamQuery(){
  const name = document.getElementById('ptq-name').value.trim();
  const side = document.getElementById('ptq-side').value;
  const url = `/api/player_teams_stats?player=${encodeURIComponent(name)}&side=${side}&limit=100`;
  const data = await apiFetch(url);
  const b = document.getElementById('ptq-body'); b.innerHTML='';
  const cnt = document.getElementById('ptq-count');
  if(!data||!data.length){
    if(cnt)cnt.textContent='';
    const hint=document.getElementById('ptq-hint');
    if(hint) hint.textContent='胜率口径：胜=1，平=0.5，负=0';
    b.innerHTML=`<tr><td colspan=7 style='color:var(--text2);text-align:center;padding:20px'>${name?'未找到数据':'请输入玩家名查询'}</td></tr>`;
    return;
  }
  if(cnt)cnt.textContent=`共${data.length}条`;
  const hint=document.getElementById('ptq-hint');
  if(hint) hint.textContent='胜率口径：胜=1，平=0.5，负=0';
  data.forEach((r,i)=>{
    const cls=i<3?`rank-${i+1}`:'';
    const sideLabel=r.side==='atk'?`<span style='color:var(--red)'>攻</span>`:`<span style='color:var(--blue)'>守</span>`;
    const wrStyle=r.win_rate>=60?'color:var(--green)':r.win_rate>=40?'color:var(--gold)':'color:var(--red)';
    const loses=(r.used_count||0)-(r.win_count||0)-(r.draw_count||0);
    // 武将头像
    const heroNames=(r.heroes_str||'').split(',').filter(Boolean);
    const heroHtml=heroNames.map(hname=>{
      let hcfg={};
      if(typeof HERO_CFG!=='undefined'){
        const found=Object.values(HERO_CFG).find(h=>h.name===hname);
        if(found)hcfg=found;
      }
      const iconId=hcfg.iconId||0;
      const country=hcfg.country||'';
      const countryColor={'魏':'var(--blue)','蜀':'var(--green)','吴':'var(--red)','汉':'var(--gold)','晋':'var(--purple)','群':'var(--text2)'}[country]||'var(--text2)';
      const imgUrl=iconId?`https://g0.gph.netease.com/ngsocial/community/stzb/cn/cards/cut/card_medium_${iconId}.jpg?gameid=g10`:'';
      return `<div style='display:inline-flex;flex-direction:column;align-items:center;margin:0 3px'>
        <div style='position:relative;width:40px;height:40px'>
          ${imgUrl?`<img src='${imgUrl}' style='width:40px;height:40px;object-fit:cover;object-position:left top;border-radius:3px;border:1px solid var(--border)' onerror='this.style.display="none"'>`:''}
          <span style='position:absolute;bottom:0;right:0;font-size:.5rem;background:#0008;padding:0 2px;border-radius:2px;color:${countryColor}'>${hcfg.type||''}</span>
        </div>
        <span style='font-size:.6rem;color:var(--text);max-width:44px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap'>${esc(hname)}</span>
      </div>`;
    }).join('');
    b.innerHTML+=`<tr>
      <td class='${cls}'>${i+1}</td>
      <td>${sideLabel}</td>
      <td><div style='display:flex;align-items:flex-end;flex-wrap:wrap;gap:2px'>${heroHtml}</div></td>
      <td style='font-family:Share Tech Mono,monospace'>${r.used_count}</td>
      <td style='color:var(--green)'>${r.win_count}</td>
      <td style='color:var(--text2)'>${r.draw_count||0}</td>
      <td style='${wrStyle}'>${r.win_rate}%<span style='color:var(--text2);font-size:.72rem'>（${r.win_count||0}胜 / ${r.draw_count||0}平 / ${loses>0?loses:0}负）</span></td>
    </tr>`;
  });
}

// Sync stats
async function refreshWriterStats(){
  const r=await apiFetch('/api/writer_stats');
  if(!r)return;
  document.getElementById('sync-battles').textContent=r.battles||0;
  document.getElementById('sync-dbsync').textContent=r.db_sync||0;
  document.getElementById('sync-notify').textContent=r.notifications||0;
  document.getElementById('sync-err').textContent=r.errors||0;
  const r2=await apiFetch('/api/db_sync/tables');
  const b=document.getElementById('sync-body');if(!b)return;b.innerHTML='';
  (r2||[]).forEach(row=>{
    b.innerHTML+=`<tr><td>${esc(row.table_name)}</td><td>${row.cnt}</td><td>${row.inserts}</td><td>${row.updates}</td><td>${row.deletes}</td></tr>`;
  });
}

// Load history
async function loadHistory(){
  const r=await apiFetch('/api/battles_v2?size=50&page=1');
  // 过滤无效战报：atk_name为空或wid=0且无有效数据的
  (r&&r.data||[]).reverse().filter(b=>(b.atk_name||b.def_name||b.def_union)&&(b.atk_name||b.wid>0)&&!b.is_npc).forEach(b=>{
    addBattleFeed({...b,fight_type_name:{0:'野战',1:'援军',2:'援军',11:'攻城',33:'大城',80:'攻城'}[b.fight_type]||''});
  });
}

function refreshAll(){
  refreshWriterStats();
  loadHistory();
  // 重新加载所有 tab 数据
  if(typeof loadRanking==='function')loadRanking();
  if(typeof loadWuxun==='function')loadWuxun();
  if(typeof loadPower==='function')loadPower();
  if(typeof loadAttendance==='function')loadAttendance();
  if(typeof loadAnalysis==='function')loadAnalysis();
  if(typeof loadTeams==='function')loadTeams();
  if(typeof loadBattlesAll==='function')loadBattlesAll(1);
  if(typeof loadTeamStats==='function')loadTeamStats();
  if(typeof loadMapStats==='function')loadMapStats();
  if(typeof loadPlayerStats==='function')loadPlayerStats();
  if(typeof loadTeamUsers==='function')loadTeamUsers();
  if(typeof loadGroupWu==='function')loadGroupWu();
  if(typeof loadTasks==='function')loadTasks();
  if(typeof loadAllianceGroupTeams==='function')loadAllianceGroupTeams();
  if(typeof loadUnionList==='function')loadUnionList();
  if(typeof loadUnionPowerRank==='function')loadUnionPowerRank();
  if(typeof loadAnnouncements==='function')loadAnnouncements();
  if(typeof loadZonePlayers==='function')loadZonePlayers();
  if(typeof loadBattleMonitor==='function')loadBattleMonitor();
  if(typeof loadBattleMonitor13a2==='function')loadBattleMonitor13a2();
  setTimeout(injectPageExportButtons, 300);
}

function refreshActivePage(options={}){
  if(document.visibilityState==='hidden') return;
  const active=document.querySelector('.page.active');
  const id=active?.id||'';
  const loaders={
    tab1:()=>loadRanking(),
    tab2:()=>loadWuxun(),
    tab3:()=>loadPower(),
    tab4:()=>loadAttendance(),
    tab5:()=>loadSchedule(),
    tab6:()=>loadAnalysis(),
    tab7:()=>{loadTeams();loadPlayerBattleTeams();},
    tab8:()=>loadScores(),
    tab9:()=>refreshWriterStats(),
    tab10:()=>loadBattlesAll(1),
    tab11:()=>loadTeamStats(),
    tab12:()=>loadMapStats(),
    tab13:()=>loadPlayerStats(),
    tab14:()=>loadTeamUsers(),
    tab15:()=>loadGroupWu(),
    tab16:()=>loadTasks(),
    tab17:()=>loadAllianceGroupTeams(),
    tab18:()=>{loadUnionList();loadUnionPowerRank();},
    tab20:()=>loadAnnouncements(),
    tab21:()=>loadZonePlayers(),
    tab22:()=>loadMsgHistory(),
    tab23:()=>loadHeroCombo(),
    tab24:()=>loadTeamReport(window._trPeriod||'all'),
    tab26:()=>loadStateRegionStats(),
    tab27:()=>loadBattleMonitor(false),
    tab28:()=>loadBattleMonitor13a2(),
    tab30:()=>{ switchTab(30,null); },
    tab31:()=>window.loadCommandCenterOverview?.(true),
  };
  if(options.includeStatus && id!=='tab9') refreshWriterStats();
  loaders[id]?.();
}

let _dashboardTick=0;
let _dashboardTicker=null;
function startDashboardTicker(){
  const tick=()=>{
    if(document.visibilityState==='hidden') return;
    _dashboardTick++;
    const active=document.querySelector('.page.active')?.id;
    if(active==='tab27') loadBattleMonitor(false);
    else if(active==='tab28') loadBattleMonitor13a2();
    else if(active==='tab9' && _dashboardTick%3===0) refreshWriterStats();
  };
  if(window.DashboardRuntime?.createVisibilityTicker){
    _dashboardTicker=window.DashboardRuntime.createVisibilityTicker({
      documentRef:document,
      intervalMs:5000,
      onVisibleTick:tick,
    });
    _dashboardTicker.start();
  }else{
    setInterval(tick,5000);
  }
}

document.addEventListener('visibilitychange',()=>{
  if(document.visibilityState==='visible') refreshActivePage({includeStatus:true});
});
injectPageExportButtons();
loadHistory().then(()=>{ injectPageExportButtons(); startDashboardTicker(); connectSSE(); });

// ===== 城池地图 =====
let _mapData = [];
async function loadMapStats(){
  const r = await apiFetch('/api/map_stats');
  if(!r) return;
  const CTYPE = STZB_META.cityTypes;
  const CCOLOR = STZB_META.cityTypeColors;
  // 统计卡片
  const cards = document.getElementById('map-cards'); cards.innerHTML='';
  const typeCnt = {};
  (r.type_dist||[]).forEach(t=>{ typeCnt[t.cell_type]=(typeCnt[t.cell_type]||0)+t.cnt; });
  cards.innerHTML=`<div class='stat-card'><div class='val'>${r.total_cells||0}</div><div class='lbl'>已知格子</div></div>`;
  Object.entries(typeCnt).forEach(([t,c])=>{
    const tname = CTYPE[t]||('type'+t);
    const tcolor = CCOLOR[t]||'var(--text)';
    cards.innerHTML+=`<div class='stat-card'><div class='val' style='color:${tcolor}'>${c}</div><div class='lbl'>${tname}</div></div>`;
  });
  // 类型分布柱状图
  const bars = document.getElementById('map-type-bars'); bars.innerHTML='';
  const grouped = {};
  (r.type_dist||[]).forEach(t=>{
    const k = CTYPE[t.cell_type]||('type'+t.cell_type);
    grouped[k] = (grouped[k]||0)+t.cnt;
  });
  const maxV = Math.max(1,...Object.values(grouped));
  Object.entries(grouped).sort((a,b)=>b[1]-a[1]).forEach(([name,cnt])=>{
    const pct = Math.round(cnt/maxV*100);
    bars.innerHTML+=`<div class='bar-row'><div class='bar-label'>${esc(name)}</div><div class='bar-track'><div class='bar-fill' style='width:${pct}%;background:var(--gold)'></div></div><div class='bar-val'>${cnt}</div></div>`;
  });
  // 城池表格
  _mapData = r.named_cities||[];
  renderMapTable(_mapData);
}

function renderMapTable(data){
  const b = document.getElementById('map-body'); b.innerHTML='';
  const CTYPE = STZB_META.cityTypes;
  const CCOLOR = {8:'var(--blue)',11:'var(--cyan)',12:'var(--gold)',13:'var(--purple)',14:'var(--red)',17:'var(--green)',20:'var(--text2)',76:'#a0714f',70:'#888',71:'#b87333',72:'#c0c0c0',73:'#ffd700',74:'#00e5ff',75:'#aaa'};
  data.forEach(r=>{
    const tname = CTYPE[r.cell_type]||('type'+r.cell_type);
    const tcolor = CCOLOR[r.cell_type]||'var(--text)';
    b.innerHTML+=`<tr>
      <td style='font-family:Share Tech Mono,monospace;font-size:.7rem;color:var(--text2)'>${r.wid}</td>
      <td style='font-family:Share Tech Mono,monospace;font-size:.7rem'>(${r.x},${r.y})</td>
      <td><span class='badge' style='background:#111;color:${tcolor}'>${esc(tname)}</span></td>
      <td><b>${esc(r.city_name||'')}</b></td>
      <td style='color:var(--text2);font-size:.72rem'>${esc(r.owner_name||'')}</td>
      <td style='color:var(--text2);font-size:.68rem'>${esc((r.updated_at||'').slice(5,16))}</td>
    </tr>`;
  });
}

function filterMapCities(){
  const q = (document.getElementById('map-filter').value||'').toLowerCase();
  const filtered = q ? _mapData.filter(r=>(r.city_name||'').toLowerCase().includes(q)||(r.owner_name||'').toLowerCase().includes(q)) : _mapData;
  renderMapTable(filtered);
}

// ===== 玩家战绩 =====
let _psData = [];
async function loadPlayerStats(){
  const r = await apiFetch('/api/player_stats');
  if(!r) return;
  _psData = r;
  document.getElementById('ps-count').textContent = `共${r.length}人`;
  renderPlayerStats(_psData);
}

function renderPlayerStats(data){
  const b = document.getElementById('ps-body'); if(!b) return;
  b.innerHTML='';
  data.forEach(r=>{
    const wuxunStyle = r.wuxun_total>0 ? 'color:var(--gold)' : 'color:var(--text2)';
    const killStyle = r.kill_enemy_count>0 ? 'color:var(--red)' : 'color:var(--text2)';
    b.innerHTML+=`<tr>
      <td><b>${esc(r.user_name||'')}</b></td>
      <td style='font-family:Share Tech Mono,monospace;font-size:.7rem;color:var(--text2)'>${r.userid}</td>
      <td style='color:var(--blue)'>${r.city_count}</td>
      <td>${r.land_count}</td>
      <td style='color:var(--cyan);font-family:Share Tech Mono,monospace'>${fmt(r.force_max)}</td>
      <td style='font-family:Share Tech Mono,monospace'>${r.power_max}</td>
      <td style='${wuxunStyle};font-family:Share Tech Mono,monospace'>${fmt(r.wuxun_total)}</td>
      <td style='font-family:Share Tech Mono,monospace'>${fmt(r.wuxun_cur_week)}</td>
      <td style='${killStyle};font-family:Share Tech Mono,monospace'>${fmt(r.kill_enemy_count)}</td>
      <td style='font-family:Share Tech Mono,monospace'>${r.grab_land_count}</td>
      <td style='color:var(--text2);font-size:.72rem'>S${r.season}</td>
      <td style='color:var(--text2);font-size:.68rem'>${esc((r.updated_at||'').slice(5,16))}</td>
    </tr>`;
  });
}

function filterPlayerStats(){
  const q = (document.getElementById('ps-filter').value||'').toLowerCase();
  const filtered = q ? _psData.filter(r=>(r.user_name||'').toLowerCase().includes(q)) : _psData;
  renderPlayerStats(filtered);
}

// ===== 同盟成员 =====
const POS_MAP = {1:'盟主',2:'副盟主',3:'长老',4:'成员',5:'见习'};
let _tuData = [];
async function loadTeamUsers(){
  const [r, s] = await Promise.all([apiFetch('/api/team_users'), apiFetch('/api/team_stats')]);
  if(!r||!s) return;
  _tuData = r;
  document.getElementById('tu-count').textContent = `共${r.length}人`;
  // 统计卡片
  const cards = document.getElementById('team-cards'); cards.innerHTML='';
  cards.innerHTML=`<div class='stat-card'><div class='val'>${s.total}</div><div class='lbl'>同盟人数</div></div>`;
  const totalPower = r.reduce((a,b)=>a+(b.power||0),0);
  const totalWu = r.reduce((a,b)=>a+(b.wuxun||0),0);
  cards.innerHTML+=`<div class='stat-card'><div class='val' style='color:var(--gold)'>${fmt(totalPower)}</div><div class='lbl'>总势力值</div></div>`;
  cards.innerHTML+=`<div class='stat-card'><div class='val' style='color:var(--cyan)'>${fmt(totalWu)}</div><div class='lbl'>总武勋</div></div>`;
  // TOP10 武勋柱状图
  const bars = document.getElementById('tu-top-bars'); bars.innerHTML='';
  const maxV = Math.max(1,...(s.top_wuxun||[]).map(p=>p.wuxun||0));
  (s.top_wuxun||[]).forEach((p,i)=>{
    const pct = Math.round((p.wuxun||0)/maxV*100);
    bars.innerHTML+=`<div class='bar-row'><div class='bar-label'>${esc(p.name)}</div><div class='bar-track'><div class='bar-fill' style='width:${pct}%;background:var(--cyan)'></div></div><div class='bar-val'>${fmt(p.wuxun||0)}</div></div>`;
  });
  renderTeamUsers(_tuData);
}

function renderTeamUsers(data){
  const b = document.getElementById('tu-body'); if(!b) return; b.innerHTML='';
  const BATCH=80;
  function buildRow(r){
    const posName = POS_MAP[r.pos]||('职位'+r.pos);
    const posColor = r.pos===1?'var(--red)':r.pos===2?'var(--gold)':r.pos===3?'var(--cyan)':'var(--text2)';
    const jt = r.join_time ? new Date(r.join_time*1000).toLocaleDateString('zh-CN') : '';
    return `<tr>
      <td><b>${esc(r.name)}</b></td>
      <td style='font-family:Share Tech Mono,monospace;font-size:.7rem;color:var(--text2)'>${r.uid}</td>
      <td><span class='badge' style='color:${posColor};background:#111'>${posName}</span></td>
      <td style='color:var(--gold);font-family:Share Tech Mono,monospace'>${fmt(r.power)}</td>
      <td style='color:var(--cyan);font-family:Share Tech Mono,monospace'>${fmt(r.wuxun)}</td>
      <td style='font-family:Share Tech Mono,monospace'>${fmt(r.contribute_week)}</td>
      <td style='font-family:Share Tech Mono,monospace;color:var(--text2)'>${fmt(r.contribute_total)}</td>
      <td style='color:var(--text2);font-size:.72rem'>${esc(r.group_name||'未分组')}</td>
      <td style='color:var(--text2);font-size:.68rem'>${jt}</td>
    </tr>`;
  }
  function renderBatch(start){
    const end=Math.min(start+BATCH,data.length);
    const tmp=document.createElement('tbody');
    let html='';
    for(let i=start;i<end;i++) html+=buildRow(data[i]);
    tmp.innerHTML=html;
    const frag=document.createDocumentFragment();
    while(tmp.firstChild) frag.appendChild(tmp.firstChild);
    b.appendChild(frag);
    if(end<data.length) requestAnimationFrame(()=>renderBatch(end));
  }
  renderBatch(0);
}

function filterTeamUsers(){
  const q=(document.getElementById('tu-filter').value||'').toLowerCase();
  const filtered=q?_tuData.filter(r=>(r.name||'').toLowerCase().includes(q)):_tuData;
  renderTeamUsers(filtered);
}
let _baPage=1;
async function loadBattlesAll(page){
  _baPage=page||_baPage;
  const player=document.getElementById('ba-player').value;
  const union=document.getElementById('ba-union').value;
  const result=document.getElementById('ba-result').value;
  const ftype=document.getElementById('ba-ftype').value;
  const period=document.getElementById('ba-period').value;
  const url=`/api/battles_all?page=${_baPage}&size=50&player=${encodeURIComponent(player)}&union=${encodeURIComponent(union)}&result=${result}&fight_type=${ftype}&period=${period}`;
  const r=await apiFetch(url);
  if(!r)return;
  document.getElementById('ba-total').textContent=`共${r.total}条`;
  const b=document.getElementById('ba-body');b.innerHTML='';
  const RMAP={1:'badge-win',2:'badge-win',11:'badge-win',12:'badge-win',3:'badge-lose',4:'badge-lose',8:'badge-lose',13:'badge-lose',5:'badge-draw',0:'badge-draw'};
  (r.data||[]).forEach(row=>{
    const result=row.result;
    const rcText=result===0?'败':result===1?'胜':'平';
    const rcStyle=result===0?'color:var(--red)':result===1?'color:var(--green)':'color:var(--text2)';
    const ft=row.fight_type;
    function heroName(id){
      if(!id) return '';
      const h=(typeof HERO_CFG!=='undefined')&&HERO_CFG[String(id)];
      return h?h.name:String(id);
    }
    function heroTag(id){
      if(!id) return '';
      const name=heroName(id);
      const h=(typeof HERO_CFG!=='undefined')&&HERO_CFG[String(id)];
      const country=(h&&h.country)||'';
      const cc={'魏':'var(--blue)','蜀':'var(--green)','吴':'var(--red)','汉':'var(--gold)','晋':'var(--purple)','群':'var(--text2)'}[country]||'var(--text2)';
      return `<span style='font-size:.6rem;color:${cc};border:1px solid ${cc};border-radius:2px;padding:0 3px;margin-right:2px;white-space:nowrap'>${esc(name)}</span>`;
    }
    const atkHeroes=[row.atk_hero1_id,row.atk_hero2_id,row.atk_hero3_id].filter(Boolean).map(heroTag).join('');
    const defHeroes=[row.def_hero1_id,row.def_hero2_id,row.def_hero3_id].filter(Boolean).map(heroTag).join('');
    b.innerHTML+=`<tr style='cursor:pointer' onclick='showBattleDetail(${row.battle_id})'>
      <td style='font-family:Share Tech Mono,monospace;font-size:.68rem;color:var(--text2)'>${esc(row.time_str||'')}<br><span style='color:var(--text2);font-size:.62rem'>${ft}</span></td>
      <td><b>${esc(row.atk_name||'')}</b><br><span style='color:var(--text2);font-size:.65rem'>${esc(row.atk_union||'')}</span></td>
      <td style='white-space:nowrap'>${atkHeroes||'<span style="color:var(--text2);font-size:.6rem">-</span>'}</td>
      <td style='${rcStyle};font-weight:bold;text-align:center'>${rcText}</td>
      <td style='white-space:nowrap'>${defHeroes||'<span style="color:var(--text2);font-size:.6rem">-</span>'}</td>
      <td><b>${esc(row.def_name||'')}</b><br><span style='color:var(--text2);font-size:.65rem'>${esc(row.def_union||'')}</span></td>
      <td style='color:var(--blue);font-size:.72rem;text-align:center'>🔍</td>
    </tr>`;
  });
  // 分页
  const total=r.total,pages=Math.ceil(total/50);
  const pg=document.getElementById('ba-pages');pg.innerHTML='';
  if(pages>1){
    const start=Math.max(1,_baPage-2),end=Math.min(pages,_baPage+2);
    if(start>1)pg.innerHTML+=`<button class='btn' onclick='loadBattlesAll(1)'>1</button>`;
    for(let i=start;i<=end;i++){
      pg.innerHTML+=`<button class='btn${i===_baPage?" btn-primary":""}' onclick='loadBattlesAll(${i})'>${i}</button>`;
    }
    if(end<pages)pg.innerHTML+=`<button class='btn' onclick='loadBattlesAll(${pages})'>${pages}</button>`;
  }
}

// ===== 队伍统计 =====
async function loadTeamStats(){
  const player=document.getElementById('tm-player').value;
  const union=document.getElementById('tm-union').value;
  const side=document.getElementById('tm-side').value;
  const url=`/api/player_teams_stats?player=${encodeURIComponent(player)}&union=${encodeURIComponent(union)}&side=${side}&limit=200`;
  const data=await apiFetch(url);
  if(!data)return;
  document.getElementById('tm-total').textContent=`共${data.length}条`;
  const hint=document.getElementById('tm-hint');
  if(hint) hint.textContent='胜率口径：胜=1，平=0.5，负=0';
  const b=document.getElementById('tm-body');b.innerHTML='';
  data.forEach((r,i)=>{
    const cls=i<3?`rank-${i+1}`:'';
    const sideLabel=r.side==='atk'?'<span style="color:var(--red)">攻</span>':'<span style="color:var(--blue)">守</span>';
    const wrStyle=r.win_rate>=60?'color:var(--green)':r.win_rate>=40?'color:var(--gold)':'color:var(--red)';
    const loses=(r.used_count||0)-(r.win_count||0)-(r.draw_count||0);
    // 解析武将显示头像
    const heroes=(r.heroes_str||'').split(',').filter(Boolean);
    const heroHtml=heroes.map(h=>`<span style='background:var(--panel2);border:1px solid var(--border);border-radius:3px;padding:1px 6px;font-size:.68rem;margin-right:3px'>${esc(h)}</span>`).join('');
    b.innerHTML+=`<tr>
      <td class='${cls}'>${i+1}</td>
      <td><b>${esc(r.player_name||'')}</b></td>
      <td style='color:var(--text2);font-size:.72rem'>${esc(r.union_name||'')}</td>
      <td>${sideLabel}</td>
      <td>${heroHtml}</td>
      <td style='font-family:Share Tech Mono,monospace'>${r.used_count}</td>
      <td style='color:var(--green)'>${r.win_count}</td>
      <td style='color:var(--text2)'>${r.draw_count||0}</td>
      <td style='${wrStyle}'>${r.win_rate}%<span style='color:var(--text2);font-size:.72rem'>（${r.win_count||0}胜 / ${r.draw_count||0}平 / ${loses>0?loses:0}负）</span></td>
    </tr>`;
  });
}

// ===== 玩家队伍一览 =====
let _pbtCurrentRows = [];
let _pbtExpandedPlayers = new Set();
let _pbtActionModels = [];

const _organizationRequestRevisions = new Map();

function beginOrganizationRequest(key, panel){
  const revision = (_organizationRequestRevisions.get(key)||0) + 1;
  _organizationRequestRevisions.set(key, revision);
  panel?.classList.add('hud-refresh-line');
  panel?.setAttribute('aria-busy', 'true');
  return {key, revision, panel};
}

function isOrganizationRequestCurrent(request){
  return Boolean(
    request
    && _organizationRequestRevisions.get(request.key)===request.revision
  );
}

function finishOrganizationRequest(request){
  if(!isOrganizationRequestCurrent(request)) return false;
  request.panel?.classList.remove('hud-refresh-line');
  request.panel?.removeAttribute('aria-busy');
  return true;
}

function bindOrganizationActions(root, actionModels=[]){
  if(!root?.addEventListener) return;
  root._organizationActionModels = actionModels;
  if(root._organizationActionsBound) return;
  root._organizationActionsBound = true;
  root.addEventListener('error', event=>{
    const image = event.target?.closest?.('[data-organization-image]');
    if(image && (!root.contains || root.contains(image))){
      image.hidden = true;
    }
  }, true);
  root.addEventListener('click', event=>{
    const trigger = event.target?.closest?.('[data-organization-action]');
    if(!trigger || (root.contains && !root.contains(trigger))) return;
    const action = trigger.dataset.organizationAction;
    const index = Number(trigger.dataset.organizationIndex);
    const model = Number.isInteger(index)
      ? root._organizationActionModels?.[index]
      : null;
    if(action==='toggle-player' && model){
      event.preventDefault?.();
      togglePlayerBattleTeams(model.key);
    }else if(action==='expand-player'){
      event.preventDefault?.();
      expandAllPlayerBattleTeams();
    }else if(action==='collapse-player'){
      event.preventDefault?.();
      collapseAllPlayerBattleTeams();
    }else if(action==='toggle-alliance' && model){
      event.preventDefault?.();
      toggleAlliancePlayerTeams(model.key);
    }else if(action==='expand-alliance'){
      event.preventDefault?.();
      expandAllAlliancePlayerTeams();
    }else if(action==='collapse-alliance'){
      event.preventDefault?.();
      collapseAllAlliancePlayerTeams();
    }
  });
}

function organizationIdentityMarkup(name, meta){
  const displayName = String(name||'未知');
  const initial = displayName.trim().slice(0,1) || '?';
  return `<span class='organization-identity'>
    <span class='organization-avatar'>${esc(initial)}</span>
    <span><strong>${esc(displayName)}</strong><small>${esc(meta||'组织成员')}</small></span>
  </span>`;
}

function organizationGroupChip(value){
  return `<span class='organization-group-chip'>${esc(value||'未分组')}</span>`;
}

function organizationActivityPercent(value, maximum){
  return Math.max(
    0,
    Math.min(
      100,
      Math.round(Number(value||0)/Math.max(1,Number(maximum||0))*100),
    ),
  );
}

function organizationActivityMarkup(value, maximum, label){
  const percent = organizationActivityPercent(value, maximum);
  return `<span class='organization-activity'><span>${esc(label||'活跃')} ${Number(value||0)}</span>
    <span class='organization-activity-track' style='--organization-activity:${percent}%'><i></i></span>
  </span>`;
}

function syncOrganizationRowState(row, selected, rowData={}){
  if(typeof applyOrganizationRowState==='function'){
    return applyOrganizationRowState(row, selected, rowData);
  }
  if(!row) return row;
  row.dataset.selected = String(selected);
  row.dataset.state = rowData.isStale ? "stale" : "current";
  return row;
}

function syncOrganizationRows(container, rowStates=[]){
  if(!container) return;
  container.querySelectorAll('.organization-row').forEach((row,index)=>{
    const rowState = rowStates[index] || {};
    syncOrganizationRowState(row, Boolean(rowState.selected), rowState);
  });
}

function organizationLineupCard(content, emptyMessage='暂无阵容'){
  const body = content
    ? `<div class='organization-lineup'>${content}</div>`
    : `<div class='hud-state hud-state-empty'>${esc(emptyMessage)}</div>`;
  return `<div class='organization-lineup-card'>${body}</div>`;
}

function renderOrganizationStateHost(stateHost, state){
  if(!stateHost) return null;
  stateHost.hidden = false;
  const rendered = window.HudSystem?.renderState(stateHost, {
    ...state,
    replace: true,
  });
  if(!rendered){
    stateHost.className = `organization-status-host hud-state hud-state-${state.kind||'empty'}`;
    stateHost.textContent = state.message || (state.kind==='error' ? '加载失败' : '暂无数据');
  }
  return rendered;
}

function ensureOrganizationStatusHost(panel){
  if(!panel) return null;
  let stateHost = panel.querySelector?.('.organization-status-host');
  if(stateHost) return stateHost;
  stateHost = document.createElement('div');
  stateHost.className = 'organization-status-host';
  stateHost.hidden = true;
  if(panel.insertBefore) panel.insertBefore(stateHost, panel.firstChild||null);
  else panel.appendChild?.(stateHost);
  return stateHost;
}

function clearOrganizationStatusHost(stateHost){
  if(!stateHost) return;
  stateHost.hidden = true;
  stateHost.className = 'organization-status-host';
  stateHost.replaceChildren?.();
  stateHost.textContent = '';
}

function renderOrganizationTableState(tbody, state, colspan=8){
  if(!tbody) return;
  const row = document.createElement('tr');
  const cell = document.createElement('td');
  const stateHost = document.createElement('div');
  row.className = 'organization-state-row';
  cell.colSpan = colspan;
  cell.appendChild(stateHost);
  row.appendChild(cell);
  tbody.replaceChildren(row);
  renderOrganizationStateHost(stateHost, state);
}

function renderOrganizationLoadError(tbody, stateHost, state, colspan=8){
  if(tbody && (tbody.childElementCount>0 || tbody.querySelector?.('.organization-row'))){
    renderOrganizationStateHost(stateHost, state);
    return 'nonblocking';
  }
  clearOrganizationStatusHost(stateHost);
  renderOrganizationTableState(tbody, state, colspan);
  return 'blocking';
}

function expandAllPlayerBattleTeams(){
  _pbtExpandedPlayers = new Set((_pbtCurrentRows||[]).map(r=>`${r.player_name||''}__${r.union||r.union_name||''}`).filter(Boolean));
  renderPlayerBattleTeams(_pbtCurrentRows);
}

function collapseAllPlayerBattleTeams(){
  _pbtExpandedPlayers = new Set();
  renderPlayerBattleTeams(_pbtCurrentRows);
}

function togglePlayerBattleTeams(playerKey){
  if(_pbtExpandedPlayers.has(playerKey)) _pbtExpandedPlayers.delete(playerKey);
  else _pbtExpandedPlayers.add(playerKey);
  renderPlayerBattleTeams(_pbtCurrentRows);
}

function renderPlayerBattleTeams(rows){
  _pbtCurrentRows = rows || [];
  const b=document.getElementById('pbt-body');
  const countEl=document.getElementById('pbt-count');
  if(!b || !countEl) return;
  b.innerHTML='';
  if(!_pbtCurrentRows.length){
    countEl.textContent='0条';
    renderOrganizationTableState(b, {
      kind:'empty',
      message:'暂无符合条件的玩家队伍',
      replace:true,
    });
    return;
  }

  const getPlayerKey = r => `${r.player_name||''}__${r.union||r.union_name||''}`;
  const validKeys = new Set(_pbtCurrentRows.map(getPlayerKey).filter(Boolean));
  _pbtExpandedPlayers = new Set([..._pbtExpandedPlayers].filter(k=>validKeys.has(k)));
  const actionHtml = validKeys.size ? `<span style='margin-left:10px;display:inline-flex;gap:6px'><button class='btn' type='button' data-organization-action='expand-player' style='font-size:.68rem;padding:2px 8px'>全部展开</button><button class='btn' type='button' data-organization-action='collapse-player' style='font-size:.68rem;padding:2px 8px'>全部收起</button></span>` : '';
  countEl.innerHTML = `共${_pbtCurrentRows.length}条 · ${validKeys.size}名玩家${actionHtml}`;
  bindOrganizationActions(countEl);

  const grouped = [];
  let currentPlayerKey = null;
  _pbtCurrentRows.forEach(r=>{
    const playerKey = getPlayerKey(r);
    if(playerKey !== currentPlayerKey){
      grouped.push({
        type:'player',
        key: playerKey,
        playerName: r.player_name||'',
        unionName: r.union||r.union_name||'',
        clanName: r.clan_name||'',
        teamCount: r._player_team_count||0,
        totalBattles: r._player_total_battles||0,
        totalWins: r._player_total_wins||0,
        totalDraws: r._player_total_draws||0,
        winRate: r._player_win_rate||0,
        mainTeamText: r._player_main_team_text||'—',
        mainTeamCount: r._player_main_team_count||0,
        mainTeamHeroes: r._player_main_team_heroes||[],
        isStale: Boolean(r.isStale),
      });
      currentPlayerKey = playerKey;
    }
    grouped.push({type:'team', ...r, _playerKey: playerKey});
  });

  let rank = 0;
  const rowStates = [];
  const actionModels = [];
  const activityMaximum = Math.max(
    1,
    ...grouped
      .filter(item=>item.type==='player')
      .map(item=>Number(item.totalBattles)||0),
  );
  const html = grouped.map(item=>{
    if(item.type==='player'){
      const expanded = _pbtExpandedPlayers.has(item.key);
      const arrow = expanded ? '▾' : '▸';
      const wrColor = item.winRate>=60?'var(--green)':item.winRate>=40?'var(--gold)':'var(--red)';
      const loseCount = Math.max(0, item.totalBattles - item.totalWins - item.totalDraws);
      const mainTeamAvatarHtml = (item.mainTeamHeroes||[]).slice(0,3).map(buildAllianceHeroMiniCard).join('');
      const actionIndex = actionModels.push({key:item.key}) - 1;
      rowStates.push({
        selected:expanded,
        isStale:Boolean(item.isStale),
      });
      return `<tr class='organization-row' data-selected='${expanded?'true':'false'}' data-state='${item.isStale?'stale':'current'}' data-organization-action='toggle-player' data-organization-index='${actionIndex}' style='cursor:pointer'>
        <td style='color:var(--gold);font-family:Share Tech Mono,monospace'>${arrow}</td>
        <td>${organizationIdentityMarkup(item.playerName, `${expanded?'点击收起':'点击展开'} · ${item.teamCount}支队伍`)}</td>
        <td>
          <div class='organization-lineup'>
            ${item.unionName?organizationGroupChip(item.unionName):''}
            ${item.clanName?organizationGroupChip(item.clanName):''}
          </div>
        </td>
        <td>
          <div style='color:var(--text2);font-size:.72rem'>总队伍：${item.teamCount}</div>
          <div style='display:flex;align-items:flex-start;gap:8px;margin-top:5px'>
            ${organizationLineupCard(mainTeamAvatarHtml)}
            <div style='min-width:0'>
              <div style='color:var(--gold);font-size:.7rem'>常用主力队</div>
              <div style='color:var(--text2);font-size:.66rem;line-height:1.35;margin-top:2px'>${esc(item.mainTeamText)}<span style='color:var(--text2)'> × ${item.mainTeamCount}</span></div>
            </div>
          </div>
        </td>
        <td>${organizationActivityMarkup(item.totalBattles, activityMaximum, '战数')}</td>
        <td style='color:var(--green)'>${item.totalWins}</td>
        <td style='color:var(--text2)'>${item.totalDraws}</td>
        <td style='color:${wrColor}'>${item.winRate}%<span style='color:var(--text2);font-size:.72rem'>（${item.totalWins}胜 / ${item.totalDraws}平 / ${loseCount}负）</span></td>
      </tr>`;
    }
    if(!_pbtExpandedPlayers.has(item._playerKey)) return '';
    rank += 1;
    const wrStyle=item.win_rate>=60?'color:var(--green)':item.win_rate>=40?'color:var(--gold)':'color:var(--red)';
    const loses=(item.cnt||0)-(item.wins||0)-(item.draws||0);
    const heroIds=(item.heroes_str||'').split('+').filter(Boolean);
    const heroStars=item.hero_stars||[0,0,0];
    const skillIds=(item.skills||'').split(',').filter(Boolean);
    const heroLevels=(item.hero_levels||'').split(',').map(l=>Number(l)||0);
    const heroHtml=heroIds.map((hid,hi)=>{
      let name=hid;
      if(typeof HERO_CFG!=='undefined'&&HERO_CFG[hid])name=HERO_CFG[hid].name||hid;
      const hcfg=(typeof HERO_CFG!=='undefined'&&HERO_CFG[hid])||{};
      const country=hcfg.country||'';
      const countryColor={'魏':'var(--blue)','蜀':'var(--green)','吴':'var(--red)','汉':'var(--gold)','晋':'var(--purple)','群':'var(--text2)'}[country]||'var(--text2)';
      const s=heroStars[hi]||0;
      const lv=heroLevels[hi]||0;
      const starColor=s>=12?'var(--red)':s>=6?'var(--gold)':s>0?'var(--cyan)':'var(--text2)';
      const starStr=s>0?`<span style='color:${starColor};font-size:.58rem;margin-left:2px'>★${s}</span>`:`<span style='color:var(--text2);font-size:.58rem;margin-left:2px'>★0</span>`;
      const lvStr=lv>0?`<span style='color:var(--gold2);font-size:.58rem;margin-left:4px;opacity:.85'>Lv${lv}</span>`:'';
      const heroSkills=skillIds.slice(hi*3,hi*3+3);
      const skillsHtml=heroSkills.map(sid=>{
        let sname=sid;
        if(typeof SKILL_CFG!=='undefined'&&SKILL_CFG[sid])sname=SKILL_CFG[sid].name||sid;
        return `<span style='font-size:.58rem;color:var(--cyan);background:#0d1820;border-radius:2px;padding:0 3px;margin:1px 1px 0 0;white-space:nowrap'>${esc(sname)}</span>`;
      }).join('');
      return `<span class='organization-hero-mini' style='border-color:${countryColor}'><strong style='color:${countryColor}'>${esc(name)}${starStr}${lvStr}</strong><span>${skillsHtml}</span></span>`;
    }).join('');
    rowStates.push({
      selected:false,
      isStale:Boolean(item.isStale),
    });
    return `<tr class='organization-row' data-selected='false' data-state='${item.isStale?'stale':'current'}'>
      <td style='color:var(--text2);font-size:.72rem;padding-left:22px'>${rank}</td>
      <td style='color:var(--text2);font-size:.72rem'>└ 队伍</td>
      <td><span class='hud-status-chip'>明细</span></td>
      <td style='max-width:480px'>${organizationLineupCard(heroHtml)}</td>
      <td style='font-family:Share Tech Mono,monospace'>${item.cnt}</td>
      <td style='color:var(--green)'>${item.wins}</td>
      <td style='color:var(--text2)'>${item.draws||0}</td>
      <td style='${wrStyle}'>${item.win_rate}%<span style='color:var(--text2);font-size:.72rem'>（${item.wins||0}胜 / ${item.draws||0}平 / ${loses>0?loses:0}负）</span></td>
    </tr>`;
  }).join('');
  b.innerHTML = html;
  _pbtActionModels = actionModels;
  bindOrganizationActions(b, _pbtActionModels);
  syncOrganizationRows(b, rowStates);
}

async function loadPlayerBattleTeams(){
  const player=document.getElementById('pbt-player').value;
  const union=document.getElementById('pbt-union').value;
  const side=document.getElementById('pbt-side').value;
  const url=`/api/player_battle_teams?player=${encodeURIComponent(player)}&union=${encodeURIComponent(union)}&side=${encodeURIComponent(side)}&_t=${Date.now()}`;
  const b=document.getElementById('pbt-body');
  const countEl=document.getElementById('pbt-count');
  const panel=b?.closest('.organization-table-panel');
  const request=beginOrganizationRequest('player-teams',panel);
  let statusHost=null;
  try{
  statusHost=ensureOrganizationStatusHost(panel);
  const hint=document.getElementById('pbt-hint');
  if(hint) hint.textContent='';
  const data=await apiFetch(url);
  if(!isOrganizationRequestCurrent(request)) return;
  if(!data){
    throw new Error('玩家队伍请求失败');
  }
  if(data.error) throw new Error(String(data.error));
  if(!Array.isArray(data)) throw new Error('玩家队伍数据格式异常');
  clearOrganizationStatusHost(statusHost);

  const filtered = dedupeBattleTeamsByHeroNames((data||[]).filter(r=>{
    const rowUnion = String(r.union||r.union_name||'').trim();
    if(union && rowUnion !== String(union).trim()) return false;
    const heroCount = (r.heroes_str||'').split('+').filter(id=>Number(id)>0).length;
    if(heroCount < 3) return false;
    const skillIds = (r.skills||'').split(',').filter(id=>Number(id)>0);
    if(skillIds.length < 9) return false;
    const everyHeroHasThreeSkills = [0,1,2].every(idx=>skillIds.slice(idx*3, idx*3+3).length === 3);
    if(!everyHeroHasThreeSkills) return false;
    const troops = Number(r.max_troops)||0;
    if(troops > 0 && troops < 10000) return false;
    return true;
  }), r=>`${r.player_name||''}__${r.union||r.union_name||''}`);

  if(!filtered.length){
    _pbtCurrentRows = [];
    _pbtExpandedPlayers = new Set();
    countEl.textContent='0条';
    renderOrganizationTableState(b, {
      kind:'empty',
      message:'暂无符合条件的玩家队伍',
      replace:true,
    });
    return;
  }

  const playerStats = new Map();
  filtered.forEach(r=>{
    const key = `${r.player_name||''}__${r.union||r.union_name||''}`;
    if(!playerStats.has(key)){
      playerStats.set(key, {
        teamCount: 0,
        totalBattles: 0,
        totalWins: 0,
        totalDraws: 0,
        mainTeamText: '—',
        mainTeamCount: 0,
        mainTeamHeroes: [],
      });
    }
    const s = playerStats.get(key);
    s.teamCount += 1;
    s.totalBattles += (r.cnt || 0);
    s.totalWins += (r.wins || 0);
    s.totalDraws += (r.draws || 0);
    if((r.cnt || 0) > s.mainTeamCount){
      s.mainTeamCount = r.cnt || 0;
      const heroIds = (r.heroes_str||'').split('+').filter(id=>Number(id)>0);
      const heroNames = heroIds.map(hid=>{
        if(typeof HERO_CFG!=='undefined'&&HERO_CFG[hid]) return HERO_CFG[hid].name||hid;
        return hid;
      });
      s.mainTeamHeroes = heroIds;
      s.mainTeamText = heroNames.join(' / ') || '—';
    }
  });

  filtered.sort((a,b)=>{
    const bKey = `${b.player_name||''}__${b.union||b.union_name||''}`;
    const aKey = `${a.player_name||''}__${a.union||a.union_name||''}`;
    const ba = Number(playerStats.get(bKey)?.totalBattles||0);
    const aa = Number(playerStats.get(aKey)?.totalBattles||0);
    if(ba !== aa) return ba - aa;
    const unionCmp = String(a.union||a.union_name||'').localeCompare(String(b.union||b.union_name||''),'zh-CN');
    if(unionCmp!==0) return unionCmp;
    const pcmp=String(a.player_name||'').localeCompare(String(b.player_name||''),'zh-CN');
    if(pcmp!==0) return pcmp;
    return (Number(b.cnt)||0)-(Number(a.cnt)||0);
  });

  filtered.forEach(r=>{
    const s = playerStats.get(`${r.player_name||''}__${r.union||r.union_name||''}`);
    r._player_team_count = s?.teamCount || 0;
    r._player_total_battles = s?.totalBattles || 0;
    r._player_total_wins = s?.totalWins || 0;
    r._player_total_draws = s?.totalDraws || 0;
    r._player_win_rate = (s && s.totalBattles) ? Math.round((s.totalWins + s.totalDraws * 0.5) / s.totalBattles * 1000) / 10 : 0;
    r._player_main_team_text = s?.mainTeamText || '—';
    r._player_main_team_count = s?.mainTeamCount || 0;
    r._player_main_team_heroes = s?.mainTeamHeroes || [];
  });

  renderPlayerBattleTeams(filtered);
  }catch(error){
    if(isOrganizationRequestCurrent(request)){
      renderOrganizationLoadError(b, statusHost, {
        kind:'error',
        message:error?.message || '玩家队伍加载失败',
        replace:true,
      });
    }
  }finally{
    finishOrganizationRequest(request);
  }
}

// ===== 分组武勋 (Tab 15) =====
async function loadGroupWu(){
  const data = await apiFetch('/api/group_wu');
  const b = document.getElementById('gw-body'); b.innerHTML='';
  const cards = document.getElementById('gw-cards'); cards.innerHTML='';
  if(!data||!data.length){
    b.innerHTML=`<tr><td colspan=6 style='color:var(--text2);text-align:center;padding:20px'>暂无分组数据，请先在同盟成员页同步成员</td></tr>`;
    return;
  }
  // 统计卡片
  const totalMem = data.reduce((s,r)=>s+r.member_count,0);
  const totalWu  = data.reduce((s,r)=>s+r.total_wu,0);
  const totalZero= data.reduce((s,r)=>s+r.zero_wu_count,0);
  cards.innerHTML=`
    <div class='stat-card'><div class='val'>${data.length}</div><div class='lbl'>分组数</div></div>
    <div class='stat-card'><div class='val'>${totalMem}</div><div class='lbl'>总人数</div></div>
    <div class='stat-card'><div class='val' style='color:var(--gold)'>${fmt(totalWu)}</div><div class='lbl'>总武勋</div></div>
    <div class='stat-card'><div class='val' style='color:var(--red)'>${totalZero}</div><div class='lbl'>0武勋人数</div></div>
  `;
  const maxWu = Math.max(1,...data.map(r=>r.total_wu));
  data.forEach(r=>{
    const pct = Math.round(r.total_wu/maxWu*100);
    const zeroRate = r.member_count>0?Math.round(r.zero_wu_count/r.member_count*100):0;
    const zeroColor = zeroRate>50?'var(--red)':zeroRate>20?'var(--gold)':'var(--green)';
    b.innerHTML+=`<tr>
      <td><b style='color:var(--gold)'>${esc(r.group||'未分组')}</b></td>
      <td>${r.member_count}</td>
      <td style='font-family:Share Tech Mono,monospace;color:var(--gold)'>${fmt(r.total_wu)}</td>
      <td style='font-family:Share Tech Mono,monospace'>${fmt(r.average_wu)}</td>
      <td style='color:${zeroColor}'>${r.zero_wu_count} (${zeroRate}%)</td>
      <td style='min-width:120px'><div style='height:6px;background:var(--border);border-radius:3px'><div style='height:100%;width:${pct}%;background:var(--gold);border-radius:3px'></div></div></td>
    </tr>`;
  });
}

// ===== 攻城考勤 (Tab 16) =====
const _legacyLoaderRequestOwners = new Map();

function beginLegacyLoaderRequest(key, panel, hasSnapshot=false){
  const revision = (_legacyLoaderRequestOwners.get(key)?.revision||0) + 1;
  const request = {
    key,
    revision,
    panel,
    hasSnapshot:Boolean(hasSnapshot),
    controller:null,
  };
  _legacyLoaderRequestOwners.set(key, request);
  panel?.classList.add('hud-refresh-line');
  panel?.setAttribute('aria-busy','true');
  return request;
}

function isLegacyLoaderRequestCurrent(request){
  return Boolean(
    request
    && _legacyLoaderRequestOwners.get(request.key)===request
  );
}

function finishLegacyLoaderRequest(request){
  if(!isLegacyLoaderRequestCurrent(request)) return false;
  request.panel?.classList.remove('hud-refresh-line');
  request.panel?.removeAttribute('aria-busy');
  return true;
}

function ensureLegacyLoaderStatusHost(panel, className='legacy-loader-status'){
  if(!panel) return null;
  let host = panel._legacyLoaderStatusHost
    || panel.querySelector?.(`.${className}`);
  if(host) return host;
  host = document.createElement('div');
  host.className = className;
  host.hidden = true;
  panel._legacyLoaderStatusHost = host;
  if(panel.insertBefore) panel.insertBefore(host,panel.firstChild||null);
  else panel.appendChild?.(host);
  return host;
}

function clearLegacyLoaderStatus(host){
  if(!host) return;
  host.hidden = true;
  host.removeAttribute?.('aria-busy');
  host.className = host.dataset.baseClass || 'legacy-loader-status';
  host.replaceChildren?.();
  host.textContent = '';
}

function renderLegacyLoaderStatus(host, kind, message){
  if(!host) return;
  host.hidden = false;
  host.dataset.baseClass ||= host.className || 'legacy-loader-status';
  const rendered = window.HudSystem?.renderState(host,{
    kind,
    message,
    replace:true,
  });
  if(!rendered){
    host.className = `${host.dataset.baseClass} hud-state hud-state-${kind}`;
    host.textContent = message;
  }
}

function legacyLoaderSurface(body, fallbackPanelId){
  return body?.closest?.('.hud-panel,.hud-page')
    || document.getElementById(fallbackPanelId);
}

let _currentTaskDetail = null;
let _taskModels = [];
let previousTaskStages = new Map();
let hasRenderedTaskStages = false;
let hasTaskSnapshot = false;
const OPERATION_STAGES = [
  {key:'preparing', label:'任务准备'},
  {key:'assembling', label:'成员集结'},
  {key:'executing', label:'攻城执行'},
  {key:'complete', label:'统计完成'},
];

function attendanceStage(task){
  const now = Date.now() / 1000;
  const taskTime = Number(task.task_time || task.time || 0);
  if(task.statistics_done || Number(task.status) === 1) return 'complete';
  if(taskTime && taskTime <= now) return 'executing';
  if(Number(task.actual_count || task.complete_user_num || 0) > 0) return 'assembling';
  return 'preparing';
}

function operationStageStrip(activeStage){
  return `<div class='operation-stage-strip'>${OPERATION_STAGES.map(stage =>
    `<span class='operation-stage ${stage.key===activeStage?'is-active':''}' data-stage='${stage.key}' data-state='${stage.key===activeStage?'active':'pending'}'>${stage.label}</span>`
  ).join('')}</div>`;
}

function taskStageKey(task, index){
  const taskId = task?.id;
  if(
    typeof taskId === 'number'
    && Number.isSafeInteger(taskId)
    && taskId > 0
  ){
    return `id-${taskId}`;
  }
  const safeIndex = Number.isSafeInteger(index) && index >= 0
    ? index
    : 0;
  return `index-${safeIndex}`;
}

function operationStageEvents(previousStages, tasks, initialized){
  if(!initialized) return [];
  const stageLabels = {
    preparing:'任务准备',
    assembling:'成员集结',
    executing:'攻城执行',
    complete:'统计完成',
  };
  return tasks.flatMap(task=>{
    const safeIndex = Number.isSafeInteger(task.index) && task.index >= 0
      ? task.index
      : 0;
    const taskKey = /^(?:id|index)-\d+$/.test(String(task.key||''))
      ? String(task.key)
      : `index-${safeIndex}`;
    const previousStage = previousStages.get(taskKey);
    if(!previousStages.has(taskKey) || previousStage === task.stage) return [];
    const stageLabel = stageLabels[task.stage];
    if(!stageLabel) return [];
    return [{
      type:'operation:stage-changed',
      target:task.target || (
        `[data-task-index="${safeIndex}"]`
      ),
      domain:'operations',
      severity:'info',
      message:`${task.name} 进入 ${stageLabel}`,
      dedupeKey:`operation-task:${taskKey}:${task.stage}`,
    }];
  });
}

function taskApiId(rawTaskId){
  if(
    typeof rawTaskId !== 'number'
    || !Number.isSafeInteger(rawTaskId)
    || rawTaskId < 1
  ){
    showToast('任务 ID 无效，操作已拒绝','var(--red)');
    return null;
  }
  return rawTaskId;
}

function bindTaskActions(root){
  if(!root?.addEventListener || root._taskActionsBound) return;
  root._taskActionsBound = true;
  root.addEventListener('click', event=>{
    const trigger = event.target?.closest?.('[data-task-action]');
    if(!trigger || (root.contains && !root.contains(trigger))) return;
    event.preventDefault?.();
    const index = Number(trigger.dataset.taskIndex);
    const task = Number.isSafeInteger(index) && index >= 0
      ? _taskModels[index]
      : null;
    if(!task){
      showToast('任务 ID 无效，操作已拒绝','var(--red)');
      return;
    }
    const action = trigger.dataset.taskAction;
    if(action === 'detail') viewTaskDetail(task.id);
    else if(action === 'statistics') doStatistics(task.id,trigger);
    else if(action === 'delete') deleteTask(task.id);
  });
}

async function loadTasks(){
  const b = document.getElementById('task-body');
  const cards = document.getElementById('task-cards');
  if(!b || !cards) return;
  bindTaskActions(b);
  const panel = legacyLoaderSurface(b,'tab16');
  const request = beginLegacyLoaderRequest('tasks',panel,hasTaskSnapshot);
  const statusHost = ensureLegacyLoaderStatusHost(panel);
  const cnt = document.getElementById('task-count');
  if(!request.hasSnapshot){
    renderLegacyLoaderStatus(statusHost,'loading','正在加载攻城任务…');
  }else{
    clearLegacyLoaderStatus(statusHost);
  }
  try{
  const data = await apiFetch('/api/tasks');
  if(!isLegacyLoaderRequestCurrent(request)) return;
  if(!Array.isArray(data)){
    throw new Error(data?.error || '攻城任务暂时不可用');
  }
  const tasks = data;
  _taskModels = tasks.slice();
  if(cnt) cnt.textContent=`共${tasks.length}个任务`;
  const taskStageModels = tasks.map((task,index)=>({
    key:taskStageKey(task,index),
    stage:attendanceStage(task),
  }));
  const currentTaskStages = new Map(taskStageModels.map(task=>[
    task.key,
    task.stage,
  ]));
  const stageCounts = taskStageModels.reduce((counts, task)=>{
    const stage = task.stage;
    counts[stage] = (counts[stage]||0) + 1;
    return counts;
  }, {});
  b.innerHTML='';
  cards.innerHTML = [
    ['待准备', stageCounts.preparing||0, 'var(--text-tertiary)'],
    ['集结中', stageCounts.assembling||0, 'var(--warning)'],
    ['执行中', stageCounts.executing||0, 'var(--domain-operations)'],
    ['已完成', stageCounts.complete||0, 'var(--success)'],
  ].map(([label,value,color]) =>
    `<div class='hud-kpi' style='--hud-kpi-accent:${color}'><div class='hud-kpi-label'>${label}</div><div class='hud-kpi-value'>${value}</div></div>`
  ).join('');
  if(!tasks.length){
    b.innerHTML=`<tr><td colspan=7 style='color:var(--text2);text-align:center;padding:20px'>暂无任务，点击「新建任务」创建</td></tr>`;
    previousTaskStages = currentTaskStages;
    hasRenderedTaskStages = true;
    hasTaskSnapshot = true;
    clearLegacyLoaderStatus(statusHost);
    return;
  }
  b.innerHTML=tasks.map((t,index)=>{
    const stage = taskStageModels[index].stage;
    const timeStr = t.time ? new Date(t.time*1000).toLocaleString('zh-CN') : '-';
    const groups = (t.target_groups||[]).join('、') || '全员';
    const statusBadge = t.status===1
      ? `<span class='badge badge-win'>已统计</span>`
      : `<span class='badge badge-draw'>待考勤</span>`;
    const posLabel = `<span class='operation-target'><strong>${esc(t.wid_name||'目标地块')}</strong>${esc(t.pos_xy||t.pos)}</span>`;
    const targetCount = Number(t.target_user_num||0);
    const completeCount = Number(t.complete_user_num||0);
    const progress = targetCount ? Math.min(100, Math.round(completeCount/targetCount*100)) : 0;
    return `<tr data-task-index='${index}'>
      <td><b>${esc(t.name)}</b> ${statusBadge}${operationStageStrip(stage)}</td>
      <td>${posLabel}</td>
      <td style='font-size:.72rem'>${timeStr}</td>
      <td><span class='operation-member-chip'>${esc(groups)}</span></td>
      <td>${targetCount}</td>
      <td><div class='operation-progress'><strong>${completeCount}</strong><div class='operation-progress-track' style='--operation-progress:${progress}%'><i></i></div></div></td>
      <td>
        <button class='btn' type='button' data-task-action='detail' data-task-index='${index}'>考勤详情</button>
        <button class='btn' type='button' data-task-action='statistics' data-task-index='${index}'>开始统计</button>
        <button class='btn' type='button' data-task-action='delete' data-task-index='${index}'>删除</button>
      </td>
    </tr>`;
  }).join('');
  const taskRows = [...(b.querySelectorAll?.('tr[data-task-index]') || [])];
  operationStageEvents(
    previousTaskStages,
    tasks.map((task,index)=>({
      key:taskStageModels[index].key,
      index,
      name:task.name,
      stage:taskStageModels[index].stage,
      target:taskRows[index] || null,
    })),
    hasRenderedTaskStages,
  ).forEach(event=>window.HudSystem?.emit({
    ...event,
    timestamp:Date.now(),
  }));
  previousTaskStages = currentTaskStages;
  hasRenderedTaskStages = true;
  hasTaskSnapshot = true;
  clearLegacyLoaderStatus(statusHost);
  }catch(error){
    if(!isLegacyLoaderRequestCurrent(request)) return;
    const message=error?.message || '攻城任务加载失败';
    if(request.hasSnapshot){
      renderLegacyLoaderStatus(statusHost,'error',message);
    }else{
      cards.innerHTML='';
      b.innerHTML=`<tr><td colspan=7 style='color:var(--red);text-align:center;padding:20px'>${esc(message)}</td></tr>`;
      renderLegacyLoaderStatus(statusHost,'error',message);
    }
  }finally{
    finishLegacyLoaderRequest(request);
  }
}

async function viewTaskDetail(rawTaskId){
  const tid = taskApiId(rawTaskId);
  if(tid === null) return;
  const data = await apiFetch(`/api/tasks/${tid}`);
  if(!data||data.error)return;
  _currentTaskDetail = data;
  const panel = document.getElementById('task-detail-panel');
  const title = document.getElementById('task-detail-title');
  const b = document.getElementById('task-detail-body');
  title.textContent=`考勤详情 — ${data.name}`;
  b.innerHTML='';
  const userList = data.user_list||{};
  const users = Object.values(userList).sort((a,z)=>(z.atk_num+z.dis_num)-(a.atk_num+a.dis_num));
  users.forEach(u=>{
    const attended = u.atk_num>0||u.dis_num>0;
    const atkTeam = u.atk_team_num||0;
    const disTeam = u.dis_team_num||0;
    b.innerHTML+=`<tr>
      <td><b>${esc(u.name)}</b></td>
      <td style='color:var(--text2)'>${esc(u.group||'')}</td>
      <td style='color:var(--${u.atk_num>0?"green":"text2"});font-family:Share Tech Mono,monospace'>${u.atk_num}</td>
      <td style='color:var(--${u.dis_num>0?"cyan":"text2"});font-family:Share Tech Mono,monospace'>${u.dis_num}</td>
      <td style='color:var(--${atkTeam>0?"gold":"text2"});font-family:Share Tech Mono,monospace'>${atkTeam}</td>
      <td style='color:var(--${disTeam>0?"purple":"text2"});font-family:Share Tech Mono,monospace'>${disTeam}</td>
      <td>${attended?`<span class='badge badge-win'>出战</span>`:`<span class='badge badge-lose'>缺勤</span>`}</td>
    </tr>`;
  });
  panel.classList.remove('is-hidden');
}

function closeTaskDetail(){
  document.getElementById('task-detail-panel').classList.add('is-hidden');
  document.getElementById('task-battles-wrap').classList.add('is-hidden');
  _currentTaskDetail=null;
}

async function loadTaskBattles(){
  if(!_currentTaskDetail){ showToast('请先打开考勤详情','var(--red)'); return; }
  const pos = _currentTaskDetail.pos;
  const userList = _currentTaskDetail.user_list||{};
  const names = Object.values(userList).map(u=>u.name).filter(Boolean);
  if(!pos||!names.length){ showToast('无坐标或成员数据','var(--red)'); return; }
  const wrap = document.getElementById('task-battles-wrap');
  const b = document.getElementById('task-battles-body');
  const cnt = document.getElementById('task-battles-count');
  wrap.classList.remove('is-hidden');
  b.innerHTML=`<tr><td colspan=5 style='color:var(--cyan);text-align:center;padding:12px'>⏳ 加载中...</td></tr>`;
  const membersParam = names.join(',');
  const data = await apiFetch(`/api/battles_all?wid=${pos}&size=200&page=1`, {
    method: 'POST',
    headers: {'Content-Type': 'application/json'},
    body: JSON.stringify({members: membersParam})
  });
  if(!data){ b.innerHTML=`<tr><td colspan=5 style='color:var(--red);text-align:center'>请求失败</td></tr>`; return; }
  const rows = data.data||[];
  cnt.textContent = `共${data.total}条`;
  b.innerHTML='';
  if(!rows.length){
    b.innerHTML=`<tr><td colspan=5 style='color:var(--text2);text-align:center;padding:12px'>该城池暂无成员战报</td></tr>`;
    return;
  }
  const RESULT_MAP={0:'平',1:'胜',2:'败',6:'胜'};
  rows.forEach(r=>{
    const timeStr = r.time_str||new Date(r.time*1000).toLocaleTimeString('zh-CN',{hour12:false});
    const res = RESULT_MAP[r.result]||r.result;
    const resColor = (r.result===1||r.result===6)?'var(--green)':r.result===2?'var(--red)':'var(--text2)';
    const garrison = r.garrison===1?`<span style='color:var(--cyan);font-size:.68rem'>拆迁</span>`:`<span style='color:var(--gold);font-size:.68rem'>主力</span>`;
    // 武将名
    const heroes = [r.atk_hero1_id,r.atk_hero2_id,r.atk_hero3_id].filter(Boolean).map(hid=>{
      if(typeof HERO_CFG!=='undefined'&&HERO_CFG[hid]) return HERO_CFG[hid].name||hid;
      return hid;
    }).join(' / ');
    b.innerHTML+=`<tr>
      <td style='font-size:.68rem;color:var(--text2);white-space:nowrap'>${esc(timeStr)}</td>
      <td><b>${esc(r.atk_name||'')}</b><br><span style='font-size:.65rem;color:var(--text2)'>${esc(r.atk_union||'')}</span></td>
      <td>${garrison}</td>
      <td style='color:${resColor};font-weight:600'>${res}</td>
      <td style='font-size:.68rem;color:var(--text2)'>${esc(heroes)}</td>
    </tr>`;
  });
}

let _statsConfirmTid = null;
let _statsConfirmBtn = null;

async function doStatistics(rawTaskId, btn){
  const tid = taskApiId(rawTaskId);
  if(tid === null) return;
  // 获取任务信息展示在确认弹窗里
  const task = await apiFetch(`/api/tasks/${tid}`);
  if(!task) return;
  _statsConfirmTid = tid;
  _statsConfirmBtn = btn;
  const infoEl = document.getElementById('stats-confirm-info');
  const posLabel = task.wid_name ? `${task.wid_name} (${task.pos_xy||task.pos})` : (task.pos_xy||task.pos);
  const groups = (task.target_groups||[]).join('、')||'全员';
  const timeStr = task.time ? new Date(task.time*1000).toLocaleString('zh-CN') : '-';
  infoEl.innerHTML=`
    <div>📋 <b style='color:var(--text)'>${esc(task.name)}</b></div>
    <div>🏰 城池：<span style='color:var(--cyan)'>${esc(posLabel)}</span></div>
    <div>⏰ 时间：${esc(timeStr)}</div>
    <div>👥 目标分组：${esc(groups)}</div>
    <div>🎯 目标人数：<span style='color:var(--gold)'>${task.target_user_num}</span> 人</div>
  `;
  document.getElementById('stats-confirm-modal').style.display='flex';
}

function closeStatsConfirm(){
  document.getElementById('stats-confirm-modal').style.display='none';
  _statsConfirmTid=null; _statsConfirmBtn=null;
}

async function confirmDoStatistics(){
  const tid = taskApiId(_statsConfirmTid);
  if(tid === null){
    closeStatsConfirm();
    return;
  }
  const btn = document.getElementById('stats-confirm-btn');
  btn.disabled=true; btn.textContent='统计中...';
  if(_statsConfirmBtn){ _statsConfirmBtn.disabled=true; _statsConfirmBtn.textContent='统计中...'; }
  const r = await apiFetch(`/api/tasks/${tid}/statistics`,{method:'POST',headers:{'Content-Type':'application/json'},body:'{}'});
  btn.disabled=false; btn.textContent='✅ 确认统计';
  if(_statsConfirmBtn){ _statsConfirmBtn.disabled=false; _statsConfirmBtn.textContent='开始统计'; }
  closeStatsConfirm();
  if(r&&r.ok){ showToast(r.msg,'var(--green)'); loadTasks(); }
  else showToast((r&&r.error)||'统计失败','var(--red)');
}

async function deleteTask(rawTaskId){
  const tid = taskApiId(rawTaskId);
  if(tid === null) return;
  if(!confirm('确认删除该任务？')) return;
  const r = await apiFetch(`/api/tasks/${tid}`,{method:'DELETE'});
  if(r&&r.ok){showToast('删除成功'); loadTasks(); closeTaskDetail();}
  else showToast('删除失败','var(--red)');
}

function showCreateTask(){
  // 设置默认时间为当前时间
  const now = new Date();
  now.setMinutes(now.getMinutes()-now.getTimezoneOffset());
  document.getElementById('ct-time').value=now.toISOString().slice(0,16);
  document.getElementById('ct-name').value='';
  document.getElementById('ct-pos').value='';
  document.getElementById('ct-groups').value='';
  document.getElementById('ct-nearby-list').innerHTML='';
  document.getElementById('ct-nearby-status').textContent='';
  switchCreateMode('group');
  // 加载分组标签
  loadGroupTags();
  document.getElementById('create-task-modal').style.display='flex';
}

function switchCreateMode(mode){
  const isGroup = mode==='group';
  document.getElementById('ct-panel-group').style.display = isGroup?'block':'none';
  document.getElementById('ct-panel-nearby').style.display = isGroup?'none':'block';
  document.getElementById('ct-mode-group').style.background = isGroup?'var(--cyan)':'var(--panel2)';
  document.getElementById('ct-mode-group').style.color = isGroup?'#0a1420':'var(--text2)';
  document.getElementById('ct-mode-nearby').style.background = isGroup?'var(--panel2)':'var(--cyan)';
  document.getElementById('ct-mode-nearby').style.color = isGroup?'var(--text2)':'#0a1420';
}

let _groupTagModels = [];

function bindGroupTagActions(root){
  if(!root?.addEventListener || root._groupTagActionsBound) return;
  root._groupTagActionsBound = true;
  root.addEventListener('click', event=>{
    const button = event.target?.closest?.('[data-group-tag-index]');
    if(!button || (root.contains && !root.contains(button))) return;
    const index = Number(button.dataset.groupTagIndex);
    const model = Number.isSafeInteger(index) ? _groupTagModels[index] : null;
    if(!model) return;
    if(model.selectAll) setGroupTag('');
    else toggleGroupTag(model.value);
  });
}

async function loadGroupTags(){
  const data = await apiFetch('/api/team_groups');
  const el = document.getElementById('ct-group-tags');
  if(!el) return;
  bindGroupTagActions(el);
  _groupTagModels = [];
  if(!Array.isArray(data) || !data.length){
    const empty = document.createElement('span');
    empty.style.fontSize = '.72rem';
    empty.style.color = 'var(--text2)';
    empty.textContent = '暂无分组数据';
    el.replaceChildren(empty);
    return;
  }
  _groupTagModels = [
    {value:'', selectAll:true},
    ...data.map(group=>({value:String(group??''), selectAll:false})),
  ];
  const buttons = _groupTagModels.map((model,index)=>{
    const button = document.createElement('button');
    button.className = 'btn';
    button.type = 'button';
    button.style.fontSize = '.72rem';
    button.style.padding = '2px 8px';
    button.dataset.groupTagIndex = String(index);
    button.textContent = model.selectAll ? '全员' : model.value;
    if(model.selectAll){
      button.style.borderColor = 'var(--green)';
      button.style.color = 'var(--green)';
    }
    return button;
  });
  el.replaceChildren(...buttons);
}

function toggleGroupTag(g){
  const input = document.getElementById('ct-groups');
  const cur = input.value.split(',').map(s=>s.trim()).filter(Boolean);
  const idx = cur.indexOf(g);
  if(idx>=0) cur.splice(idx,1);
  else cur.push(g);
  input.value = cur.join(',');
  // 更新按钮高亮
  document.querySelectorAll('#ct-group-tags button[data-group-tag-index]').forEach(btn=>{
    const index = Number(btn.dataset.groupTagIndex);
    const model = Number.isSafeInteger(index) ? _groupTagModels[index] : null;
    if(!model || model.selectAll) return;
    const sel = cur.includes(model.value);
    btn.style.background = sel?'var(--cyan)':'transparent';
    btn.style.color = sel?'#0a1420':'var(--text)';
  });
}

function setGroupTag(g){
  document.getElementById('ct-groups').value=g;
  document.querySelectorAll('#ct-group-tags button[data-group-tag-index]').forEach(btn=>{
    const index = Number(btn.dataset.groupTagIndex);
    const model = Number.isSafeInteger(index) ? _groupTagModels[index] : null;
    if(!model || model.selectAll) return;
    btn.style.background='transparent'; btn.style.color='var(--text)';
  });
}

let _nearbyPlayers = [];

function onCtPosInput(){
  // 坐标变化时清空预览
  _nearbyPlayers = [];
  document.getElementById('ct-nearby-list').innerHTML='';
  document.getElementById('ct-nearby-status').textContent='';
}

async function previewNearby(){
  const pos = document.getElementById('ct-pos').value.trim();
  const limit = parseInt(document.getElementById('ct-nearby-limit').value)||20;
  const group = document.getElementById('ct-groups').value.trim();
  if(!pos){ showToast('请先填写城池坐标','var(--red)'); return; }
  const statusEl = document.getElementById('ct-nearby-status');
  statusEl.textContent = '查询中...';
  const groupParam = group ? ('&group='+encodeURIComponent(group.split(',')[0].trim())) : '';
  const data = await apiFetch(`/api/tasks/nearby_players?pos=${encodeURIComponent(pos)}&limit=${limit}${groupParam}`);
  if(!data||data.error){ statusEl.textContent='查询失败'; return; }
  _nearbyPlayers = data;
  statusEl.textContent = `共${data.length}人`;
  const el = document.getElementById('ct-nearby-list');
  el.innerHTML='';
  if(!data.length){ el.innerHTML=`<div style='color:var(--text2);font-size:.75rem;padding:8px'>暂无有坐标的成员</div>`; return; }
  // 渲染列表，带复选框
  el.innerHTML=`<div style='display:flex;gap:6px;margin-bottom:6px'>
    <button class='btn' style='font-size:.68rem;padding:1px 6px' onclick='nearbySelectAll(true)'>全选</button>
    <button class='btn' style='font-size:.68rem;padding:1px 6px' onclick='nearbySelectAll(false)'>清空</button>
    <span style='font-size:.68rem;color:var(--text2)'>勾选后创建任务时只统计勾选成员</span>
  </div><table style='width:100%;font-size:.75rem;border-collapse:collapse'><thead><tr style='color:var(--text2)'><th>选</th><th>名字</th><th>分组</th><th>坐标</th><th>距离</th><th>战力</th></tr></thead><tbody>`+
  data.map((p,i)=>`<tr style='border-top:1px solid var(--border)'>
    <td><input type='checkbox' class='nearby-chk' data-uid='${p.uid}' ${i<20?'checked':''}></td>
    <td><b>${esc(p.name)}</b></td>
    <td style='color:var(--text2)'>${esc(p.group||'')}</td>
    <td style='font-family:monospace'>${p.pos_xy}</td>
    <td style='color:var(--cyan)'>${p.dist}</td>
    <td style='color:var(--gold)'>${fmt(p.power||0)}</td>
  </tr>`).join('')+
  `</tbody></table>`;
}

function nearbySelectAll(v){
  document.querySelectorAll('.nearby-chk').forEach(c=>c.checked=v);
}
function closeCreateTask(){
  document.getElementById('create-task-modal').style.display='none';
}

async function createTask(){
  const name   = document.getElementById('ct-name').value.trim();
  const posRaw = document.getElementById('ct-pos').value.trim();
  const timeRaw= document.getElementById('ct-time').value;
  const groupsRaw=document.getElementById('ct-groups').value.trim();
  if(!name||!posRaw){ showToast('请填写任务名和坐标','var(--red)'); return; }
  const groups = groupsRaw ? groupsRaw.split(',').map(s=>s.trim()).filter(Boolean) : [];
  const taskTime = timeRaw ? Math.floor(new Date(timeRaw).getTime()/1000) : 0;
  // 如果有勾选的智能分配成员，提取选中的 uid
  const chks = document.querySelectorAll('.nearby-chk:checked');
  const selectedUids = chks.length > 0 ? [...chks].map(c=>parseInt(c.dataset.uid)) : null;
  const body = {name, pos:posRaw, time:taskTime, groups};
  if(selectedUids && selectedUids.length > 0) body.uids = selectedUids;
  const r = await apiFetch('/api/tasks',{
    method:'POST',
    headers:{'Content-Type':'application/json'},
    body:JSON.stringify(body)
  });
  if(r&&r.ok){ showToast(r.msg,'var(--green)'); closeCreateTask(); loadTasks(); }
  else showToast((r&&r.error)||'创建失败','var(--red)');
}

function exportTaskExcel(){
  if(!_currentTaskDetail){ showToast('请先打开考勤详情','var(--red)'); return; }
  const userList=Object.values(_currentTaskDetail.user_list||{});
  // CSV 含队伍数列（对齐参考项目 Task.vue exportExcel）
  let csv='名字,分组,主力,拆迁,主力次数,拆迁次数,状态\n';
  userList.sort((a,b)=>(b.atk_num+b.dis_num)-(a.atk_num+a.dis_num)).forEach(u=>{
    const status=(u.atk_num>0||u.dis_num>0)?'出战':'缺勤';
    csv+=`${u.name},${u.group||''},${u.atk_team_num||0},${u.dis_team_num||0},${u.atk_num},${u.dis_num},${status}\n`;
  });
  const blob=new Blob(["\uFEFF"+csv],{type:'text/csv;charset=utf-8'});
  const a=document.createElement('a');
  a.href=URL.createObjectURL(blob);
  a.download=`${_currentTaskDetail.name}_考勤表.csv`;
  a.click();
  showToast('已导出考勤表');
}

// ===== 同盟成员队伍一览 =====
let _agtGroupsLoaded = false;
let _agtCurrentRows = [];
let _agtExpandedPlayers = new Set();
let _agtActionModels = [];

function expandAllAlliancePlayerTeams(){
  _agtExpandedPlayers = new Set((_agtCurrentRows||[]).map(r=>`${r._group_name||'未分组'}__${r.player_name||''}`).filter(Boolean));
  renderAllianceGroupTeams(_agtCurrentRows);
}

function collapseAllAlliancePlayerTeams(){
  _agtExpandedPlayers = new Set();
  renderAllianceGroupTeams(_agtCurrentRows);
}

function escapeOrganizationAttribute(value){
  return String(value??'')
    .replace(/&/g,'&amp;')
    .replace(/</g,'&lt;')
    .replace(/>/g,'&gt;')
    .replace(/"/g,'&quot;')
    .replace(/'/g,'&#39;');
}

function replaceOrganizationOptions(select, values, allLabel='全部'){
  if(!select) return;
  const allOption = document.createElement('option');
  allOption.value = '';
  allOption.textContent = allLabel;
  const options = (values||[]).map(value=>{
    const option = document.createElement('option');
    option.value = String(value??'');
    option.textContent = String(value??'');
    return option;
  });
  select.replaceChildren(allOption, ...options);
}

async function ensureAllianceGroupOptions(request=null){
  const sel = document.getElementById('agt-group');
  if(!sel) return;
  if(_agtGroupsLoaded) return;
  const groups = await apiFetch('/api/team_groups');
  if(request && !isOrganizationRequestCurrent(request)) return;
  replaceOrganizationOptions(sel, groups, '全部分组');
  _agtGroupsLoaded = true;
}

function toggleAlliancePlayerTeams(playerKey){
  if(_agtExpandedPlayers.has(playerKey)) _agtExpandedPlayers.delete(playerKey);
  else _agtExpandedPlayers.add(playerKey);
  renderAllianceGroupTeams(_agtCurrentRows);
}

function buildAllianceHeroMiniCard(hid){
  let name=hid;
  let hcfg={};
  if(typeof HERO_CFG!=='undefined'&&HERO_CFG[hid]){
    hcfg=HERO_CFG[hid]||{};
    name=hcfg.name||hid;
  }
  const iconId=Number(hcfg.iconId)||0;
  const country=hcfg.country||'';
  const countryColor={'魏':'var(--blue)','蜀':'var(--green)','吴':'var(--red)','汉':'var(--gold)','晋':'var(--purple)','群':'var(--text2)'}[country]||'var(--text2)';
  const imgUrl=iconId>0?`https://g0.gph.netease.com/ngsocial/community/stzb/cn/cards/cut/card_medium_${encodeURIComponent(String(Math.trunc(iconId)))}.jpg?gameid=g10`:'';
  return `<span class='organization-hero-mini' title='${escapeOrganizationAttribute(name)}' style='border-color:${countryColor}'>
    <span style='position:relative;width:26px;height:26px;border-radius:4px;overflow:hidden;border:1px solid ${countryColor};background:#0d1520'>
      ${imgUrl?`<img data-organization-image src='${escapeOrganizationAttribute(imgUrl)}' style='width:26px;height:26px;object-fit:cover;object-position:left top'>`:''}
    </span>
    <strong>${esc(name)}</strong>
  </span>`;
}

function getTeamHeroNames(row){
  return (row.heroes_str||'').split('+').filter(id=>Number(id)>0).map(hid=>{
    if(typeof HERO_CFG!=='undefined'&&HERO_CFG[hid]) return HERO_CFG[hid].name||String(hid);
    return String(hid);
  });
}

function getTeamHeroNameKey(row){
  return getTeamHeroNames(row).join('+');
}

function dedupeBattleTeamsByHeroNames(rows, scopeBuilder){
  const scopedBuckets = new Map();
  (rows||[]).forEach(row=>{
    const heroNames = getTeamHeroNames(row);
    const heroNameSet = new Set(heroNames);
    const scopeKey = typeof scopeBuilder==='function' ? scopeBuilder(row) : '';
    if(!scopedBuckets.has(scopeKey)) scopedBuckets.set(scopeKey, []);
    const buckets = scopedBuckets.get(scopeKey);

    const matchedIdxs = [];
    buckets.forEach((bucket, idx)=>{
      const hasOverlap = heroNames.some(name=>bucket.heroSet.has(name));
      if(hasOverlap) matchedIdxs.push(idx);
    });

    if(!matchedIdxs.length){
      buckets.push({ row:{...row, _dedupe_source_cnt: Number(row.cnt||0)}, heroSet: heroNameSet });
      return;
    }

    const baseBucket = buckets[matchedIdxs[0]];
    const kept = baseBucket.row;
    kept.cnt = Number(kept.cnt||0) + Number(row.cnt||0);
    kept.wins = Number(kept.wins||0) + Number(row.wins||0);
    kept.draws = Number(kept.draws||0) + Number(row.draws||0);
    kept.win_rate = kept.cnt ? Math.round((Number(kept.wins||0) + Number(kept.draws||0) * 0.5) / Number(kept.cnt||0) * 1000) / 10 : 0;
    if(Number(row.max_troops||0) > Number(kept.max_troops||0)) kept.max_troops = row.max_troops;
    if(Number(row.cnt||0) > Number(kept._dedupe_source_cnt||0)){
      kept.heroes_str = row.heroes_str;
      kept.skills = row.skills;
      kept.hero_stars = row.hero_stars;
      kept.hero_levels = row.hero_levels;
      kept._dedupe_source_cnt = Number(row.cnt||0);
    }
    heroNames.forEach(name=>baseBucket.heroSet.add(name));

    for(let i=matchedIdxs.length-1;i>=1;i--){
      const mergeIdx = matchedIdxs[i];
      const mergeBucket = buckets[mergeIdx];
      kept.cnt = Number(kept.cnt||0) + Number(mergeBucket.row.cnt||0);
      kept.wins = Number(kept.wins||0) + Number(mergeBucket.row.wins||0);
      kept.draws = Number(kept.draws||0) + Number(mergeBucket.row.draws||0);
      kept.win_rate = kept.cnt ? Math.round((Number(kept.wins||0) + Number(kept.draws||0) * 0.5) / Number(kept.cnt||0) * 1000) / 10 : 0;
      if(Number(mergeBucket.row.max_troops||0) > Number(kept.max_troops||0)) kept.max_troops = mergeBucket.row.max_troops;
      if(Number(mergeBucket.row._dedupe_source_cnt||0) > Number(kept._dedupe_source_cnt||0)){
        kept.heroes_str = mergeBucket.row.heroes_str;
        kept.skills = mergeBucket.row.skills;
        kept.hero_stars = mergeBucket.row.hero_stars;
        kept.hero_levels = mergeBucket.row.hero_levels;
        kept._dedupe_source_cnt = Number(mergeBucket.row._dedupe_source_cnt||0);
      }
      mergeBucket.heroSet.forEach(name=>baseBucket.heroSet.add(name));
      buckets.splice(mergeIdx, 1);
    }
  });

  return Array.from(scopedBuckets.values()).flatMap(buckets=>buckets.map(bucket=>{
    const row = bucket.row;
    delete row._dedupe_source_cnt;
    return row;
  }));
}

function renderAllianceGroupTeams(rows){
  _agtCurrentRows = rows || [];
  const b=document.getElementById('agt-body');
  const countEl=document.getElementById('agt-count');
  if(!b || !countEl) return;
  b.innerHTML='';
  if(!_agtCurrentRows.length){
    countEl.textContent='0条';
    renderOrganizationTableState(b, {
      kind:'empty',
      message:'暂无符合条件的同盟成员队伍',
      replace:true,
    });
    return;
  }

  const validKeys = new Set(_agtCurrentRows.map(r=>`${r._group_name||'未分组'}__${r.player_name||''}`));
  _agtExpandedPlayers = new Set([..._agtExpandedPlayers].filter(k=>validKeys.has(k)));
  const actionHtml = validKeys.size ? `<span style='margin-left:10px;display:inline-flex;gap:6px'><button class='btn' type='button' data-organization-action='expand-alliance' style='font-size:.68rem;padding:2px 8px'>全部展开</button><button class='btn' type='button' data-organization-action='collapse-alliance' style='font-size:.68rem;padding:2px 8px'>全部收起</button></span>` : '';
  countEl.innerHTML = `共${_agtCurrentRows.length}条 · ${validKeys.size}名成员${actionHtml}`;
  bindOrganizationActions(countEl);

  const grouped = [];
  let currentGroup = null;
  let currentPlayerKey = null;
  _agtCurrentRows.forEach(r=>{
    const groupName = r._group_name || '未分组';
    const playerKey = `${groupName}__${r.player_name||''}`;
    if(groupName !== currentGroup){
      grouped.push({
        type:'group',
        groupName,
        memberCount: r._group_member_count||0,
        teamCount: r._group_team_count||0,
        totalBattles: r._group_total_battles||0,
        isStale: Boolean(r.isStale),
      });
      currentGroup = groupName;
      currentPlayerKey = null;
    }
    if(playerKey !== currentPlayerKey){
      grouped.push({
        type:'player',
        key: playerKey,
        groupName,
        playerName: r.player_name||'',
        unionName: r.union||'',
        teamCount: r._player_team_count||0,
        totalBattles: r._player_total_battles||0,
        totalWins: r._player_total_wins||0,
        totalDraws: r._player_total_draws||0,
        winRate: r._player_win_rate||0,
        mainTeamText: r._player_main_team_text||'—',
        mainTeamCount: r._player_main_team_count||0,
        mainTeamHeroes: r._player_main_team_heroes||[],
        isStale: Boolean(r.isStale),
      });
      currentPlayerKey = playerKey;
    }
    grouped.push({type:'team', ...r, _playerKey: playerKey});
  });

  let rank = 0;
  const rowStates = [];
  const actionModels = [];
  const activityMaximum = Math.max(
    1,
    ...grouped.map(item=>Number(item.totalBattles)||0),
  );
  const html = grouped.map(item=>{
    if(item.type==='group'){
      rowStates.push({
        selected:false,
        isStale:Boolean(item.isStale),
      });
      return `<tr class='organization-row' data-selected='false' data-state='${item.isStale?'stale':'current'}'><td colspan='8' style='padding:10px 12px;color:var(--gold);font-weight:700;letter-spacing:.08em;border-top:1px solid var(--border)'>
        <div style='display:flex;align-items:center;justify-content:space-between;gap:10px;flex-wrap:wrap'>
          ${organizationGroupChip(item.groupName)}
          <span style='display:flex;gap:6px;flex-wrap:wrap'>
            <span class='hud-status-chip'>人数 ${item.memberCount}</span>
            <span class='hud-status-chip'>队伍 ${item.teamCount}</span>
            ${organizationActivityMarkup(item.totalBattles,activityMaximum,'总战数')}
          </span>
        </div>
      </td></tr>`;
    }
    if(item.type==='player'){
      const expanded = _agtExpandedPlayers.has(item.key);
      const arrow = expanded ? '▾' : '▸';
      const wrColor = item.winRate>=60?'var(--green)':item.winRate>=40?'var(--gold)':'var(--red)';
      const loseCount = Math.max(0, item.totalBattles - item.totalWins - item.totalDraws);
      const mainTeamAvatarHtml = (item.mainTeamHeroes||[]).slice(0,3).map(buildAllianceHeroMiniCard).join('');
      const actionIndex = actionModels.push({key:item.key}) - 1;
      rowStates.push({
        selected:expanded,
        isStale:Boolean(item.isStale),
      });
      return `<tr class='organization-row' data-selected='${expanded?'true':'false'}' data-state='${item.isStale?'stale':'current'}' data-organization-action='toggle-alliance' data-organization-index='${actionIndex}' style='cursor:pointer'>
        <td style='color:var(--gold);font-family:Share Tech Mono,monospace'>${arrow}</td>
        <td>${organizationIdentityMarkup(item.playerName, `${expanded?'点击收起':'点击展开'} · ${item.teamCount}支队伍`)}</td>
        <td>
          <div class='organization-lineup'>
            ${item.unionName?organizationGroupChip(item.unionName):''}
            ${organizationGroupChip(item.groupName)}
          </div>
        </td>
        <td>
          <div style='color:var(--text2);font-size:.72rem'>总队伍：${item.teamCount}</div>
          <div style='display:flex;align-items:flex-start;gap:8px;margin-top:5px'>
            ${organizationLineupCard(mainTeamAvatarHtml)}
            <div style='min-width:0'>
              <div style='color:var(--gold);font-size:.7rem'>常用主力队</div>
              <div style='color:var(--text2);font-size:.66rem;line-height:1.35;margin-top:2px'>${esc(item.mainTeamText)}<span style='color:var(--text2)'> × ${item.mainTeamCount}</span></div>
            </div>
          </div>
        </td>
        <td>${organizationActivityMarkup(item.totalBattles,activityMaximum,'战数')}</td>
        <td style='color:var(--green)'>${item.totalWins}</td>
        <td style='color:var(--text2)'>${item.totalDraws}</td>
        <td style='color:${wrColor}'>${item.winRate}%<span style='color:var(--text2);font-size:.72rem'>（${item.totalWins}胜 / ${item.totalDraws}平 / ${loseCount}负）</span></td>
      </tr>`;
    }
    if(!_agtExpandedPlayers.has(item._playerKey)) return '';
    rank += 1;
    const wrStyle=item.win_rate>=60?'color:var(--green)':item.win_rate>=40?'color:var(--gold)':'color:var(--red)';
    const loses=(item.cnt||0)-(item.wins||0)-(item.draws||0);
    const heroIds=(item.heroes_str||'').split('+').filter(Boolean);
    const heroStars=item.hero_stars||[0,0,0];
    const skillIds=(item.skills||'').split(',').filter(Boolean);
    const heroLevels=(item.hero_levels||'').split(',').map(l=>Number(l)||0);
    const heroHtml=heroIds.map((hid,hi)=>{
      let name=hid;
      if(typeof HERO_CFG!=='undefined'&&HERO_CFG[hid])name=HERO_CFG[hid].name||hid;
      const hcfg=(typeof HERO_CFG!=='undefined'&&HERO_CFG[hid])||{};
      const country=hcfg.country||'';
      const countryColor={'魏':'var(--blue)','蜀':'var(--green)','吴':'var(--red)','汉':'var(--gold)','晋':'var(--purple)','群':'var(--text2)'}[country]||'var(--text2)';
      const s=heroStars[hi]||0;
      const lv=heroLevels[hi]||0;
      const starColor=s>=12?'var(--red)':s>=6?'var(--gold)':s>0?'var(--cyan)':'var(--text2)';
      const starStr=s>0?`<span style='color:${starColor};font-size:.58rem;margin-left:2px'>★${s}</span>`:`<span style='color:var(--text2);font-size:.58rem;margin-left:2px'>★0</span>`;
      const lvStr=lv>0?`<span style='color:var(--gold2);font-size:.58rem;margin-left:4px;opacity:.85'>Lv${lv}</span>`:'';
      const heroSkills=skillIds.slice(hi*3,hi*3+3);
      const skillsHtml=heroSkills.map(sid=>{
        let sname=sid;
        if(typeof SKILL_CFG!=='undefined'&&SKILL_CFG[sid])sname=SKILL_CFG[sid].name||sid;
        return `<span style='font-size:.58rem;color:var(--cyan);background:#0d1820;border-radius:2px;padding:0 3px;margin:1px 1px 0 0;white-space:nowrap'>${esc(sname)}</span>`;
      }).join('');
      return `<span class='organization-hero-mini' style='border-color:${countryColor}'><strong style='color:${countryColor}'>${esc(name)}${starStr}${lvStr}</strong><span>${skillsHtml}</span></span>`;
    }).join('');
    rowStates.push({
      selected:false,
      isStale:Boolean(item.isStale),
    });
    return `<tr class='organization-row' data-selected='false' data-state='${item.isStale?'stale':'current'}'>
      <td style='color:var(--text2);font-size:.72rem;padding-left:22px'>${rank}</td>
      <td style='color:var(--text2);font-size:.72rem'>└ 队伍</td>
      <td><span class='hud-status-chip'>明细</span></td>
      <td style='max-width:480px'>${organizationLineupCard(heroHtml)}</td>
      <td style='font-family:Share Tech Mono,monospace'>${item.cnt}</td>
      <td style='color:var(--green)'>${item.wins}</td>
      <td style='color:var(--text2)'>${item.draws||0}</td>
      <td style='${wrStyle}'>${item.win_rate}%<span style='color:var(--text2);font-size:.72rem'>（${item.wins||0}胜 / ${item.draws||0}平 / ${loses>0?loses:0}负）</span></td>
    </tr>`;
  }).join('');
  b.innerHTML = html;
  _agtActionModels = actionModels;
  bindOrganizationActions(b, _agtActionModels);
  syncOrganizationRows(b, rowStates);
}

async function loadAllianceGroupTeams(){
  const player=document.getElementById('agt-player').value;
  const side=document.getElementById('agt-side').value;
  const group=document.getElementById('agt-group').value;
  const b=document.getElementById('agt-body');
  const countEl=document.getElementById('agt-count');
  const panel=b?.closest('.organization-table-panel');
  const request=beginOrganizationRequest('alliance-teams',panel);
  let statusHost=null;
  try{
  statusHost=ensureOrganizationStatusHost(panel);
  await ensureAllianceGroupOptions(request);
  if(!isOrganizationRequestCurrent(request)) return;
  const hint=document.getElementById('agt-hint');
  if(hint) hint.textContent='';
  const teamUsers = await apiFetch('/api/team_users');
  if(!isOrganizationRequestCurrent(request)) return;
  if(!teamUsers){
    throw new Error('成员数据加载失败');
  }
  if(teamUsers.error) throw new Error(String(teamUsers.error));
  if(!Array.isArray(teamUsers)) throw new Error('成员数据格式异常');
  const memberMap = new Map();
  (teamUsers||[]).forEach(u=>{
    if(u && u.name) memberMap.set(String(u.name).trim(), u);
  });
  const unionName = (()=>{
    const typed = (document.getElementById('pbt-union')?.value||'').trim();
    if(typed) return typed;
    const profileUnion = (window.__profileUnionName||'').trim();
    if(profileUnion) return profileUnion;
    return '';
  })();
  const url=`/api/player_battle_teams?player=${encodeURIComponent(player)}&union=${encodeURIComponent(unionName)}&side=${encodeURIComponent(side)}&_t=${Date.now()}`;
  const data=await apiFetch(url);
  if(!isOrganizationRequestCurrent(request)) return;
  if(!data){
    throw new Error('同盟成员队伍请求失败');
  }
  if(data.error) throw new Error(String(data.error));
  if(!Array.isArray(data)) throw new Error('同盟成员队伍数据格式异常');
  clearOrganizationStatusHost(statusHost);
  const filtered = dedupeBattleTeamsByHeroNames((data||[]).filter(r=>{
    const member = memberMap.get(String(r.player_name||'').trim());
    if(!member) return false;
    const heroCount = (r.heroes_str||'').split('+').filter(id=>Number(id)>0).length;
    if(heroCount < 3) return false;
    const skillIds = (r.skills||'').split(',').filter(id=>Number(id)>0);
    if(skillIds.length < 9) return false;
    const everyHeroHasThreeSkills = [0,1,2].every(idx=>skillIds.slice(idx*3, idx*3+3).length === 3);
    if(!everyHeroHasThreeSkills) return false;
    const troops = Number(r.max_troops)||0;
    // 有兵力值时才做 10000 过滤；为 0 说明该条没采到兵力，先保留，避免整页被误杀
    if(troops > 0 && troops < 10000) return false;
    if(group && (member.group_name||'') !== group) return false;
    return true;
  }).map(r=>{
    const member = memberMap.get(String(r.player_name||'').trim()) || {};
    return {...r, _group_name: member.group_name||'未分组'};
  }), r=>`${r._group_name||'未分组'}__${r.player_name||''}`);
  const playerStats = new Map();
  filtered.forEach(r=>{
    const groupName = r._group_name || '未分组';
    const playerName = r.player_name || '';
    const key = `${groupName}__${playerName}`;
    if(!playerStats.has(key)){
      playerStats.set(key, {
        teamCount: 0,
        totalBattles: 0,
        totalWins: 0,
        totalDraws: 0,
        mainTeamText: '—',
        mainTeamCount: 0,
        mainTeamHeroes: [],
      });
    }
    const s = playerStats.get(key);
    s.teamCount += 1;
    s.totalBattles += (r.cnt || 0);
    s.totalWins += (r.wins || 0);
    s.totalDraws += (r.draws || 0);
    if((r.cnt || 0) > s.mainTeamCount){
      s.mainTeamCount = r.cnt || 0;
      const heroIds = (r.heroes_str||'').split('+').filter(id=>Number(id)>0);
      const heroNames = heroIds.map(hid=>{
        if(typeof HERO_CFG!=='undefined'&&HERO_CFG[hid]) return HERO_CFG[hid].name||hid;
        return hid;
      });
      s.mainTeamHeroes = heroIds;
      s.mainTeamText = heroNames.join(' / ') || '—';
    }
  });

  filtered.sort((a,b)=>{
    const ga=(a._group_name||'未分组');
    const gb=(b._group_name||'未分组');
    const gcmp=ga.localeCompare(gb,'zh-CN');
    if(gcmp!==0) return gcmp;

    const aKey = `${ga}__${a.player_name||''}`;
    const bKey = `${gb}__${b.player_name||''}`;
    const ba = Number(playerStats.get(aKey)?.totalBattles||0);
    const bb = Number(playerStats.get(bKey)?.totalBattles||0);
    if(bb !== ba) return bb - ba;

    const pcmp=String(a.player_name||'').localeCompare(String(b.player_name||''),'zh-CN');
    if(pcmp!==0) return pcmp;
    return (Number(b.cnt)||0)-(Number(a.cnt)||0);
  });

  const groupStats = new Map();
  filtered.forEach(r=>{
    const groupName = r._group_name || '未分组';
    if(!groupStats.has(groupName)){
      groupStats.set(groupName, { memberSet:new Set(), teamCount:0, totalBattles:0 });
    }
    const g = groupStats.get(groupName);
    g.memberSet.add(r.player_name || '');
    g.teamCount += 1;
    g.totalBattles += (r.cnt || 0);
  });

  filtered.forEach(r=>{
    const key = `${r._group_name||'未分组'}__${r.player_name||''}`;
    const s = playerStats.get(key);
    const g = groupStats.get(r._group_name||'未分组');
    r._player_team_count = s?.teamCount || 0;
    r._player_total_battles = s?.totalBattles || 0;
    r._player_total_wins = s?.totalWins || 0;
    r._player_total_draws = s?.totalDraws || 0;
    r._player_win_rate = (s && s.totalBattles) ? Math.round((s.totalWins + s.totalDraws * 0.5) / s.totalBattles * 1000) / 10 : 0;
    r._player_main_team_text = s?.mainTeamText || '—';
    r._player_main_team_count = s?.mainTeamCount || 0;
    r._player_main_team_heroes = s?.mainTeamHeroes || [];
    r._group_member_count = g?.memberSet?.size || 0;
    r._group_team_count = g?.teamCount || 0;
    r._group_total_battles = g?.totalBattles || 0;
  });

  _agtExpandedPlayers = new Set();
  if(!filtered.length){
    _agtCurrentRows = [];
    countEl.textContent='0条';
    renderOrganizationTableState(b, {
      kind:'empty',
      message:'暂无符合条件的同盟成员队伍',
      replace:true,
    });
    return;
  }
  renderAllianceGroupTeams(filtered);
  }catch(error){
    if(isOrganizationRequestCurrent(request)){
      renderOrganizationLoadError(b, statusHost, {
        kind:'error',
        message:error?.message || '同盟成员队伍加载失败',
        replace:true,
      });
    }
  }finally{
    finishOrganizationRequest(request);
  }
}

// ===== 攻城战场态势 (Tab 17) =====
async function loadBattleField(){
  const [bfData, bqData] = await Promise.all([
    apiFetch('/api/battle_field'),
    apiFetch('/api/battle_queue'),
  ]);
  const bf = bfData || [];
  const bq = bqData || [];

  // ===== 统计卡片 =====
  const cards = document.getElementById('bf-cards');
  if(cards){
    const totalCity = bf.length;
    const totalNearby = bf.reduce((s,r)=>s+(r.nearby_count||0),0);
    const totalQueue = bq.length;
    const totalPower = bq.reduce((s,r)=>s+(r.power||0),0);
    const cityIds = [...new Set(bq.map(r=>r.city_id).filter(Boolean))];
    cards.innerHTML=`
      <div class='stat-card'><div class='val' style='color:var(--red)'>${totalCity}</div><div class='lbl'>战场城池</div></div>
      <div class='stat-card'><div class='val' style='color:var(--gold)'>${totalNearby}</div><div class='lbl'>附近成员次</div></div>
      <div class='stat-card'><div class='val' style='color:var(--cyan)'>${totalQueue}</div><div class='lbl'>出征队列数</div></div>
      <div class='stat-card'><div class='val' style='color:var(--purple)'>${cityIds.length}</div><div class='lbl'>攻打城池数</div></div>
      <div class='stat-card'><div class='val' style='color:var(--green)'>${fmt(totalPower)}</div><div class='lbl'>队列总战力</div></div>
    `;
  }

  // ===== 子标签切换 =====
  const wrap = document.getElementById('bf-subtabs');
  if(wrap && !wrap.dataset.init){
    wrap.dataset.init='1';
    wrap.querySelectorAll('button').forEach(btn=>{
      btn.onclick=()=>{
        wrap.querySelectorAll('button').forEach(b=>b.classList.remove('active'));
        btn.classList.add('active');
        document.getElementById('bf-panel-field').style.display = btn.dataset.t==='field'?'':'none';
        document.getElementById('bf-panel-queue').style.display = btn.dataset.t==='queue'?'':'none';
      };
    });
  }

  // ===== 战场态势表 (000018aa) =====
  const b = document.getElementById('bf-body'); b.innerHTML='';
  if(!bf.length){
    b.innerHTML=`<tr><td colspan=7 style='color:var(--text2);text-align:center;padding:20px'>暂无战场数据（需捕获 000018aa）</td></tr>`;
  } else {
    const CTYPE=STZB_META.cityTypes;
    bf.forEach(r=>{
      const coord=(r.x&&r.y)?`(${r.x},${r.y})`:'-';
      const cityName=r.city_name?`<b style='color:var(--gold)'>${esc(r.city_name)}</b>`:`<span style='color:var(--text2)'>${CTYPE[r.cell_type]||'wid:'+r.wid}</span>`;
      const atkName=r.attacker_name?`<b style='color:var(--red)'>${esc(r.attacker_name)}</b><br><span style='font-size:.65rem;color:var(--text2)'>${esc(r.attacker_group||'')}</span>`:`<span style='color:var(--text2)'>uid:${r.attacker_uid}</span>`;
      const nearbyHtml=(r.nearby_members||[]).slice(0,8).map(m=>`<span class='badge' style='background:#1a2535;color:var(--cyan);font-size:.62rem;margin:1px'>${esc(m.name)}</span>`).join('');
      const more=(r.nearby_count||0)>8?`<span style='color:var(--text2);font-size:.65rem'>+${r.nearby_count-8}人</span>`:'';
      b.innerHTML+=`<tr>
        <td style='font-family:Share Tech Mono,monospace;font-size:.68rem;color:var(--text2)'>${r.wid}</td>
        <td style='font-family:Share Tech Mono,monospace;font-size:.72rem'>${coord}</td>
        <td>${cityName}</td>
        <td>${atkName}</td>
        <td style='color:var(--${(r.nearby_count||0)>10?"red":(r.nearby_count||0)>5?"gold":"text2"})'>${r.nearby_count||0}</td>
        <td style='max-width:260px;word-break:break-word'>${nearbyHtml}${more}</td>
        <td style='color:var(--text2);font-size:.68rem'>${r.captured_at?r.captured_at.slice(5,16):'-'}</td>
      </tr>`;
    });
  }

  // ===== 队列快照表 (000018ae) =====
  const bqb = document.getElementById('bq-body'); if(!bqb) return;
  bqb.innerHTML='';
  if(!bq.length){
    bqb.innerHTML=`<tr><td colspan=8 style='color:var(--text2);text-align:center;padding:20px'>暂无队列数据（需捕获 000018ae）</td></tr>`;
  } else {
    const FLAG={1:'普通',3:'主力',29:'副盟主'};
    // 按玩家分组，统计每人出了几队
    const byUid={};
    bq.forEach(r=>{ if(!byUid[r.uid]) byUid[r.uid]={name:r.name||r.member_name,slots:[],power:r.power,flag:r.flag,city_id:r.city_id,group_name:r.group_name}; byUid[r.uid].slots.push(r.queue_slot); });
    Object.values(byUid).sort((a,b)=>(b.power||0)-(a.power||0)).forEach(p=>{
      const flagColor={1:'var(--text2)',3:'var(--gold)',29:'var(--red)'}[p.flag]||'var(--text2)';
      const heroName = window.HEROCFG&&window.HEROCFG[bq.find(r=>r.uid===p.uid)&&bq.find(r=>r.uid===p.uid).cur_hero_id] ? window.HEROCFG[bq.find(r=>r.uid===p.uid).cur_hero_id].name : '';
      bqb.innerHTML+=`<tr>
        <td style='color:var(--gold2);font-weight:600'>${esc(p.name||'')}</td>
        <td><span class='badge' style='color:${flagColor}'>${FLAG[p.flag]||p.flag}</span></td>
        <td><span class='badge' style='color:var(--cyan)'>${esc(p.group_name||'-')}</span></td>
        <td style='color:var(--red);font-weight:600'>${p.slots.length}队</td>
        <td style='font-family:Share Tech Mono,monospace;font-size:.72rem'>${fmt(p.power||0)}</td>
        <td style='color:var(--text2);font-size:.72rem'>${p.slots.join(',')}</td>
        <td style='color:var(--purple);font-size:.72rem'>${heroName}</td>
        <td style='color:var(--text2);font-size:.68rem'>城${p.city_id||0}</td>
      </tr>`;
    });
  }
}

// ===== 联盟过滤候选 =====
function fillUnionFilterOptions(){
  const dl = document.getElementById('union-filter-list');
  if(!dl) return;
  const seen = new Set();
  const names = [];
  const rows = Array.isArray(_ulData) ? _ulData : (Array.isArray(_ulData?.data) ? _ulData.data : []);
  rows.forEach(r=>{
    const name = (r && r.name ? String(r.name) : '').trim();
    if(name && !seen.has(name)){
      seen.add(name);
      names.push(name);
    }
  });
  names.sort((a,b)=>a.localeCompare(b,'zh-CN'));
  dl.innerHTML = names.map(name=>`<option value="${esc(name)}"></option>`).join('');
}

async function loadUnionList(){
  const data = await apiFetch('/api/union_list');
  _ulData = Array.isArray(data) ? data : (Array.isArray(data?.data) ? data.data : []);
  fillUnionFilterOptions();
  const cards = document.getElementById('ul-cards');
  if(cards){
    const totalPower = _ulData.reduce((s,r)=>s+(r.power||0),0);
    const totalMember = _ulData.reduce((s,r)=>s+(r.total_member||0),0);
    cards.innerHTML=`
      <div class='stat-card'><div class='val'>${_ulData.length}</div><div class='lbl'>联盟数</div></div>
      <div class='stat-card'><div class='val' style='color:var(--gold)'>${fmt(totalPower)}</div><div class='lbl'>联盟总势力</div></div>
      <div class='stat-card'><div class='val' style='color:var(--cyan)'>${totalMember}</div><div class='lbl'>联盟总人数</div></div>
    `;
  }
  const bars = document.getElementById('ul-bars');
  if(bars){
    bars.innerHTML='';
    const top = _ulData.slice(0,15);
    const maxV = Math.max(1,...top.map(r=>Number(r.power||0)));
    top.forEach(r=>{
      const pct = Math.round(Number(r.power||0) / maxV * 100);
      bars.innerHTML += `<div class='bar-row'>
        <div class='bar-label'>${esc(r.name||'')}</div>
        <div class='bar-track'><div class='bar-fill' style='width:${pct}%;background:var(--cyan)'></div></div>
        <div class='bar-val'>${fmt(r.power||0)}</div>
      </div>`;
    });
  }
  const b = document.getElementById('ul-body');
  if(!b) return;
  b.innerHTML='';
  _ulData.forEach((r,i)=>{
    const cls = i===0?'rank-1':i===1?'rank-2':i===2?'rank-3':'';
    const updateTime = r.updated_at ? r.updated_at.slice(5,16) : '';
    b.innerHTML+=`<tr>
      <td class='${cls}' style='font-family:Share Tech Mono,monospace'>${r.rank||i+1}</td>
      <td><b>${esc(r.name||'')}</b></td>
      <td style='color:var(--text2)'>${r.level}</td>
      <td class='${cls}' style='font-family:Share Tech Mono,monospace'>${fmt(r.power)}</td>
      <td>${r.total_member}</td>
      <td style='color:var(--gold);font-family:Share Tech Mono,monospace'>${r.occupy_city_value||0}</td>
      <td style='color:var(--blue)'>${r.total_npc_city||0}</td>
      <td style='color:var(--text2);font-size:.72rem'>${regionName(r.region||'')}</td>
      <td style='color:var(--text2);font-size:.68rem'>${updateTime}</td>
    </tr>`;
  });
}

// ===== 游戏公告 (Tab 19) =====
async function loadUnionPowerRank(){
  const data = await apiFetch('/api/union_power_rank');
  if(!data) return;
  const rows = data.rows || [];
  const s = data.summary || {};

  const countEl = document.getElementById('upr-count');
  if(countEl) countEl.textContent = `共${rows.length}条`;

  const cards = document.getElementById('upr-cards');
  if(cards){
    cards.innerHTML = `
      <div class='stat-card'><div class='val'>${s.total_players||0}</div><div class='lbl'>玩家数量</div></div>
      <div class='stat-card'><div class='val' style='color:var(--gold)'>${fmt(s.total_power||0)}</div><div class='lbl'>总势力值</div></div>
      <div class='stat-card'><div class='val' style='color:var(--cyan)'>${fmt(s.total_land||0)}</div><div class='lbl'>总领地</div></div>
      <div class='stat-card'><div class='val' style='color:var(--purple)'>${fmt(s.total_fort||0)}</div><div class='lbl'>总要塞</div></div>
      <div class='stat-card'><div class='val' style='color:var(--red)'>${fmt(s.total_branch_city||0)}</div><div class='lbl'>总分城</div></div>
    `;
  }

  const body = document.getElementById('upr-body');
  if(body){
    body.innerHTML = '';
    rows.forEach((r,i)=>{
      const cls = i===0?'rank-1':i===1?'rank-2':i===2?'rank-3':'';
      const timeText = r.updated_at ? String(r.updated_at).slice(5,16) : '';
      body.innerHTML += `<tr>
        <td class='${cls}' style='font-family:Share Tech Mono,monospace'>${r.rank||i+1}</td>
        <td><b>${esc(r.name||'')}</b></td>
        <td class='${cls}' style='font-family:Share Tech Mono,monospace;color:var(--gold)'>${fmt(r.power||0)}</td>
        <td>${fmt(r.land_count||0)}</td>
        <td style='color:var(--purple)'>${fmt(r.fort_count||0)}</td>
        <td style='color:var(--red)'>${fmt(r.branch_city_count||0)}</td>
        <td style='color:var(--text2);font-size:.72rem'>${esc(regionName(r.region||''))}</td>
        <td style='color:var(--text2);font-size:.68rem'>${timeText}</td>
      </tr>`;
    });
  }

  const bars = document.getElementById('upr-bars');
  if(bars){
    bars.innerHTML = '';
    const top = rows.slice(0,15);
    const maxV = Math.max(1,...top.map(r=>Number(r.power||0)));
    top.forEach(r=>{
      const pct = Math.round(Number(r.power||0) / maxV * 100);
      bars.innerHTML += `<div class='bar-row'>
        <div class='bar-label'>${esc(r.name||'')}</div>
        <div class='bar-track'><div class='bar-fill' style='width:${pct}%;background:var(--gold)'></div></div>
        <div class='bar-val'>${fmt(r.power||0)}</div>
      </div>`;
    });
  }
}

async function loadAnnouncements(){
  const data = await apiFetch('/api/announcements');
  const el = document.getElementById('ann-list');
  if(!el) return;
  el.innerHTML='';
  if(!data||!data.length){
    el.innerHTML=`<div style='color:var(--text2);text-align:center;padding:30px'>暂无公告数据，等待 0000030c 包捕获</div>`;
    return;
  }
  data.forEach(r=>{
    const typeColor = r.ann_type===1?'var(--gold)':r.ann_type===2?'var(--red)':'var(--blue)';
    const typeLabel = r.ann_type===1?'活动':r.ann_type===2?'紧急':'公告';
    const content = (r.content||'').replace(/\n/g,'<br>').replace(/@([^@]+)@/g,'<b style="color:var(--gold)">$1</b>');
    el.innerHTML+=`<div style='background:var(--panel2);border:1px solid var(--border);border-radius:6px;padding:14px'>
      <div style='display:flex;justify-content:space-between;align-items:center;margin-bottom:8px'>
        <b style='color:var(--text);font-size:.88rem'>${esc(r.title||'（无标题）')}</b>
        <div style='display:flex;gap:8px;align-items:center'>
          <span class='badge' style='background:#111;color:${typeColor}'>${typeLabel}</span>
          <span style='color:var(--text2);font-size:.7rem;font-family:Share Tech Mono,monospace'>${r.time_str||''}</span>
        </div>
      </div>
      <div style='font-size:.78rem;color:var(--text2);line-height:1.7'>${content}</div>
    </div>`;
  });
}

// ===== 战区玩家 (Tab 20) =====
let _zpData = [], _zpAllData = [];
async function loadZonePlayers(){
  const [data, stats] = await Promise.all([
    apiFetch('/api/zone_players?limit=500'),
    apiFetch('/api/zone_players/stats')
  ]);
  _zpAllData = data || [];
  _zpData = _zpAllData;
  const cards = document.getElementById('zp-cards');
  if(cards && stats){
    cards.innerHTML=`
      <div class='stat-card'><div class='val'>${stats.total||0}</div><div class='lbl'>战区玩家总数</div></div>
      <div class='stat-card'><div class='val' style='color:var(--gold)'>${(stats.top_unions||[]).length}</div><div class='lbl'>活跃联盟</div></div>
      <div class='stat-card'><div class='val' style='color:var(--cyan)'>${stats.top_players&&stats.top_players[0]?fmt(stats.top_players[0].power):0}</div><div class='lbl'>最高势力值</div></div>
    `;
    // 联盟势力条形图
    const bars = document.getElementById('zp-union-bars');
    if(bars){
      bars.innerHTML='';
      const maxV = Math.max(1,...(stats.top_unions||[]).map(u=>u.total_power||0));
      (stats.top_unions||[]).slice(0,15).forEach(u=>{
        const pct = Math.round((u.total_power||0)/maxV*100);
        bars.innerHTML+=`<div class='bar-row'>
          <div class='bar-label'>${esc(u.union_name||'uid:'+u.union_id)}</div>
          <div class='bar-track'><div class='bar-fill' style='width:${pct}%;background:var(--gold)'></div></div>
          <div class='bar-val'>${u.member_count}人</div>
        </div>`;
      });
    }
  }
  const cnt = document.getElementById('zp-count');
  if(cnt) cnt.textContent = `共${_zpData.length}人`;
  renderZonePlayers(_zpData);
}

function renderZonePlayers(data){
  const b = document.getElementById('zp-body');
  if(!b) return;
  b.innerHTML='';
  data.slice(0,300).forEach((r,i)=>{
    const cls = i===0?'rank-1':i===1?'rank-2':i===2?'rank-3':'';
    const lastActive = r.last_active ? new Date(r.last_active*1000).toLocaleDateString('zh-CN') : '';
    b.innerHTML+=`<tr>
      <td class='${cls}'>${i+1}</td>
      <td><b>${esc(r.name||'')}</b></td>
      <td class='${cls}' style='font-family:Share Tech Mono,monospace'>${fmt(r.power)}</td>
      <td style='color:var(--text2);font-size:.72rem'>${r.union_id||''}</td>
      <td style='font-family:Share Tech Mono,monospace;font-size:.7rem;color:var(--text2)'>${r.wid||''}</td>
      <td style='color:var(--text2);font-size:.68rem'>${lastActive}</td>
    </tr>`;
  });
}

function filterZonePlayers(){
  const q = (document.getElementById('zp-name').value||'').toLowerCase();
  _zpData = q ? _zpAllData.filter(r=>(r.name||'').toLowerCase().includes(q)) : _zpAllData;
  const cnt = document.getElementById('zp-count');
  if(cnt) cnt.textContent = `共${_zpData.length}人`;
  renderZonePlayers(_zpData);
}

// ============================================================
// TAB 21: 战场消息 (00000834 聊天 + 战斗通知)
// ============================================================
let _msgList = [];       // 全部消息
let _msgFilter = 'all';  // 'all' | 'chat' | 'battle_notice'
let _msgChatCount = 0;
let _msgNoticeCount = 0;

async function loadMsgHistory(){
  const data = await apiFetch('/api/msg_history?limit=200');
  if(!data||!data.length) return;
  // 只加载聊天消息，过滤掉战斗通知
  const chats = data.filter(m=>m.kind==='chat');
  chats.forEach(m=>{
    if(!_msgList.find(x=>x.id===m.id)) {
      _msgList.push(m);
      _msgChatCount++;
    }
  });
  document.getElementById('msg-chat-count').textContent = _msgChatCount;
  renderMsgList();
}

// ===== 武将阵容胜率 (Tab 22) =====
let hasHeroComboSnapshot = false;

async function loadHeroCombo(){
  const min = document.getElementById('combo-min')?.value || 3;
  const cards = document.getElementById('combo-cards');
  const b = document.getElementById('combo-body');
  if(!b) return;
  const panel = legacyLoaderSurface(b,'tab23');
  const request = beginLegacyLoaderRequest(
    'hero-combo',
    panel,
    hasHeroComboSnapshot,
  );
  const statusHost = ensureLegacyLoaderStatusHost(panel);
  if(!request.hasSnapshot){
    renderLegacyLoaderStatus(statusHost,'loading','正在计算武将阵容…');
  }else{
    clearLegacyLoaderStatus(statusHost);
  }
  try{
  const data = await apiFetch(`/api/heroes/combo_winrate?min=${min}`);
  if(!isLegacyLoaderRequestCurrent(request)) return;
  if(!Array.isArray(data)){
    throw new Error(data?.error || '武将阵容统计暂时不可用');
  }
  // 统计卡片
  if(cards){
    const total = data.reduce((s,r)=>s+r.total,0);
    const top = data[0];
    cards.innerHTML = `
      <div class='hud-kpi'><div class='hud-kpi-label'>有效组合数</div><div class='hud-kpi-value'>${data.length}</div></div>
      <div class='hud-kpi' style='--hud-kpi-accent:var(--domain-organization)'><div class='hud-kpi-label'>覆盖战报数</div><div class='hud-kpi-value'>${total}</div></div>
      ${top ? `<div class='hud-kpi' style='--hud-kpi-accent:var(--success)'><div class='hud-kpi-label'>最高胜率组合</div><div class='hud-kpi-value'>${top.win_rate}%</div></div>` : ''}
    `;
  }
  const topLineups = document.getElementById('combo-top-lineups');
  if(topLineups){
    topLineups.innerHTML = data.slice(0,3).map((row,index)=>{
      const rank=index+1;
      const rankTier=rank<=3?'top':'standard';
      return `<article class='analysis-lineup-card analysis-evidence' data-kind='history' data-rank-tier='${rankTier}' style='--rank-accent:var(--${rank===1?'warning':rank===2?'text-secondary':'domain-operations'})'>
        <span class='analysis-rank' data-rank='${rank}'>${rank}</span>
        <h3>${esc(row.combo)}</h3>
        <p>历史样本 ${row.total} 场 · 仅代表当前数据库统计</p>
        <div class='analysis-lineup-metrics'>
          <span>胜率 ${row.win_rate}%</span>
          <span>${row.win} 胜 / ${row.draw} 平 / ${row.lose} 负</span>
        </div>
      </article>`;
    }).join('');
  }
  if(!data.length){
    b.innerHTML = `<tr><td colspan=8 style='text-align:center;color:var(--text2);padding:20px'>暂无数据（需要有武将出战记录的战报）</td></tr>`;
    hasHeroComboSnapshot = true;
    clearLegacyLoaderStatus(statusHost);
    return;
  }
  b.innerHTML = data.map((r,i)=>{
    const wr = r.win_rate;
    const barColor = wr>=70?'var(--green)':wr>=50?'var(--gold)':'var(--red)';
    const heroes = r.combo.split('+').map(h=>`<span class='hud-status-chip'>${esc(h)}</span>`).join('');
    const rankTier=i<3?'top':'standard';
    return `<tr class='analysis-row' data-rank-tier='${rankTier}'>
      <td><span class='analysis-rank' data-rank='${i+1}'>${i+1}</span></td>
      <td style='max-width:280px'><div class='analysis-evidence-row'>${heroes}</div></td>
      <td style='font-family:Share Tech Mono,monospace'>${r.total}</td>
      <td style='color:var(--green);font-weight:600'>${r.win}</td>
      <td style='color:var(--red)'>${r.lose}</td>
      <td style='color:var(--text2)'>${r.draw}</td>
      <td style='font-weight:700;color:${barColor}'>${wr}%</td>
      <td style='min-width:100px'><div style='background:var(--panel2);border-radius:3px;height:8px;overflow:hidden'><div style='width:${wr}%;height:100%;background:${barColor};border-radius:3px;transition:width .4s'></div></div></td>
    </tr>`;
  }).join('');
  hasHeroComboSnapshot = true;
  clearLegacyLoaderStatus(statusHost);
  }catch(error){
    if(!isLegacyLoaderRequestCurrent(request)) return;
    const message=error?.message || '武将阵容统计加载失败';
    if(request.hasSnapshot){
      renderLegacyLoaderStatus(statusHost,'error',message);
    }else{
      if(cards) cards.innerHTML='';
      b.innerHTML = `<tr><td colspan=8 style='text-align:center;color:var(--red);padding:20px'>${esc(message)}</td></tr>`;
      renderLegacyLoaderStatus(statusHost,'error',message);
    }
  }finally{
    finishLegacyLoaderRequest(request);
  }
}

// ===== 团数据 (Tab 23) =====
let _trPeriod = 'all';
let _trData = null;

async function loadTeamReport(period){
  const nextPeriod = period || 'all';
  const dim   = document.getElementById('tr-dim')?.value || 'group';
  const group = document.getElementById('tr-group')?.value || '';

  // 高亮按钮
  ['today','yesterday','week','lastweek','all'].forEach(p=>{
    const b = document.getElementById('tr-btn-'+p);
    if(b) b.className = p===nextPeriod ? 'btn btn-primary' : 'btn';
  });

  const tbody = document.getElementById('tr-body');
  const cards = document.getElementById('tr-cards');
  const panel=tbody?.closest('.organization-table-panel');
  const request=beginOrganizationRequest('team-report',panel);
  let statusHost=null;
  try{
  statusHost=ensureOrganizationStatusHost(panel);

  const url = `/api/team_report?period=${nextPeriod}&dim=${dim}&group=${encodeURIComponent(group)}`;
  const res = await apiFetch(url);
  if(!isOrganizationRequestCurrent(request)) return;
  if(!res){
    throw new Error('团数据请求失败');
  }
  if(res.error) throw new Error(String(res.error));
  if(!Array.isArray(res.rows)) throw new Error('团数据格式异常');

  clearOrganizationStatusHost(statusHost);
  _trPeriod = nextPeriod;
  _trData = res;
  const s = res.summary || {};
  const rows = res.rows || [];

  // 填充分组下拉（仅首次）
  if(dim==='group'){
    const sel = document.getElementById('tr-group');
    if(sel && sel.options.length <= 1){
      rows.forEach(r=>{
        if(r.name && r.name!=='未知'){
          const o = document.createElement('option');
          o.value = r.name; o.textContent = r.name;
          sel.appendChild(o);
        }
      });
    }
  }

  // 汇总卡片
  cards.innerHTML = `
    <div class='hud-kpi'><div class='hud-kpi-label'>总战报</div><div class='hud-kpi-value'>${s.total_battles||0}</div></div>
    <div class='hud-kpi' style='--hud-kpi-accent:var(--success)'><div class='hud-kpi-label'>胜率</div><div class='hud-kpi-value'>${s.win_rate||0}%</div></div>
    <div class='hud-kpi' style='--hud-kpi-accent:var(--domain-organization)'><div class='hud-kpi-label'>参战人数</div><div class='hud-kpi-value'>${s.total_players||0}</div></div>
    <div class='hud-kpi' style='--hud-kpi-accent:var(--text-tertiary)'><div class='hud-kpi-label'>平局</div><div class='hud-kpi-value'>${s.total_draws||0}</div></div>
    <div class='hud-kpi' style='--hud-kpi-accent:var(--danger)'><div class='hud-kpi-label'>攻城场次</div><div class='hud-kpi-value'>${s.total_city||0}</div></div>
    <div class='hud-kpi' style='--hud-kpi-accent:var(--domain-analysis)'><div class='hud-kpi-label'>总功勋</div><div class='hud-kpi-value'>${fmt(s.total_gongxun||0)}</div></div>
  `;

  // 表头
  const isGroup = dim==='group';
  document.getElementById('tr-thead').innerHTML = isGroup ? `<tr>
    <th>#</th>
    <th>分组</th>
    <th>人数</th>
    <th>战报</th><th>胜</th><th>败</th><th>平</th><th>胜率</th><th>攻城</th><th>总功勋</th><th>平均武勋</th><th>平均势力值</th>
  </tr>` : `<tr>
    <th>#</th>
    <th>成员</th>
    <th>分组</th>
    <th>战报</th><th>胜</th><th>败</th><th>平</th><th>胜率</th><th>攻城</th><th>功勋</th><th>势力值</th>
  </tr>`;
  document.getElementById('tr-table-title').textContent = isGroup ? '分组战斗数据' : '成员战斗数据';

  // 表格行
  const rowHtml = rows.map((r,i)=>{
    const wrColor = r.win_rate>=60?'var(--green)':r.win_rate>=40?'var(--gold)':'var(--red)';
    const activity = organizationActivityMarkup(r.win_rate,100,'胜率');
    const tailCols = isGroup
      ? `<td style='font-family:Share Tech Mono,monospace;color:var(--purple)'>${fmt(r.total_gongxun||0)}</td>
         <td style='font-family:Share Tech Mono,monospace;color:var(--gold)'>${Math.round(Number(r.avg_gongxun||0))}</td>
         <td style='font-family:Share Tech Mono,monospace;font-size:.72rem;color:var(--text2)'>${Math.round(Number(r.avg_power||0))}</td>`
      : `<td style='font-family:Share Tech Mono,monospace;color:var(--purple)'>${fmt(r.total_gongxun||0)}</td>
         <td style='font-family:Share Tech Mono,monospace;font-size:.72rem;color:var(--text2)'>${fmt(r.power||0)}</td>`;
    const col2 = isGroup
      ? `<td style='color:var(--text2)'>${r.player_cnt||0}人</td>`
      : `<td>${organizationGroupChip(r.group_name||'未分组')}</td>`;
    return `<tr class='organization-row' data-selected='false' data-state='${r.isStale?'stale':'current'}'>
      <td style='color:var(--text2);font-family:Share Tech Mono,monospace'>${i+1}</td>
      <td>${isGroup?organizationGroupChip(r.name||'未知'):organizationIdentityMarkup(r.name||'未知',r.group_name||'成员')}</td>
      ${col2}
      <td style='font-family:Share Tech Mono,monospace'>${r.battles}</td>
      <td style='color:var(--green)'>${r.wins}</td>
      <td style='color:var(--red)'>${r.loses}</td>
      <td style='color:var(--text2)'>${r.draws||0}</td>
      <td><span style='color:${wrColor};font-weight:600'>${r.win_rate}%</span><span style='color:var(--text2);font-size:.72rem'>（${r.wins||0}胜 / ${r.draws||0}平 / ${r.loses||0}负）</span>${activity}</td>
      <td style='color:var(--cyan)'>${r.city_battles||0}</td>
      ${tailCols}
    </tr>`;
  }).join('');
  if(rows.length){
    tbody.innerHTML = rowHtml;
    syncOrganizationRows(tbody, rows.map(rowData=>({
      selected:false,
      isStale:Boolean(rowData.isStale),
    })));
  }else{
    renderOrganizationTableState(tbody, {
      kind:'empty',
      message:'当前筛选没有团数据',
      replace:true,
    }, isGroup ? 12 : 11);
  }

  const periodName = {today:'今日',yesterday:'昨日',week:'本周',lastweek:'上周',all:'全部'}[_trPeriod]||'';
  const el = document.getElementById('tr-update-time');
  if(el) el.textContent = `${periodName} · ${rows.length}条 · ${new Date().toLocaleTimeString('zh-CN')}`;
  const status = document.getElementById('hud-team-report-status');
  if(status){
    status.textContent = `LIVE · ${rows.length}`;
    status.dataset.status = 'live';
  }
  }catch(error){
    if(isOrganizationRequestCurrent(request)){
      renderOrganizationLoadError(tbody, statusHost, {
        kind:'error',
        message:error?.message || '团数据加载失败',
        replace:true,
      }, dim==='group' ? 12 : 11);
    }
  }finally{
    finishOrganizationRequest(request);
  }
}

function buildTeamReportExportHtml(){
  if(!_trData) return '';
  const dim = document.getElementById('tr-dim')?.value||'group';
  const isGroup = dim==='group';
  const periodName = {today:'今日',yesterday:'昨日',week:'本周',lastweek:'上周',all:'全部'}[_trPeriod]||'全部';
  const rows = _trData.rows || [];
  const s = _trData.summary || {};
  const nowText = new Date().toLocaleString('zh-CN', { hour12:false });

  const headHtml = isGroup
    ? `<tr>
        <th>#</th><th>分组</th><th>人数</th><th>战报</th><th>胜</th><th>败</th><th>平</th><th>胜率</th><th>攻城</th><th>总功勋</th><th>平均武勋</th><th>平均势力值</th>
      </tr>`
    : `<tr>
        <th>#</th><th>成员</th><th>分组</th><th>战报</th><th>胜</th><th>败</th><th>平</th><th>胜率</th><th>攻城</th><th>功勋</th><th>势力值</th>
      </tr>`;

  const bodyHtml = rows.map((r,i)=>{
    const wr = Number(r.win_rate||0);
    const wrColor = wr>=60?'#41664d':wr>=40?'#8f6a2a':'#9a4d41';
    const rankCls = i===0?'rank1':i===1?'rank2':i===2?'rank3':'';
    return isGroup
      ? `<tr>
          <td class="${rankCls}">${i+1}</td>
          <td class="name">${esc(r.name||'')}</td>
          <td>${r.player_cnt||0}</td>
          <td>${r.battles||0}</td>
          <td class="win">${r.wins||0}</td>
          <td class="lose">${r.loses||0}</td>
          <td class="draw">${r.draws||0}</td>
          <td><span style="color:${wrColor};font-weight:700">${wr}%</span></td>
          <td>${r.city_battles||0}</td>
          <td>${fmt(r.total_gongxun||0)}</td>
          <td>${Math.round(Number(r.avg_gongxun||0))}</td>
          <td>${Math.round(Number(r.avg_power||0))}</td>
        </tr>`
      : `<tr>
          <td class="${rankCls}">${i+1}</td>
          <td class="name">${esc(r.name||'')}</td>
          <td>${esc(r.group_name||'')}</td>
          <td>${r.battles||0}</td>
          <td class="win">${r.wins||0}</td>
          <td class="lose">${r.loses||0}</td>
          <td class="draw">${r.draws||0}</td>
          <td><span style="color:${wrColor};font-weight:700">${wr}%</span></td>
          <td>${r.city_battles||0}</td>
          <td>${fmt(r.total_gongxun||0)}</td>
          <td>${fmt(r.power||0)}</td>
        </tr>`;
  }).join('');

  return `<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8">
<title>团数据报告</title>
<style>
  :root{
    --paper:#f8f1e4;--paper-soft:#f3e8d6;--paper-soft-2:#eadcc4;--paper-edge:#e1d1b4;--line:#d7c3a0;
    --text:#2a241c;--muted:#7a6956;--title:#1b1510;--accent:#8f6a2a;--accent-soft:#e7d8ba;
    --green:#41664d;--red:#9a4d41;--blue:#4e6a8d;--cyan:#557978;--purple:#705c8d;
  }
  @page{size:A4 landscape;margin:12mm;}
  body{
    margin:0;padding:30px;color:var(--text);font-family:'SimSun','宋体',serif;
    background:
      radial-gradient(circle at top left, rgba(143,106,42,.08), transparent 28%),
      radial-gradient(circle at bottom right, rgba(122,105,86,.08), transparent 24%),
      linear-gradient(180deg,#fbf5ea 0%,#f4ecde 100%);
    -webkit-print-color-adjust:exact;print-color-adjust:exact;
  }
  body.long-shot{padding:20px;}
  .page-shell{
    max-width:1520px;margin:0 auto;padding:18px 18px 10px;
    background:linear-gradient(180deg,rgba(255,251,245,.94) 0%,rgba(248,241,228,.96) 100%);
    border:1px solid var(--paper-edge);border-radius:18px;
    box-shadow:0 18px 40px rgba(120,90,45,.10), inset 0 1px 0 rgba(255,255,255,.55);
    position:relative;overflow:hidden;
  }
  .page-shell::before{
    content:'';position:absolute;inset:10px;border:1px solid rgba(143,106,42,.18);border-radius:12px;pointer-events:none;
  }
  .page{max-width:none;margin:0 auto;position:relative;z-index:1;}
  .header{
    position:relative;display:flex;justify-content:space-between;align-items:flex-end;
    border-bottom:2px solid rgba(143,106,42,.45);padding:4px 8px 16px;margin-bottom:22px;gap:12px;
  }
  .header::after{
    content:'';position:absolute;left:0;bottom:-2px;width:140px;height:4px;border-radius:999px;
    background:linear-gradient(90deg,var(--accent),rgba(143,106,42,0));
  }
  .title{font-size:30px;letter-spacing:.14em;color:var(--title);font-weight:700;}
  .sub{margin-top:6px;color:var(--muted);font-size:13px;letter-spacing:.04em;}
  .meta{font-size:13px;color:var(--muted);text-align:right;line-height:1.8;}
  .cards{display:grid;grid-template-columns:repeat(6,1fr);gap:14px;margin:18px 0 20px;}
  .card{
    background:linear-gradient(180deg,#fffaf1 0%,#f4ead9 100%);
    border:1px solid var(--line);border-radius:16px;padding:16px 14px;text-align:center;
    box-shadow:0 10px 20px rgba(143,106,42,.08), inset 0 1px 0 rgba(255,255,255,.55);
  }
  .card .val{font-size:30px;line-height:1.1;color:var(--title);font-weight:700;}
  .card .lbl{margin-top:6px;font-size:12px;color:var(--muted);letter-spacing:.1em;}
  .table-wrap{
    background:rgba(255,250,242,.94);border:1px solid var(--line);border-radius:16px;overflow:hidden;
    box-shadow:0 10px 24px rgba(86,65,33,.06), inset 0 1px 0 rgba(255,255,255,.55);
  }
  table{width:100%;border-collapse:collapse;background:transparent;}
  thead th{
    background:#efe2cc;color:var(--muted);padding:12px 10px;font-size:13px;text-align:left;
    border-bottom:1px solid var(--line);position:sticky;top:0;
  }
  tbody td{padding:10px 10px;border-bottom:1px solid rgba(215,195,160,.55);font-size:14px;color:var(--text);}
  tbody tr:nth-child(odd){background:rgba(255,251,245,.82);}
  tbody tr:nth-child(even){background:rgba(248,239,224,.66);}
  .name{color:var(--title);font-weight:700;}
  .win{color:var(--green);font-weight:700;}
  .lose{color:var(--red);font-weight:700;}
  .draw{color:var(--accent);font-weight:700;}
  .rank1,.rank2,.rank3{color:var(--title);font-weight:700;}
  .footer{margin-top:14px;color:var(--muted);font-size:12px;text-align:right;}
  .long-shot table{border-radius:0;overflow:visible;}
  .long-shot thead th{position:static;}
  .action-bar{
    position:sticky;top:0;z-index:9;display:flex;justify-content:flex-end;gap:10px;padding:0 0 14px;
    background:linear-gradient(180deg,rgba(244,236,222,.98) 0%,rgba(244,236,222,.9) 78%,rgba(244,236,222,0) 100%);
  }
  .action-btn{
    background:linear-gradient(180deg,#f6e8ca 0%,#e6d0a4 100%);border:1px solid #cfb585;color:#3b2d18;
    font-weight:700;font-size:14px;padding:10px 18px;border-radius:999px;cursor:pointer;
    box-shadow:0 8px 18px rgba(120,90,45,.12);
  }
  .action-btn.secondary{background:linear-gradient(180deg,#fffaf1 0%,#efe1c7 100%);color:var(--text);}
  @media print{
    .action-bar{display:none !important;}
    body{padding:0;background:#fff !important;color:var(--text) !important;}
    .page-shell{max-width:none;box-shadow:none;border-radius:0;}
    .card,.header,.table-wrap,table{break-inside:avoid;box-shadow:none !important;}
  }
</style>
</head>
<body>
  <div class="page-shell">
    <div class="page" id="team-report-page">
      <div class="header">
        <div>
          <div class="title">团数据统计报告</div>
          <div class="sub">${isGroup?'按分组':'按成员'} · ${periodName}${!isGroup&&document.getElementById('tr-group')?.value?` · ${esc(document.getElementById('tr-group').value)}`:''}</div>
        </div>
        <div class="meta">
          <div>导出时间：${nowText}</div>
          <div>记录条数：${rows.length}</div>
        </div>
      </div>

      <div class="cards">
        <div class="card"><div class="val">${s.total_battles||0}</div><div class="lbl">总战报</div></div>
        <div class="card"><div class="val" style="color:var(--green)">${s.win_rate||0}%</div><div class="lbl">胜率</div></div>
        <div class="card"><div class="val" style="color:var(--cyan)">${s.total_players||0}</div><div class="lbl">参战人数</div></div>
        <div class="card"><div class="val" style="color:var(--accent)">${s.total_draws||0}</div><div class="lbl">平局</div></div>
        <div class="card"><div class="val" style="color:var(--red)">${s.total_city||0}</div><div class="lbl">攻城场次</div></div>
        <div class="card"><div class="val" style="color:var(--purple)">${fmt(s.total_gongxun||0)}</div><div class="lbl">总功勋</div></div>
      </div>

      <div class="table-wrap">
        <table>
          <thead>${headHtml}</thead>
          <tbody>${bodyHtml}</tbody>
        </table>
      </div>

      <div class="footer">率土战场指挥台 · 导出报告</div>
    </div>
  </div>
</body>
</html>`;
}

function openTeamReportExportWindow(extraScript=''){
  const html = buildTeamReportExportHtml();
  if(!html) return null;
  const w = window.open('', '_blank');
  if(!w){ showToast('请允许弹窗后再导出','var(--red)'); return null; }
  w.document.open();
  w.document.write(html + extraScript);
  w.document.close();
  return w;
}

// ===== 州郡 / 团统计 (Tab 26) =====
let _srData = null;
let hasStateRegionSnapshot = false;
let _stateRegionAbortController = null;

const SR_STATE_POLYGONS = [
  { name:'凉州', center:[175,188], label:[176,188], value:[176,212], points:'58,170 72,150 96,140 114,118 150,104 198,106 236,118 268,136 294,160 298,188 284,212 286,238 262,252 226,262 198,274 168,270 138,258 110,244 82,228 64,206' },
  { name:'并州', center:[330,166], label:[330,164], value:[330,188], points:'242,118 278,102 322,96 372,100 416,108 448,126 470,150 474,176 456,196 430,208 412,226 380,234 344,230 316,214 286,204 270,184 262,160 252,140' },
  { name:'冀州', center:[532,164], label:[532,160], value:[532,184], points:'430,112 472,100 526,96 586,98 638,106 676,122 704,146 704,174 682,196 650,206 626,198 604,188 564,184 532,180 500,180 466,188 436,182 420,160 420,134' },
  { name:'幽州', center:[716,88], label:[716,84], value:[716,108], points:'596,62 630,48 670,38 722,36 776,44 822,60 858,84 876,108 860,124 824,126 792,116 760,110 724,108 692,108 660,104 628,100 600,88 586,74' },
  { name:'青州', center:[698,186], label:[698,182], value:[698,206], points:'624,132 660,132 706,138 752,148 792,166 810,192 804,220 780,238 742,242 706,240 676,236 652,226 632,206 614,184 612,156' },
  { name:'徐州', center:[794,276], label:[794,272], value:[794,296], points:'766,200 806,202 850,214 888,234 912,262 918,292 904,320 880,344 846,356 806,356 772,348 742,332 722,304 718,276 726,246 742,222' },
  { name:'兖州', center:[546,242], label:[546,238], value:[546,262], points:'462,188 496,184 534,184 576,186 612,192 638,206 646,230 636,252 610,264 582,270 550,272 520,268 494,258 468,244 454,222 450,202' },
  { name:'司隶', center:[404,250], label:[404,244], value:[404,266], points:'330,224 358,214 392,210 426,212 460,220 484,234 488,254 474,272 450,282 424,286 396,286 368,282 344,274 324,258 320,240' },
  { name:'豫州', center:[566,314], label:[566,308], value:[566,332], points:'482,270 516,272 554,274 598,276 634,286 660,306 664,334 650,358 620,372 584,376 548,372 514,362 488,344 470,320 468,294' },
  { name:'雍州', center:[346,328], label:[346,322], value:[346,346], points:'264,250 302,250 340,260 382,274 420,294 430,322 422,352 402,380 366,394 326,394 292,384 264,366 246,338 242,306 250,278' },
  { name:'荆州', center:[440,396], label:[440,390], value:[440,414], points:'374,350 414,344 456,346 502,356 536,376 550,404 544,430 522,452 486,464 446,468 408,462 374,446 354,422 350,392 358,368' },
  { name:'扬州', center:[648,410], label:[648,404], value:[648,428], points:'548,362 584,374 626,378 674,382 724,394 754,416 764,444 754,468 718,478 676,476 634,470 596,458 566,438 548,412 542,386' },
  { name:'益州', center:[184,336], label:[184,330], value:[184,354], points:'64,238 90,228 122,236 154,246 186,256 218,268 246,286 250,314 242,344 224,372 196,394 158,406 122,404 90,392 62,370 46,340 40,304 46,270' }
];

function srGetMapMetric(){
  return document.getElementById('sr-map-metric')?.value || 'player_count';
}

function srMetricLabel(metric){
  return metric === 'total_power' ? '势力值' : '人数';
}

function srMetricValue(row, metric){
  return Number(row?.[metric] || 0);
}

function srLegendGradient(metric){
  return metric === 'total_power'
    ? 'linear-gradient(180deg,#ffd98a 0%, #c98a1f 55%, #2a1a08 100%)'
    : 'linear-gradient(180deg,#9bc8ff 0%, #377dff 55%, #182338 100%)';
}

function srStateColor(value, maxValue, metric){
  const v = Number(value||0);
  const max = Math.max(1, Number(maxValue||0));
  const ratio = Math.max(0, Math.min(1, v / max));
  const h = metric === 'total_power' ? 38 : 216;
  const s = metric === 'total_power' ? 84 : 72;
  const l = 18 + ratio * 58;
  return `hsl(${h} ${s}% ${l}%)`;
}

function renderStateMap(stateRows){
  const svg = document.getElementById('sr-map-svg');
  if(!svg) return;
  try {
    const rows = Array.isArray(stateRows) ? stateRows : [];
    const metric = srGetMapMetric();
    const metricLabel = srMetricLabel(metric);
    const stateMap = {};
    rows.forEach(r=>{ stateMap[String((r && r.state) || '')] = r; });
    const maxMetric = Math.max(1, ...rows.map(r=>srMetricValue(r, metric)));
    const legendBar = document.getElementById('sr-map-legend-bar');
    const legendHigh = document.getElementById('sr-map-legend-high');
    const legendLow = document.getElementById('sr-map-legend-low');
    if(legendBar) legendBar.style.background = srLegendGradient(metric);
    if(legendHigh) legendHigh.textContent = `多 ${metricLabel}`;
    if(legendLow) legendLow.textContent = `少 ${metricLabel}`;

    const html = SR_STATE_POLYGONS.map((item)=>{
      const row = stateMap[item.name] || {};
      const players = Number(row.player_count||0);
      const totalPower = Number(row.total_power||0);
      const avgPower = Number(row.avg_power||0);
      const metricValue = srMetricValue(row, metric);
      const fill = srStateColor(metricValue, maxMetric, metric);
      const labelPos = item.label || item.center || [0,0];
      const valuePos = item.value || [labelPos[0], labelPos[1] + 22];
      const labelX = labelPos[0], labelY = labelPos[1];
      const valueX = valuePos[0], valueY = valuePos[1];
      const valueText = metric === 'total_power' ? fmt(totalPower) : `${players}人`;
      return `
        <g class='sr-map-state' style='cursor:default'>
          <polygon points='${item.points}' fill='${fill}' stroke='#7387a8' stroke-width='2.2' opacity='0.97'>
            <title>${item.name}\n人数：${players}人\n总势力：${fmt(totalPower)}\n平均势力：${fmt(avgPower)}\n当前着色：${metricLabel} ${metric === 'total_power' ? fmt(totalPower) : players}</title>
          </polygon>
          <text x='${labelX}' y='${labelY}' text-anchor='middle' fill='#f3efe4' style='font-size:18px;font-weight:700;paint-order:stroke;stroke:#101722;stroke-width:4'>${item.name}</text>
          <text x='${valueX}' y='${valueY}' text-anchor='middle' fill='#f3efe4' style='font-size:${metric === 'total_power' ? 14 : 16}px;paint-order:stroke;stroke:#101722;stroke-width:4'>${valueText}</text>
        </g>`;
    }).join('');

    svg.innerHTML = `
      <defs>
        <filter id='srMapGlow'>
          <feDropShadow dx='0' dy='0' stdDeviation='8' flood-color='${metric === 'total_power' ? '#f59e0b' : '#3b82f6'}' flood-opacity='0.22'/>
        </filter>
      </defs>
      <g filter='url(#srMapGlow)'>${html}</g>
    `;
  } catch (e) {
    console.error('renderStateMap failed:', e);
    svg.innerHTML = '';
  }
}

async function loadStateRegionStats(){
  const scope = document.getElementById('sr-scope')?.value || 'all';
  const group = document.getElementById('sr-group')?.value || '';
  const panel = document.getElementById('tab26');
  _stateRegionAbortController?.abort();
  const request = beginLegacyLoaderRequest(
    'state-region',
    panel,
    hasStateRegionSnapshot,
  );
  const controller = new AbortController();
  request.controller = controller;
  _stateRegionAbortController = controller;
  const statusHost = ensureLegacyLoaderStatusHost(panel);
  if(!request.hasSnapshot){
    renderLegacyLoaderStatus(statusHost,'loading','正在加载州郡分布…');
  }else{
    clearLegacyLoaderStatus(statusHost);
  }
  try{
  const res = await apiFetch(
    `/api/state_region_stats?scope=${scope}&group=${encodeURIComponent(group)}`,
    {signal:controller.signal},
  );
  if(!isLegacyLoaderRequestCurrent(request)) return;
  if(!res || res.error){
    throw new Error(res?.error || '州郡分布暂时不可用');
  }
  _srData = res;
  const meta = res.meta || {};

  const groups = Array.isArray(res.groups) ? res.groups : [];
  const groupSel = document.getElementById('sr-group');
  if(groupSel){
    const prev = groupSel.value;
    const options = [document.createElement('option')];
    options[0].value = '';
    options[0].textContent = '全部团';
    groups.forEach(value=>{
      const option = document.createElement('option');
      option.value = String(value);
      option.textContent = String(value);
      options.push(option);
    });
    groupSel.replaceChildren(...options);
    if(groups.includes(prev)) groupSel.value = prev;
    if(scope !== 'group') groupSel.value = '';
    groupSel.disabled = scope !== 'group';
    groupSel.style.opacity = scope === 'group' ? '1' : '.6';
  }

  const s = res.summary || {};
  const stateRows = res.state_rows || [];
  const groupRows = res.group_rows || [];
  const allianceRows = res.alliance_rows || [];
  const emptyHint = meta.message || '';

  const cards = document.getElementById('sr-cards');
  if(cards){
    cards.innerHTML = `
      <div class='hud-kpi'><div class='hud-kpi-label'>统计人数</div><div class='hud-kpi-value'>${s.total_players||0}</div></div>
      <div class='hud-kpi' style='--hud-kpi-accent:var(--warning)'><div class='hud-kpi-label'>总势力值</div><div class='hud-kpi-value'>${fmt(s.total_power||0)}</div></div>
      <div class='hud-kpi' style='--hud-kpi-accent:var(--domain-intelligence)'><div class='hud-kpi-label'>覆盖州数</div><div class='hud-kpi-value'>${s.state_count||0}</div></div>
      <div class='hud-kpi' style='--hud-kpi-accent:var(--info)'><div class='hud-kpi-label'>同盟数量</div><div class='hud-kpi-value'>${s.alliance_count||0}</div></div>
      <div class='hud-kpi' style='--hud-kpi-accent:var(--domain-analysis)'><div class='hud-kpi-label'>分组数量</div><div class='hud-kpi-value'>${s.group_count||0}</div></div>
      <div class='hud-kpi' style='--hud-kpi-accent:var(--success)'><div class='hud-kpi-label'>已分组成员</div><div class='hud-kpi-value'>${s.grouped_players||0}</div></div>
    `;
  }
  const noteEl = document.getElementById('sr-note');
  if(noteEl){
    noteEl.textContent = emptyHint;
    noteEl.hidden = !emptyHint;
  }

  const countEl = document.getElementById('sr-count');
  if(countEl) countEl.textContent = emptyHint ? emptyHint : `州 ${stateRows.length} 条 / 同盟 ${allianceRows.length} 条 / 分组 ${groupRows.length} 条`;
  const timeEl = document.getElementById('sr-update-time');
  const updateText = `更新于 ${new Date().toLocaleTimeString('zh-CN',{hour12:false})}`;
  if(timeEl) timeEl.textContent = updateText;
  const freshness = document.getElementById('hud-region-updated');
  if(freshness){
    freshness.textContent = emptyHint ? 'DEGRADED' : 'LIVE';
    freshness.dataset.status = emptyHint ? 'degraded' : 'live';
    freshness.title = updateText;
  }

  const stateBody = document.getElementById('sr-state-body');
  if(stateBody){
    stateBody.innerHTML = stateRows.map((r,i)=>{
      const cls = i===0?'rank-1':i===1?'rank-2':i===2?'rank-3':'';
      return `<tr>
        <td class='${cls}' style='font-family:Share Tech Mono,monospace'>${i+1}</td>
        <td><b>${esc(r.state||'未知')}</b></td>
        <td>${r.player_count||0}</td>
        <td class='${cls}' style='font-family:Share Tech Mono,monospace'>${fmt(r.total_power||0)}</td>
        <td style='color:var(--gold)'>${fmt(r.avg_power||0)}</td>
        <td style='color:var(--text2)'>${fmt(r.max_power||0)}</td>
      </tr>`;
    }).join('') || `<tr><td colspan='6' style='text-align:center;color:var(--text2);padding:20px'>${esc(emptyHint || '暂无数据')}</td></tr>`;
  }

  renderStateMap(stateRows);

  const stateBars = document.getElementById('sr-state-bars');
  if(stateBars){
    stateBars.innerHTML = '';
    const top = stateRows.slice(0,13);
    if(!top.length && emptyHint){
      stateBars.innerHTML = `<div style='text-align:center;color:var(--text2);padding:20px'>${esc(emptyHint)}</div>`;
    } else {
      const maxV = Math.max(1, ...top.map(r=>Number(r.total_power||0)));
      top.forEach(r=>{
        const pct = Math.round(Number(r.total_power||0) / maxV * 100);
        stateBars.innerHTML += `<div class='bar-row'>
          <div class='bar-label'>${esc(r.state||'未知')}</div>
          <div class='bar-track'><div class='bar-fill' style='width:${pct}%;background:var(--blue)'></div></div>
          <div class='bar-val'>${fmt(r.total_power||0)}</div>
        </div>`;
      });
    }
  }

  const groupBody = document.getElementById('sr-group-body');
  if(groupBody){
    groupBody.innerHTML = groupRows.map((r,i)=>{
      const cls = i===0?'rank-1':i===1?'rank-2':i===2?'rank-3':'';
      return `<tr>
        <td class='${cls}' style='font-family:Share Tech Mono,monospace'>${i+1}</td>
        <td><span class='badge' style='background:#172232;color:var(--cyan)'>${esc(r.alliance_name||'未加入同盟')}</span></td>
        <td><b>${esc(r.group_name||'未分组')}</b></td>
        <td>${r.player_count||0}</td>
        <td class='${cls}' style='font-family:Share Tech Mono,monospace'>${fmt(r.total_power||0)}</td>
        <td style='font-size:.72rem;color:var(--text2);white-space:normal;line-height:1.45'>${esc(r.state_summary||'-')}</td>
      </tr>`;
    }).join('') || `<tr><td colspan='6' style='text-align:center;color:var(--text2);padding:20px'>${esc(emptyHint || '暂无数据')}</td></tr>`;
  }

  const groupBars = document.getElementById('sr-group-bars');
  if(groupBars){
    groupBars.innerHTML = '';
    const top = groupRows.slice(0,15);
    if(!top.length && emptyHint){
      groupBars.innerHTML = `<div style='text-align:center;color:var(--text2);padding:20px'>${esc(emptyHint)}</div>`;
    } else {
      const maxV = Math.max(1, ...top.map(r=>Number(r.player_count||0)));
      top.forEach(r=>{
        const pct = Math.round(Number(r.player_count||0) / maxV * 100);
        groupBars.innerHTML += `<div class='bar-row'>
          <div class='bar-label'>${esc((r.alliance_name||'未加入同盟') + ' / ' + (r.group_name||'未分组'))}</div>
          <div class='bar-track'><div class='bar-fill' style='width:${pct}%;background:var(--purple)'></div></div>
          <div class='bar-val'>${r.player_count||0}人</div>
        </div>`;
      });
    }
  }
  hasStateRegionSnapshot = true;
  clearLegacyLoaderStatus(statusHost);
  }catch(error){
    if(!isLegacyLoaderRequestCurrent(request)) return;
    if(error?.name==='AbortError' || controller.signal.aborted) return;
    const message=error?.message || '州郡分布加载失败';
    const noteEl = document.getElementById('sr-note');
    if(request.hasSnapshot){
      renderLegacyLoaderStatus(statusHost,'error',message);
      if(noteEl){
        noteEl.textContent = message;
        noteEl.hidden = false;
      }
    }else{
      renderLegacyLoaderStatus(statusHost,'error',message);
      if(noteEl){
        noteEl.textContent = message;
        noteEl.hidden = false;
      }
      const stateBody=document.getElementById('sr-state-body');
      const groupBody=document.getElementById('sr-group-body');
      if(stateBody) stateBody.innerHTML=`<tr><td colspan='6' style='text-align:center;color:var(--red);padding:20px'>${esc(message)}</td></tr>`;
      if(groupBody) groupBody.innerHTML=`<tr><td colspan='6' style='text-align:center;color:var(--red);padding:20px'>${esc(message)}</td></tr>`;
    }
    const freshness=document.getElementById('hud-region-updated');
    if(freshness){
      freshness.textContent='DEGRADED';
      freshness.dataset.status='degraded';
    }
  }finally{
    if(_stateRegionAbortController===controller){
      _stateRegionAbortController=null;
    }
    finishLegacyLoaderRequest(request);
  }
}

function exportTeamReportPretty(){
  return exportTeamReportPDF();
}

function withOrganizationExportBusy(action, button=document.activeElement){
  const exportButton = button?.matches?.('button') ? button : null;
  const previousMinWidth = exportButton?.style.minWidth || '';
  if(exportButton){
    const width = Math.ceil(exportButton.getBoundingClientRect().width);
    if(width) exportButton.style.minWidth = `${width}px`;
    exportButton.setAttribute("aria-busy", "true");
    exportButton.disabled = true;
  }
  const finish = ()=>{
    if(!exportButton) return;
    exportButton.removeAttribute("aria-busy");
    exportButton.disabled = false;
    exportButton.style.minWidth = previousMinWidth;
  };
  let result;
  try{
    result = action();
  }catch(error){
    finish();
    throw error;
  }
  return Promise.resolve(result).finally(finish);
}

function exportTeamReportPDF(){
  return withOrganizationExportBusy(()=>{
    const w = openTeamReportExportWindow();
    if(!w) return null;
    return new Promise((resolve,reject)=>setTimeout(()=>{
      try{
        if(w.closed) throw new Error('导出窗口已关闭');
        w.print();
        resolve(w);
      }catch(error){
        reject(error);
      }
    }, 350));
  });
}

function exportTeamReportLongImage(){
  return withOrganizationExportBusy(()=>{
    const extraScript = `<script>
    document.body.classList.add('long-shot');
    const bar = document.createElement('div');
    bar.className = 'action-bar';
    bar.innerHTML = '<button class="action-btn" id="save-long-shot">保存长图</button><button class="action-btn secondary" onclick="window.print()">打印 / 另存PDF</button>';
    const shell = document.querySelector('.page-shell') || document.body;
    shell.insertBefore(bar, shell.firstChild);
    function downloadLongShot(){
      try{
        const page = document.getElementById('team-report-page');
        const shellEl = document.querySelector('.page-shell') || page;
        const oldBg = document.body.style.background;
        document.body.style.background = '#f4ecde';
        const svg = '<svg xmlns="http://www.w3.org/2000/svg" width="' + shellEl.scrollWidth + '" height="' + shellEl.scrollHeight + '">' +
          '<foreignObject width="100%" height="100%">' + new XMLSerializer().serializeToString(shellEl) + '</foreignObject>' +
          '</svg>';
        const blob = new Blob([svg], {type:'image/svg+xml;charset=utf-8'});
        const url = URL.createObjectURL(blob);
        const img = new Image();
        img.onload = function(){
          const canvas = document.createElement('canvas');
          canvas.width = shellEl.scrollWidth;
          canvas.height = shellEl.scrollHeight;
          const ctx = canvas.getContext('2d');
          ctx.fillStyle = '#f4ecde';
          ctx.fillRect(0,0,canvas.width,canvas.height);
          ctx.drawImage(img,0,0);
          URL.revokeObjectURL(url);
          document.body.style.background = oldBg;
          const a = document.createElement('a');
          a.href = canvas.toDataURL('image/png');
          a.download = '团数据长图_' + new Date().toISOString().slice(0,10) + '.png';
          a.click();
        };
        img.onerror = function(){
          URL.revokeObjectURL(url);
          alert('当前浏览器不支持直接生成长图，请改用“打印 / 另存PDF”。');
        };
        img.src = url;
      }catch(e){
        alert('生成长图失败：' + (e && e.message ? e.message : e));
      }
    }
    document.getElementById('save-long-shot').addEventListener('click', downloadLongShot);
  <\/script>`;
    const w = openTeamReportExportWindow(extraScript);
    if(!w) return null;
    return new Promise(resolve=>setTimeout(()=>resolve(w), 350));
  });
}

function exportTeamReportCSV(){
  if(!_trData) return;
  const dim = document.getElementById('tr-dim')?.value||'group';
  const isGroup = dim==='group';
  const headers = isGroup
    ? ['#','分组','人数','战报','胜','败','平','胜率%','攻城','总功勋','平均武勋','平均势力值']
    : ['#','成员','分组','战报','胜','败','平','胜率%','攻城','功勋','势力值'];
  const rows = (_trData.rows||[]).map((r,i)=> isGroup
    ? [i+1, r.name||'', r.player_cnt||0, r.battles, r.wins, r.loses, r.draws||0, r.win_rate, r.city_battles||0, r.total_gongxun||0, r.avg_gongxun||0, r.avg_power||0]
    : [i+1, r.name||'', r.group_name||'', r.battles, r.wins, r.loses, r.draws||0, r.win_rate, r.city_battles||0, r.total_gongxun||0, r.power||0]
  );
  const csv = [headers,...rows].map(r=>r.map(c=>`"${String(c).replace(/"/g,'""')}"`).join(',')).join('\n');
  const blob = new Blob(['\uFEFF'+csv],{type:'text/csv;charset=utf-8'});
  const a = document.createElement('a');
  a.href = URL.createObjectURL(blob);
  a.download = `团数据_${_trPeriod}_${new Date().toISOString().slice(0,10)}.csv`;
  a.click();
}

function getCurrentPageEl(){
  return document.querySelector('.page.active') || document.querySelector('.page');
}

function getCurrentPageTitle(pageEl){
  if(!pageEl) return '页面';
  const headTitle = pageEl.querySelector('.tbl-head h3');
  if(headTitle && headTitle.textContent.trim()) return headTitle.textContent.trim();
  const navBtn = document.querySelector(`nav button[onclick*="${pageEl.id?.replace('tab','')}"].active`);
  if(navBtn && navBtn.textContent.trim()) return navBtn.textContent.trim();
  return pageEl.id || '页面';
}

function buildPageExportHtml(pageEl, options={}){
  if(!pageEl) return '';
  const title = options.title || getCurrentPageTitle(pageEl);
  const nowText = new Date().toLocaleString('zh-CN', { hour12:false });
  const clone = pageEl.cloneNode(true);
  clone.querySelectorAll('button').forEach(btn=>btn.remove());
  clone.querySelectorAll('[onclick]').forEach(el=>el.removeAttribute('onclick'));
  clone.querySelectorAll('input').forEach(inp=>{
    const span = document.createElement('span');
    span.textContent = inp.value || inp.placeholder || '';
    span.style.cssText = 'display:inline-block;min-width:48px;padding:6px 10px;border:1px solid #d6c8ad;border-radius:999px;background:#fbf6ed;color:#2a241c;box-shadow:inset 0 1px 0 rgba(255,255,255,.55);';
    inp.replaceWith(span);
  });
  clone.querySelectorAll('select').forEach(sel=>{
    const span = document.createElement('span');
    const text = sel.options && sel.selectedIndex >= 0 ? sel.options[sel.selectedIndex].text : '';
    span.textContent = text;
    span.style.cssText = 'display:inline-block;min-width:48px;padding:6px 10px;border:1px solid #d6c8ad;border-radius:999px;background:#fbf6ed;color:#2a241c;box-shadow:inset 0 1px 0 rgba(255,255,255,.55);';
    sel.replaceWith(span);
  });
  clone.querySelectorAll('.tbl-scroll,.feed').forEach(el=>{
    el.style.maxHeight = 'none';
    el.style.overflow = 'visible';
  });
  clone.querySelectorAll('thead th').forEach(th=>th.style.position='static');
  const content = clone.innerHTML;
  return `<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8">
<title>${title}</title>
<style>
  :root{
    --paper:#f8f1e4;--paper-soft:#f3e8d6;--paper-soft-2:#eadcc4;--paper-edge:#e1d1b4;--line:#d7c3a0;
    --text:#2a241c;--muted:#7a6956;--title:#1b1510;--accent:#8f6a2a;--accent-soft:#e7d8ba;
    --green:#41664d;--red:#9a4d41;--blue:#4e6a8d;--cyan:#557978;--purple:#705c8d;
  }
  @page{size:A4 landscape;margin:12mm;}
  body{
    margin:0;padding:30px;color:var(--text);font-family:'SimSun','宋体',serif;
    background:
      radial-gradient(circle at top left, rgba(143,106,42,.08), transparent 28%),
      radial-gradient(circle at bottom right, rgba(122,105,86,.08), transparent 24%),
      linear-gradient(180deg,#fbf5ea 0%,#f4ecde 100%);
    -webkit-print-color-adjust:exact;print-color-adjust:exact;
  }
  .page-shell{
    max-width:1520px;margin:0 auto;padding:18px 18px 10px;
    background:linear-gradient(180deg,rgba(255,251,245,.94) 0%,rgba(248,241,228,.96) 100%);
    border:1px solid var(--paper-edge);border-radius:18px;
    box-shadow:0 18px 40px rgba(120,90,45,.10), inset 0 1px 0 rgba(255,255,255,.55);
    position:relative;overflow:hidden;
  }
  .page-shell::before{
    content:'';position:absolute;inset:10px;border:1px solid rgba(143,106,42,.18);border-radius:12px;pointer-events:none;
  }
  .export-header{
    position:relative;display:flex;justify-content:space-between;align-items:flex-end;
    border-bottom:2px solid rgba(143,106,42,.45);padding:4px 8px 16px;margin-bottom:22px;gap:12px;
  }
  .export-header::after{
    content:'';position:absolute;left:0;bottom:-2px;width:140px;height:4px;border-radius:999px;
    background:linear-gradient(90deg,var(--accent),rgba(143,106,42,0));
  }
  .export-title{font-size:30px;letter-spacing:.14em;color:var(--title);font-weight:700;}
  .export-subtitle{margin-top:6px;color:var(--muted);font-size:13px;letter-spacing:.04em;}
  .export-meta{font-size:13px;color:var(--muted);text-align:right;line-height:1.8;}
  .page{display:block !important;animation:none !important;position:relative;z-index:1;}
  .cards-row{display:flex;gap:14px;margin-bottom:20px;flex-wrap:wrap;}
  .stat-card{
    flex:1;min-width:148px;background:linear-gradient(180deg,#fffaf1 0%,#f4ead9 100%);
    border:1px solid var(--line);border-radius:16px;padding:16px 14px;text-align:center;
    box-shadow:0 10px 20px rgba(143,106,42,.08), inset 0 1px 0 rgba(255,255,255,.55);
  }
  .stat-card .val{font-size:30px;color:var(--title);line-height:1.1;font-weight:700;}
  .stat-card .lbl{margin-top:6px;font-size:12px;color:var(--muted);letter-spacing:.14em;}
  .tbl-wrap{
    background:rgba(255,250,242,.94);border:1px solid var(--line);border-radius:16px;overflow:hidden;
    box-shadow:0 10px 24px rgba(86,65,33,.06), inset 0 1px 0 rgba(255,255,255,.55);
  }
  .tbl-head{
    display:flex;align-items:center;justify-content:space-between;padding:13px 18px;border-bottom:1px solid var(--line);
    background:linear-gradient(180deg,#f7ecdc 0%,#efdfc5 100%);
  }
  .tbl-head h3{font-size:.92rem;letter-spacing:.12em;color:var(--title);margin:0;}
  .tbl-controls{display:flex;gap:8px;align-items:center;flex-wrap:wrap;color:var(--muted);}
  .tbl-scroll,.feed{max-height:none !important;overflow:visible !important;}
  table{width:100%;border-collapse:collapse;font-size:14px;background:transparent;}
  thead th{
    padding:11px 12px;background:#efe2cc;color:var(--muted);text-align:left;border-bottom:1px solid var(--line);
    position:static !important;font-weight:600;
  }
  tbody td{padding:10px 12px;color:var(--text);border-bottom:1px solid rgba(215,195,160,.55);white-space:nowrap;}
  tbody tr:nth-child(odd){background:rgba(255,251,245,.82);}
  tbody tr:nth-child(even){background:rgba(248,239,224,.66);}
  .badge{
    display:inline-block;padding:2px 8px;border-radius:999px;font-size:12px;
    background:var(--accent-soft);color:var(--text);border:1px solid #d7c19a;
  }
  .feed-item,.bar-row{break-inside:avoid;}
  .bar-track{background:#e8dac0 !important;border:1px solid var(--line) !important;border-radius:999px !important;}
  .bar-fill{border-radius:999px !important;}
  .prog{background:#e8dac0 !important;}
  .export-footer{margin-top:16px;color:var(--muted);font-size:12px;text-align:right;}
  .rank-1,.rank-2,.rank-3{color:var(--title) !important;font-weight:700;}
  .btn{border-color:var(--line) !important;color:var(--text) !important;background:#f8f0e2 !important;}
  tr[style*='background:#0d1420'],tr[style*='background:#101925'],tr[style*='background:var(--panel2)'],tr[style*='background:#121c2a'],tr[style*='background:#101929'],tr[style*='background:#111a28']{background:rgba(248,239,224,.66) !important;}
  td[style*='background:#0d1420'],td[style*='background:#101925'],td[style*='background:var(--panel2)'],td[style*='background:#121c2a'],td[style*='background:#101929'],td[style*='background:#111a28'],span[style*='background:#0d1520'],span[style*='background:#0d1820'],span[style*='background:#111a28'],span[style*='background:#172232'],div[style*='background:#0d1520'],div[style*='background:#0d1820'],div[style*='background:#111a28'],div[style*='background:#172232']{background:var(--accent-soft) !important;color:var(--text) !important;border-color:#d7c19a !important;}
  img{filter:saturate(.88) contrast(.96);}
  [style*='box-shadow']{box-shadow:none !important;}
  [style*='text-shadow']{text-shadow:none !important;}
  [style*='color:transparent']{color:var(--text) !important;}
  [style*='opacity:0']{opacity:1 !important;}
  [style*='var(--gold)'],[style*='var(--gold2)']{color:var(--accent) !important;}
  [style*='var(--green)']{color:var(--green) !important;}
  [style*='var(--red)']{color:var(--red) !important;}
  [style*='var(--blue)']{color:var(--blue) !important;}
  [style*='var(--cyan)']{color:var(--cyan) !important;}
  [style*='var(--purple)']{color:var(--purple) !important;}
  [style*='var(--text2)'],[style*='color:#7a8a9a'],[style*='color:#66788b'],[style*='color:#8ea0b3']{color:var(--muted) !important;}
  [style*='color:var(--text)'],[style*='color:#d4cfc0'],[style*='color:#fff'],[style*='color:white']{color:var(--text) !important;}
  input,select,span[style*='border:1px solid #cfd6de']{background:#fbf6ed !important;color:var(--text) !important;border-color:#d6c8ad !important;}
  @media print{
    body{padding:0;background:#fff !important;color:var(--text) !important;}
    .page-shell{max-width:none;box-shadow:none;border-radius:0;}
    .tbl-wrap,.stat-card{break-inside:avoid;box-shadow:none !important;}
    .tbl-wrap,.stat-card,table{box-shadow:none !important;}
  }
</style>
</head>
<body>
  <div class="page-shell">
    <div class="export-header">
      <div>
        <div class="export-title">${title}</div>
        <div class="export-subtitle">当前页面导出（米色古风战报）</div>
      </div>
      <div class="export-meta">
        <div>导出时间：${nowText}</div>
      </div>
    </div>
    ${content}
    <div class="export-footer">率土战场指挥台 · 页面导出</div>
  </div>
</body>
</html>`;
}

function openExportWindowFromHtml(html){
  if(!html) return null;
  const w = window.open('', '_blank');
  if(!w){ showToast('请允许弹窗后再导出','var(--red)'); return null; }
  w.document.open();
  w.document.write(html);
  w.document.close();
  return w;
}

function getPageExportSnapshot(pageEl){
  if(!pageEl) return null;
  const title = getCurrentPageTitle(pageEl);
  const tables = [...pageEl.querySelectorAll('table')].map((table, idx)=>{
    const wrap = table.closest('.tbl-wrap') || table.parentElement;
    const h3 = wrap?.querySelector('.tbl-head h3');
    const secTitle = h3?.textContent?.trim() || `表格 ${idx+1}`;
    const headers = [...table.querySelectorAll('thead th')].map(th=>th.innerText.trim());
    const rows = [...table.querySelectorAll('tbody tr')].map(tr=>[...tr.querySelectorAll('td')].map(td=>td.innerText.trim().replace(/\n+/g,' / ')));
    return { title: secTitle, headers, rows };
  });
  const stats = [...pageEl.querySelectorAll('.stat-card')].map(card=>({
    value: card.querySelector('.val')?.innerText?.trim() || '',
    label: card.querySelector('.lbl')?.innerText?.trim() || ''
  })).filter(x=>x.value || x.label);
  return { title, tables, stats };
}

function ensureExportLibraries(){
  const hasExcel = typeof ExcelJS !== 'undefined';
  const hasPdf = !!(window.jspdf && window.jspdf.jsPDF);
  if(!hasExcel || !hasPdf){
    showToast('导出库加载中，请稍后再试','var(--red)');
    return false;
  }
  return true;
}

async function exportCurrentPagePDF(){
  const pageEl = getCurrentPageEl();
  const html = buildPageExportHtml(pageEl);
  const w = openExportWindowFromHtml(html);
  if(!w) return;
  setTimeout(()=>w.print(), 350);
}

function applyExcelHeaderStyle(cell, fill='FF121D2C', color='FF8EA0B3', size=11){
  cell.font = { bold:true, color:{ argb:color }, name:'Microsoft YaHei', size };
  cell.fill = { type:'pattern', pattern:'solid', fgColor:{ argb:fill } };
  cell.alignment = { vertical:'middle', horizontal:'center', wrapText:true };
  cell.border = { top:{style:'thin',color:{argb:'FF1D2A3C'}}, left:{style:'thin',color:{argb:'FF1D2A3C'}}, bottom:{style:'thin',color:{argb:'FF1D2A3C'}}, right:{style:'thin',color:{argb:'FF1D2A3C'}} };
}

function applyExcelBodyStyle(cell, fill='FF0F1620', align='left'){
  cell.font = { color:{ argb:'FFD8D2C0' }, name:'Microsoft YaHei', size:10 };
  cell.fill = { type:'pattern', pattern:'solid', fgColor:{ argb:fill } };
  cell.alignment = { vertical:'middle', horizontal:align, wrapText:true };
  cell.border = { top:{style:'thin',color:{argb:'FF172232'}}, left:{style:'thin',color:{argb:'FF172232'}}, bottom:{style:'thin',color:{argb:'FF172232'}}, right:{style:'thin',color:{argb:'FF172232'}} };
}

function finishWorksheetLayout(ws, widths, frozenRows=5){
  ws.columns = widths.map(w=>({ width:w }));
  ws.views = [{ state:'frozen', ySplit:frozenRows }];
  if(ws.rowCount >= frozenRows){
    ws.autoFilter = { from: { row:frozenRows, column:1 }, to: { row:frozenRows, column:widths.length } };
  }
}

function buildWorkbookCover(ws, title, subtitle, colCount){
  ws.mergeCells(1,1,1,colCount);
  ws.getCell(1,1).value = title;
  ws.getCell(1,1).font = { size:18, bold:true, color:{ argb:'FFE8C86A' }, name:'Microsoft YaHei' };
  ws.getCell(1,1).fill = { type:'pattern', pattern:'solid', fgColor:{ argb:'FF0B1019' } };
  ws.getCell(1,1).alignment = { vertical:'middle', horizontal:'left' };
  ws.mergeCells(2,1,2,colCount);
  ws.getCell(2,1).value = subtitle;
  ws.getCell(2,1).font = { size:10, color:{ argb:'FF8EA0B3' }, name:'Microsoft YaHei' };
  ws.getCell(2,1).fill = { type:'pattern', pattern:'solid', fgColor:{ argb:'FF0F1620' } };
}

function exportTeamReportExcel(){
  if(!_trData) return exportCurrentPageTableGeneric();
  const wb = new ExcelJS.Workbook();
  wb.creator = '率土战场指挥台';
  wb.created = new Date();
  const ws = wb.addWorksheet('团数据');
  const dim = document.getElementById('tr-dim')?.value||'group';
  const isGroup = dim==='group';
  const periodName = {today:'今日',yesterday:'昨日',week:'本周',lastweek:'上周',all:'全部'}[_trPeriod]||'全部';
  const s = _trData.summary || {};
  const rows = _trData.rows || [];
  buildWorkbookCover(ws, `团数据统计报告（${isGroup?'按分组':'按成员'}）`, `统计周期：${periodName}  ·  导出时间：${new Date().toLocaleString('zh-CN',{hour12:false})}`, isGroup ? 12 : 11);
  ws.addRow([]);
  const statLabelRow = ws.addRow(['总战报','胜率','参战人数','平局','攻城场次','总功勋']);
  const statValueRow = ws.addRow([s.total_battles||0, (s.win_rate||0)/100, s.total_players||0, s.total_draws||0, s.total_city||0, s.total_gongxun||0]);
  statLabelRow.eachCell(c=>applyExcelHeaderStyle(c,'FF111A28','FFE8C86A',10));
  statValueRow.eachCell((c,idx)=>{ applyExcelBodyStyle(c,'FF101929', idx===2?'center':'right'); c.numFmt = idx===2 ? '0%' : '#,##0'; });
  ws.addRow([]);
  const headers = isGroup
    ? ['排名','分组','人数','战报','胜','败','平','胜率','攻城','总功勋','平均武勋','平均势力值']
    : ['排名','成员','分组','战报','胜','败','平','胜率','攻城','功勋','势力值'];
  const headerRow = ws.addRow(headers);
  headerRow.eachCell(c=>applyExcelHeaderStyle(c));
  rows.forEach((r,i)=>{
    const row = ws.addRow(isGroup
      ? [i+1, r.name||'', r.player_cnt||0, r.battles||0, r.wins||0, r.loses||0, r.draws||0, (Number(r.win_rate||0))/100, r.city_battles||0, r.total_gongxun||0, Math.round(Number(r.avg_gongxun||0)), Math.round(Number(r.avg_power||0))]
      : [i+1, r.name||'', r.group_name||'', r.battles||0, r.wins||0, r.loses||0, r.draws||0, (Number(r.win_rate||0))/100, r.city_battles||0, r.total_gongxun||0, r.power||0]
    );
    row.eachCell((c,col)=>{
      applyExcelBodyStyle(c, i%2===0?'FF0D1420':'FF101929', [2,3].includes(col)?'left':'right');
      if(col===8) c.numFmt = '0%';
      else if(col>=1) c.numFmt = col===2 || col===3 ? '@' : '#,##0';
    });
    row.getCell(2).font = { ...row.getCell(2).font, bold:true, color:{ argb:'FFE8C86A' } };
    row.getCell(8).fill = { type:'pattern', pattern:'solid', fgColor:{ argb:(Number(r.win_rate||0)>=60?'FF163020':Number(r.win_rate||0)>=40?'FF3A3318':'FF301818') } };
  });
  finishWorksheetLayout(ws, isGroup?[8,16,10,10,8,8,8,10,10,14,12,14]:[8,14,12,10,8,8,8,10,10,14,14], 6);
  return wb.xlsx.writeBuffer().then(buf=>{
    const blob = new Blob([buf], { type:'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' });
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = `团数据_${periodName}_${new Date().toISOString().slice(0,10)}.xlsx`;
    a.click();
    URL.revokeObjectURL(a.href);
  });
}

function exportTeamUsersExcel(){
  if(!_tuData?.length) return exportCurrentPageTableGeneric();
  const wb = new ExcelJS.Workbook();
  wb.creator = '率土战场指挥台';
  wb.created = new Date();
  const ws = wb.addWorksheet('同盟成员');
  const totalPower = _tuData.reduce((a,b)=>a+(b.power||0),0);
  const totalWu = _tuData.reduce((a,b)=>a+(b.wuxun||0),0);
  buildWorkbookCover(ws, '同盟成员总览', `导出时间：${new Date().toLocaleString('zh-CN',{hour12:false})}`, 9);
  ws.addRow([]);
  const statLabelRow = ws.addRow(['同盟人数','总势力值','总武勋']);
  const statValueRow = ws.addRow([_tuData.length, totalPower, totalWu]);
  statLabelRow.eachCell(c=>applyExcelHeaderStyle(c,'FF111A28','FFE8C86A',10));
  statValueRow.eachCell(c=>{ applyExcelBodyStyle(c,'FF101929','right'); c.numFmt='#,##0'; });
  ws.addRow([]);
  const headerRow = ws.addRow(['成员','UID','职位','势力值','武勋','周贡献','总贡献','分组','加入日期']);
  headerRow.eachCell(c=>applyExcelHeaderStyle(c));
  _tuData.forEach((r,i)=>{
    const row = ws.addRow([r.name||'', r.uid||0, POS_MAP[r.pos]||('职位'+(r.pos||0)), r.power||0, r.wuxun||0, r.contribute_week||0, r.contribute_total||0, r.group_name||'未分组', r.join_time ? new Date(r.join_time*1000).toLocaleDateString('zh-CN') : '' ]);
    row.eachCell((c,col)=>{ applyExcelBodyStyle(c, i%2===0?'FF0D1420':'FF101929', [1,3,8,9].includes(col)?'left':'right'); if([2,4,5,6,7].includes(col)) c.numFmt='#,##0'; });
    row.getCell(1).font = { ...row.getCell(1).font, bold:true, color:{ argb:'FFE8C86A' } };
    row.getCell(4).fill = { type:'pattern', pattern:'solid', fgColor:{ argb:'FF2A2410' } };
    row.getCell(5).fill = { type:'pattern', pattern:'solid', fgColor:{ argb:'FF102430' } };
  });
  finishWorksheetLayout(ws, [14,14,12,14,14,12,12,12,12], 6);
  return wb.xlsx.writeBuffer().then(buf=>{
    const blob = new Blob([buf], { type:'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' });
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = `同盟成员_${new Date().toISOString().slice(0,10)}.xlsx`;
    a.click();
    URL.revokeObjectURL(a.href);
  });
}

function exportUnionListExcel(){
  if(!_ulData?.length) return exportCurrentPageTableGeneric();
  const wb = new ExcelJS.Workbook();
  wb.creator = '率土战场指挥台';
  wb.created = new Date();
  const ws = wb.addWorksheet('联盟列表');
  const totalPower = _ulData.reduce((s,r)=>s+(r.power||0),0);
  const totalMember = _ulData.reduce((s,r)=>s+(r.total_member||0),0);
  buildWorkbookCover(ws, '联盟列表总览', `导出时间：${new Date().toLocaleString('zh-CN',{hour12:false})}`, 9);
  ws.addRow([]);
  const statLabelRow = ws.addRow(['联盟数','总势力值','总人数']);
  const statValueRow = ws.addRow([_ulData.length, totalPower, totalMember]);
  statLabelRow.eachCell(c=>applyExcelHeaderStyle(c,'FF111A28','FFE8C86A',10));
  statValueRow.eachCell(c=>{ applyExcelBodyStyle(c,'FF101929','right'); c.numFmt='#,##0'; });
  ws.addRow([]);
  const headerRow = ws.addRow(['排名','联盟','等级','势力值','人数','占领值','NPC城','区域','更新时间']);
  headerRow.eachCell(c=>applyExcelHeaderStyle(c));
  _ulData.forEach((r,i)=>{
    const row = ws.addRow([r.rank||i+1, r.name||'', r.level||0, r.power||0, r.total_member||0, r.occupy_city_value||0, r.total_npc_city||0, r.region||'', r.updated_at ? r.updated_at.slice(5,16) : '' ]);
    row.eachCell((c,col)=>{ applyExcelBodyStyle(c, i%2===0?'FF0D1420':'FF101929', [2,8,9].includes(col)?'left':'right'); if([1,3,4,5,6,7].includes(col)) c.numFmt='#,##0'; });
    row.getCell(1).font = { ...row.getCell(1).font, bold:true, color:{ argb:i<3?'FFE8C86A':'FFD8D2C0' } };
    row.getCell(2).font = { ...row.getCell(2).font, bold:true, color:{ argb:'FFE8C86A' } };
    row.getCell(4).fill = { type:'pattern', pattern:'solid', fgColor:{ argb:'FF2A2410' } };
  });
  finishWorksheetLayout(ws, [8,18,8,14,10,10,10,12,12], 6);
  return wb.xlsx.writeBuffer().then(buf=>{
    const blob = new Blob([buf], { type:'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' });
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = `联盟列表_${new Date().toISOString().slice(0,10)}.xlsx`;
    a.click();
    URL.revokeObjectURL(a.href);
  });
}

function exportBattlesAllExcel(){
  const pageEl = document.getElementById('tab10');
  const tbody = pageEl?.querySelector('#ba-body');
  if(!tbody || !tbody.children.length) return exportCurrentPageTableGeneric();
  const wb = new ExcelJS.Workbook();
  wb.creator = '率土战场指挥台';
  wb.created = new Date();
  const ws = wb.addWorksheet('全部战报');
  buildWorkbookCover(ws, '全部战报导出', `导出时间：${new Date().toLocaleString('zh-CN',{hour12:false})}`, 7);
  ws.addRow([]);
  const filterDesc = [
    `玩家：${document.getElementById('ba-player')?.value||'全部'}`,
    `联盟：${document.getElementById('ba-union')?.value||'全部'}`,
    `结果：${document.getElementById('ba-result')?.value||'全部'}`,
    `类型：${document.getElementById('ba-ftype')?.value||'全部'}`,
    `周期：${document.getElementById('ba-period')?.value||'全部'}`
  ].join('  ·  ');
  ws.mergeCells(4,1,4,7);
  ws.getCell(4,1).value = filterDesc;
  ws.getCell(4,1).font = { size:10, color:{ argb:'FF8EA0B3' }, name:'Microsoft YaHei' };
  ws.getCell(4,1).fill = { type:'pattern', pattern:'solid', fgColor:{ argb:'FF0F1620' } };
  const headerRow = ws.addRow(['时间','攻方','攻方武将','结果','守方武将','守方','查看']);
  headerRow.eachCell(c=>applyExcelHeaderStyle(c));
  [...tbody.querySelectorAll('tr')].forEach((tr,i)=>{
    const cells = [...tr.querySelectorAll('td')].map(td=>td.innerText.trim().replace(/\n+/g,' / '));
    const row = ws.addRow(cells);
    row.eachCell((c,col)=>{ applyExcelBodyStyle(c, i%2===0?'FF0D1420':'FF101929', [1,2,3,5,6].includes(col)?'left':'center'); });
    if(String(cells[3]||'').includes('胜')) row.getCell(4).font = { ...row.getCell(4).font, bold:true, color:{ argb:'FF46B06E' } };
    else if(String(cells[3]||'').includes('败')) row.getCell(4).font = { ...row.getCell(4).font, bold:true, color:{ argb:'FFE05050' } };
    else row.getCell(4).font = { ...row.getCell(4).font, bold:true, color:{ argb:'FFC8A044' } };
  });
  finishWorksheetLayout(ws, [20,18,28,10,28,18,10], 5);
  return wb.xlsx.writeBuffer().then(buf=>{
    const blob = new Blob([buf], { type:'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' });
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = `全部战报_${new Date().toISOString().slice(0,10)}.xlsx`;
    a.click();
    URL.revokeObjectURL(a.href);
  });
}

async function exportCurrentPageTableGeneric(){
  if(!ensureExportLibraries()) return;
  const pageEl = getCurrentPageEl();
  const snap = getPageExportSnapshot(pageEl);
  if(!snap || !snap.tables.length){
    showToast('当前页面没有可导出的表格','var(--red)');
    return;
  }
  const wb = new ExcelJS.Workbook();
  wb.creator = '率土战场指挥台';
  wb.created = new Date();
  snap.tables.forEach((table, idx)=>{
    const ws = wb.addWorksheet((table.title || `表格${idx+1}`).slice(0,31));
    const title = snap.title || '页面导出';
    const colCount = Math.max(1, table.headers.length || (table.rows[0]?.length || 1));
    buildWorkbookCover(ws, title, `${table.title || `表格 ${idx+1}`}  ·  导出时间：${new Date().toLocaleString('zh-CN', { hour12:false })}`, colCount);
    if(snap.stats.length){
      ws.addRow([]);
      const labels = snap.stats.map(s=>s.label);
      const values = snap.stats.map(s=>s.value);
      const r1 = ws.addRow(labels);
      const r2 = ws.addRow(values);
      r1.eachCell(c=>applyExcelHeaderStyle(c,'FF111A28','FFE8C86A',10));
      r2.eachCell(c=>applyExcelBodyStyle(c,'FF101929','right'));
      ws.addRow([]);
    } else {
      ws.addRow([]);
    }
    const headerRow = ws.addRow(table.headers);
    headerRow.eachCell(c=>applyExcelHeaderStyle(c));
    table.rows.forEach((row, ridx)=>{
      const excelRow = ws.addRow(row);
      excelRow.eachCell(cell=>applyExcelBodyStyle(cell, ridx % 2 === 0 ? 'FF0D1420' : 'FF101929'));
    });
    const widths = Array.from({length: colCount}, (_, i)=>Math.min(40, Math.max(12, Math.max(...ws.getColumn(i+1).values.filter(Boolean).map(v=>String(v).length)) + 2)));
    finishWorksheetLayout(ws, widths, snap.stats.length ? 6 : 4);
  });
  const buf = await wb.xlsx.writeBuffer();
  const blob = new Blob([buf], { type:'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' });
  const a = document.createElement('a');
  a.href = URL.createObjectURL(blob);
  a.download = `${String(snap.title || '页面导出').replace(/[\\/:*?"<>|]/g,'_')}_${new Date().toISOString().slice(0,10)}.xlsx`;
  a.click();
  URL.revokeObjectURL(a.href);
}

async function exportCurrentPageTable(){
  const pageEl = getCurrentPageEl();
  const id = pageEl?.id || '';
  if(id === 'tab23') return exportTeamReportExcel();
  if(id === 'tab14') return exportTeamUsersExcel();
  if(id === 'tab18') return exportUnionListExcel();
  if(id === 'tab10') return exportBattlesAllExcel();
  return exportCurrentPageTableGeneric();
}

function injectPageExportButtons(){
  document.querySelectorAll('.page').forEach(page=>{
    const head = page.querySelector('.tbl-head');
    if(!head || head.dataset.exportInjected === '1') return;
    let controls = head.querySelector('.tbl-controls');
    if(!controls){
      controls = document.createElement('div');
      controls.className = 'tbl-controls';
      head.appendChild(controls);
    }
    const pdfBtn = document.createElement('button');
    pdfBtn.className = 'btn';
    pdfBtn.textContent = '📄 导出PDF';
    pdfBtn.onclick = ()=>exportCurrentPagePDF();
    const tableBtn = document.createElement('button');
    tableBtn.className = 'btn';
    tableBtn.textContent = '📋 导出表格';
    tableBtn.onclick = ()=>exportCurrentPageTable();
    controls.appendChild(pdfBtn);
    controls.appendChild(tableBtn);
    head.dataset.exportInjected = '1';
  });
}

// 添加样式
(function(){
  const s = document.createElement('style');
  s.textContent = `.msg-filter-btn{padding:4px 14px;background:var(--panel2);border:1px solid var(--border);color:var(--text2);border-radius:3px;cursor:pointer;font-size:.78rem;transition:all .2s}
.msg-filter-btn.active{background:#1a2535;color:var(--gold);border-color:var(--gold)}`;
  document.head.appendChild(s);
})();

function filterMsg(kind){
  _msgFilter = kind;
  document.querySelectorAll('.msg-filter-btn').forEach(b=>b.classList.remove('active'));
  const btnMap = {all:'msg-btn-all', chat:'msg-btn-chat', battle_notice:'msg-btn-notice'};
  const btn = document.getElementById(btnMap[kind]);
  if(btn) btn.classList.add('active');
  renderMsgList();
}

function clearMsgList(){
  _msgList = [];
  _msgChatCount = 0;
  _msgNoticeCount = 0;
  document.getElementById('msg-chat-count').textContent = '0';
  document.getElementById('msg-notice-count').textContent = '0';
  renderMsgList();
}

function renderMsgList(){
  const b = document.getElementById('msg-body');
  if(!b) return;
  const q = (document.getElementById('msg-search')||{}).value||'';
  const filtered = _msgList.filter(m=>{
    if(_msgFilter !== 'all' && m.kind !== _msgFilter) return false;
    if(q){
      const s = JSON.stringify(m).toLowerCase();
      if(!s.includes(q.toLowerCase())) return false;
    }
    return true;
  });
  b.innerHTML = filtered.slice(0,300).map(m=>{
    if(m.kind === 'chat'){
      return `<tr>
        <td style='color:var(--text2);font-size:.68rem;white-space:nowrap'>${esc(m.time_str||'')}</td>
        <td><span class='badge' style='background:#1a1a2e;color:var(--cyan)'>💬 聊天</span></td>
        <td style='color:var(--gold)'><b>${esc(m.sender||'')}</b></td>
        <td style='color:var(--text2);font-size:.72rem'>${esc(m.union||'')}</td>
        <td>${esc(m.text||'')}</td>
      </tr>`;
    } else {
      const resClass = m.result===1||m.result===7||m.result===11?'badge-win':m.result===2||m.result===6||m.result===12?'badge-lose':'badge-draw';
      return `<tr>
        <td style='color:var(--text2);font-size:.68rem;white-space:nowrap'>${esc(m.time_str||'')}</td>
        <td><span class='badge' style='background:#1a2010;color:var(--green)'>⚔ 战斗</span></td>
        <td style='color:var(--red)'><b>${esc(m.atk_name||'')}</b></td>
        <td style='color:var(--text2);font-size:.72rem'>${esc(m.def_union||'')}</td>
        <td><span class='badge ${resClass}'>${esc(m.result_desc||'')}</span>
          <span style='color:var(--text2);font-size:.7rem'> wx=${fmt(m.atk_gongxun||0)}</span>
          <span style='color:var(--text2);font-size:.7rem'> ${esc(m.fight_type_name||'')} wid=${m.wid||''}</span>
        </td>
      </tr>`;
    }
  }).join('');
}

function onMsg834(evt){
  const d = evt.data||{};
  if(evt.type === 'chat_834'){
    _msgChatCount++;
    document.getElementById('msg-chat-count').textContent = _msgChatCount;
    _msgList.unshift({kind:'chat', ...d});
  } else if(evt.type === 'battle_notice'){
    _msgNoticeCount++;
    const el=document.getElementById('msg-notice-count');
    if(el) el.textContent = _msgNoticeCount;
    _msgList.unshift({kind:'battle_notice', ...d});
  }
  if(_msgList.length > 500) _msgList.length = 500;
  // 只有当前在 tab21 时才实时刷新
  if(document.getElementById('tab21')&&document.getElementById('tab21').classList.contains('active')){
    renderMsgList();
  }
}

/*
 * Disabled duplicate module.
 * The export and message functions below are byte-for-byte duplicates of the
 * active definitions above. They remain temporarily as commented migration
 * context and are excluded from runtime execution.
 */
/*
function exportTeamReportPretty(){
  return exportTeamReportPDF();
}

function exportTeamReportPDF(){
  const w = openTeamReportExportWindow();
  if(!w) return;
  setTimeout(()=>w.print(), 350);
}

function exportTeamReportLongImage(){
  const extraScript = `<script>
    document.body.classList.add('long-shot');
    const bar = document.createElement('div');
    bar.className = 'action-bar';
    bar.innerHTML = '<button class="action-btn" id="save-long-shot">保存长图</button><button class="action-btn secondary" onclick="window.print()">打印 / 另存PDF</button>';
    const shell = document.querySelector('.page-shell') || document.body;
    shell.insertBefore(bar, shell.firstChild);
    function downloadLongShot(){
      try{
        const page = document.getElementById('team-report-page');
        const shellEl = document.querySelector('.page-shell') || page;
        const oldBg = document.body.style.background;
        document.body.style.background = '#f4ecde';
        const svg = '<svg xmlns="http://www.w3.org/2000/svg" width="' + shellEl.scrollWidth + '" height="' + shellEl.scrollHeight + '">' +
          '<foreignObject width="100%" height="100%">' + new XMLSerializer().serializeToString(shellEl) + '</foreignObject>' +
          '</svg>';
        const blob = new Blob([svg], {type:'image/svg+xml;charset=utf-8'});
        const url = URL.createObjectURL(blob);
        const img = new Image();
        img.onload = function(){
          const canvas = document.createElement('canvas');
          canvas.width = shellEl.scrollWidth;
          canvas.height = shellEl.scrollHeight;
          const ctx = canvas.getContext('2d');
          ctx.fillStyle = '#f4ecde';
          ctx.fillRect(0,0,canvas.width,canvas.height);
          ctx.drawImage(img,0,0);
          URL.revokeObjectURL(url);
          document.body.style.background = oldBg;
          const a = document.createElement('a');
          a.href = canvas.toDataURL('image/png');
          a.download = '团数据长图_' + new Date().toISOString().slice(0,10) + '.png';
          a.click();
        };
        img.onerror = function(){
          URL.revokeObjectURL(url);
          alert('当前浏览器不支持直接生成长图，请改用“打印 / 另存PDF”。');
        };
        img.src = url;
      }catch(e){
        alert('生成长图失败：' + (e && e.message ? e.message : e));
      }
    }
    document.getElementById('save-long-shot').addEventListener('click', downloadLongShot);
  <\/script>`;
  const w = openTeamReportExportWindow(extraScript);
  if(!w) return;
}

function exportTeamReportCSV(){
  if(!_trData) return;
  const dim = document.getElementById('tr-dim')?.value||'group';
  const isGroup = dim==='group';
  const headers = isGroup
    ? ['#','分组','人数','战报','胜','败','平','胜率%','攻城','总功勋','平均武勋','平均势力值']
    : ['#','成员','分组','战报','胜','败','平','胜率%','攻城','功勋','势力值'];
  const rows = (_trData.rows||[]).map((r,i)=> isGroup
    ? [i+1, r.name||'', r.player_cnt||0, r.battles, r.wins, r.loses, r.draws||0, r.win_rate, r.city_battles||0, r.total_gongxun||0, r.avg_gongxun||0, r.avg_power||0]
    : [i+1, r.name||'', r.group_name||'', r.battles, r.wins, r.loses, r.draws||0, r.win_rate, r.city_battles||0, r.total_gongxun||0, r.power||0]
  );
  const csv = [headers,...rows].map(r=>r.map(c=>`"${String(c).replace(/"/g,'""')}"`).join(',')).join('\n');
  const blob = new Blob(['\uFEFF'+csv],{type:'text/csv;charset=utf-8'});
  const a = document.createElement('a');
  a.href = URL.createObjectURL(blob);
  a.download = `团数据_${_trPeriod}_${new Date().toISOString().slice(0,10)}.csv`;
  a.click();
}

function getCurrentPageEl(){
  return document.querySelector('.page.active') || document.querySelector('.page');
}

function getCurrentPageTitle(pageEl){
  if(!pageEl) return '页面';
  const headTitle = pageEl.querySelector('.tbl-head h3');
  if(headTitle && headTitle.textContent.trim()) return headTitle.textContent.trim();
  const navBtn = document.querySelector(`nav button[onclick*="${pageEl.id?.replace('tab','')}"].active`);
  if(navBtn && navBtn.textContent.trim()) return navBtn.textContent.trim();
  return pageEl.id || '页面';
}

function buildPageExportHtml(pageEl, options={}){
  if(!pageEl) return '';
  const title = options.title || getCurrentPageTitle(pageEl);
  const nowText = new Date().toLocaleString('zh-CN', { hour12:false });
  const clone = pageEl.cloneNode(true);
  clone.querySelectorAll('button').forEach(btn=>btn.remove());
  clone.querySelectorAll('[onclick]').forEach(el=>el.removeAttribute('onclick'));
  clone.querySelectorAll('input').forEach(inp=>{
    const span = document.createElement('span');
    span.textContent = inp.value || inp.placeholder || '';
    span.style.cssText = 'display:inline-block;min-width:48px;padding:6px 10px;border:1px solid #d6c8ad;border-radius:999px;background:#fbf6ed;color:#2a241c;box-shadow:inset 0 1px 0 rgba(255,255,255,.55);';
    inp.replaceWith(span);
  });
  clone.querySelectorAll('select').forEach(sel=>{
    const span = document.createElement('span');
    const text = sel.options && sel.selectedIndex >= 0 ? sel.options[sel.selectedIndex].text : '';
    span.textContent = text;
    span.style.cssText = 'display:inline-block;min-width:48px;padding:6px 10px;border:1px solid #d6c8ad;border-radius:999px;background:#fbf6ed;color:#2a241c;box-shadow:inset 0 1px 0 rgba(255,255,255,.55);';
    sel.replaceWith(span);
  });
  clone.querySelectorAll('.tbl-scroll,.feed').forEach(el=>{
    el.style.maxHeight = 'none';
    el.style.overflow = 'visible';
  });
  clone.querySelectorAll('thead th').forEach(th=>th.style.position='static');
  const content = clone.innerHTML;
  return `<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8">
<title>${title}</title>
<style>
  :root{
    --paper:#f8f1e4;--paper-soft:#f3e8d6;--paper-soft-2:#eadcc4;--paper-edge:#e1d1b4;--line:#d7c3a0;
    --text:#2a241c;--muted:#7a6956;--title:#1b1510;--accent:#8f6a2a;--accent-soft:#e7d8ba;
    --green:#41664d;--red:#9a4d41;--blue:#4e6a8d;--cyan:#557978;--purple:#705c8d;
  }
  @page{size:A4 landscape;margin:12mm;}
  body{
    margin:0;padding:30px;color:var(--text);font-family:'SimSun','宋体',serif;
    background:
      radial-gradient(circle at top left, rgba(143,106,42,.08), transparent 28%),
      radial-gradient(circle at bottom right, rgba(122,105,86,.08), transparent 24%),
      linear-gradient(180deg,#fbf5ea 0%,#f4ecde 100%);
    -webkit-print-color-adjust:exact;print-color-adjust:exact;
  }
  .page-shell{
    max-width:1520px;margin:0 auto;padding:18px 18px 10px;
    background:linear-gradient(180deg,rgba(255,251,245,.94) 0%,rgba(248,241,228,.96) 100%);
    border:1px solid var(--paper-edge);border-radius:18px;
    box-shadow:0 18px 40px rgba(120,90,45,.10), inset 0 1px 0 rgba(255,255,255,.55);
    position:relative;overflow:hidden;
  }
  .page-shell::before{
    content:'';position:absolute;inset:10px;border:1px solid rgba(143,106,42,.18);border-radius:12px;pointer-events:none;
  }
  .export-header{
    position:relative;display:flex;justify-content:space-between;align-items:flex-end;
    border-bottom:2px solid rgba(143,106,42,.45);padding:4px 8px 16px;margin-bottom:22px;gap:12px;
  }
  .export-header::after{
    content:'';position:absolute;left:0;bottom:-2px;width:140px;height:4px;border-radius:999px;
    background:linear-gradient(90deg,var(--accent),rgba(143,106,42,0));
  }
  .export-title{font-size:30px;letter-spacing:.14em;color:var(--title);font-weight:700;}
  .export-subtitle{margin-top:6px;color:var(--muted);font-size:13px;letter-spacing:.04em;}
  .export-meta{font-size:13px;color:var(--muted);text-align:right;line-height:1.8;}
  .page{display:block !important;animation:none !important;position:relative;z-index:1;}
  .cards-row{display:flex;gap:14px;margin-bottom:20px;flex-wrap:wrap;}
  .stat-card{
    flex:1;min-width:148px;background:linear-gradient(180deg,#fffaf1 0%,#f4ead9 100%);
    border:1px solid var(--line);border-radius:16px;padding:16px 14px;text-align:center;
    box-shadow:0 10px 20px rgba(143,106,42,.08), inset 0 1px 0 rgba(255,255,255,.55);
  }
  .stat-card .val{font-size:30px;color:var(--title);line-height:1.1;font-weight:700;}
  .stat-card .lbl{margin-top:6px;font-size:12px;color:var(--muted);letter-spacing:.14em;}
  .tbl-wrap{
    background:rgba(255,250,242,.94);border:1px solid var(--line);border-radius:16px;overflow:hidden;
    box-shadow:0 10px 24px rgba(86,65,33,.06), inset 0 1px 0 rgba(255,255,255,.55);
  }
  .tbl-head{
    display:flex;align-items:center;justify-content:space-between;padding:13px 18px;border-bottom:1px solid var(--line);
    background:linear-gradient(180deg,#f7ecdc 0%,#efdfc5 100%);
  }
  .tbl-head h3{font-size:.92rem;letter-spacing:.12em;color:var(--title);margin:0;}
  .tbl-controls{display:flex;gap:8px;align-items:center;flex-wrap:wrap;color:var(--muted);}
  .tbl-scroll,.feed{max-height:none !important;overflow:visible !important;}
  table{width:100%;border-collapse:collapse;font-size:14px;background:transparent;}
  thead th{
    padding:11px 12px;background:#efe2cc;color:var(--muted);text-align:left;border-bottom:1px solid var(--line);
    position:static !important;font-weight:600;
  }
  tbody td{padding:10px 12px;color:var(--text);border-bottom:1px solid rgba(215,195,160,.55);white-space:nowrap;}
  tbody tr:nth-child(odd){background:rgba(255,251,245,.82);}
  tbody tr:nth-child(even){background:rgba(248,239,224,.66);}
  .badge{
    display:inline-block;padding:2px 8px;border-radius:999px;font-size:12px;
    background:var(--accent-soft);color:var(--text);border:1px solid #d7c19a;
  }
  .feed-item,.bar-row{break-inside:avoid;}
  .bar-track{background:#e8dac0 !important;border:1px solid var(--line) !important;border-radius:999px !important;}
  .bar-fill{border-radius:999px !important;}
  .prog{background:#e8dac0 !important;}
  .export-footer{margin-top:16px;color:var(--muted);font-size:12px;text-align:right;}
  .rank-1,.rank-2,.rank-3{color:var(--title) !important;font-weight:700;}
  .btn{border-color:var(--line) !important;color:var(--text) !important;background:#f8f0e2 !important;}
  tr[style*='background:#0d1420'],tr[style*='background:#101925'],tr[style*='background:var(--panel2)'],tr[style*='background:#121c2a'],tr[style*='background:#101929'],tr[style*='background:#111a28']{background:rgba(248,239,224,.66) !important;}
  td[style*='background:#0d1420'],td[style*='background:#101925'],td[style*='background:var(--panel2)'],td[style*='background:#121c2a'],td[style*='background:#101929'],td[style*='background:#111a28'],span[style*='background:#0d1520'],span[style*='background:#0d1820'],span[style*='background:#111a28'],span[style*='background:#172232'],div[style*='background:#0d1520'],div[style*='background:#0d1820'],div[style*='background:#111a28'],div[style*='background:#172232']{background:var(--accent-soft) !important;color:var(--text) !important;border-color:#d7c19a !important;}
  img{filter:saturate(.88) contrast(.96);}
  [style*='box-shadow']{box-shadow:none !important;}
  [style*='text-shadow']{text-shadow:none !important;}
  [style*='color:transparent']{color:var(--text) !important;}
  [style*='opacity:0']{opacity:1 !important;}
  [style*='var(--gold)'],[style*='var(--gold2)']{color:var(--accent) !important;}
  [style*='var(--green)']{color:var(--green) !important;}
  [style*='var(--red)']{color:var(--red) !important;}
  [style*='var(--blue)']{color:var(--blue) !important;}
  [style*='var(--cyan)']{color:var(--cyan) !important;}
  [style*='var(--purple)']{color:var(--purple) !important;}
  [style*='var(--text2)'],[style*='color:#7a8a9a'],[style*='color:#66788b'],[style*='color:#8ea0b3']{color:var(--muted) !important;}
  [style*='color:var(--text)'],[style*='color:#d4cfc0'],[style*='color:#fff'],[style*='color:white']{color:var(--text) !important;}
  input,select,span[style*='border:1px solid #cfd6de']{background:#fbf6ed !important;color:var(--text) !important;border-color:#d6c8ad !important;}
  @media print{
    body{padding:0;background:#fff !important;color:var(--text) !important;}
    .page-shell{max-width:none;box-shadow:none;border-radius:0;}
    .tbl-wrap,.stat-card{break-inside:avoid;box-shadow:none !important;}
    .tbl-wrap,.stat-card,table{box-shadow:none !important;}
  }
</style>
</head>
<body>
  <div class="page-shell">
    <div class="export-header">
      <div>
        <div class="export-title">${title}</div>
        <div class="export-subtitle">当前页面导出（米色古风战报）</div>
      </div>
      <div class="export-meta">
        <div>导出时间：${nowText}</div>
      </div>
    </div>
    ${content}
    <div class="export-footer">率土战场指挥台 · 页面导出</div>
  </div>
</body>
</html>`;
}

function openExportWindowFromHtml(html){
  if(!html) return null;
  const w = window.open('', '_blank');
  if(!w){ showToast('请允许弹窗后再导出','var(--red)'); return null; }
  w.document.open();
  w.document.write(html);
  w.document.close();
  return w;
}

function getPageExportSnapshot(pageEl){
  if(!pageEl) return null;
  const title = getCurrentPageTitle(pageEl);
  const tables = [...pageEl.querySelectorAll('table')].map((table, idx)=>{
    const wrap = table.closest('.tbl-wrap') || table.parentElement;
    const h3 = wrap?.querySelector('.tbl-head h3');
    const secTitle = h3?.textContent?.trim() || `表格 ${idx+1}`;
    const headers = [...table.querySelectorAll('thead th')].map(th=>th.innerText.trim());
    const rows = [...table.querySelectorAll('tbody tr')].map(tr=>[...tr.querySelectorAll('td')].map(td=>td.innerText.trim().replace(/\n+/g,' / ')));
    return { title: secTitle, headers, rows };
  });
  const stats = [...pageEl.querySelectorAll('.stat-card')].map(card=>({
    value: card.querySelector('.val')?.innerText?.trim() || '',
    label: card.querySelector('.lbl')?.innerText?.trim() || ''
  })).filter(x=>x.value || x.label);
  return { title, tables, stats };
}

function ensureExportLibraries(){
  const hasExcel = typeof ExcelJS !== 'undefined';
  const hasPdf = !!(window.jspdf && window.jspdf.jsPDF);
  if(!hasExcel || !hasPdf){
    showToast('导出库加载中，请稍后再试','var(--red)');
    return false;
  }
  return true;
}

async function exportCurrentPagePDF(){
  const pageEl = getCurrentPageEl();
  const html = buildPageExportHtml(pageEl);
  const w = openExportWindowFromHtml(html);
  if(!w) return;
  setTimeout(()=>w.print(), 350);
}

function applyExcelHeaderStyle(cell, fill='FF121D2C', color='FF8EA0B3', size=11){
  cell.font = { bold:true, color:{ argb:color }, name:'Microsoft YaHei', size };
  cell.fill = { type:'pattern', pattern:'solid', fgColor:{ argb:fill } };
  cell.alignment = { vertical:'middle', horizontal:'center', wrapText:true };
  cell.border = { top:{style:'thin',color:{argb:'FF1D2A3C'}}, left:{style:'thin',color:{argb:'FF1D2A3C'}}, bottom:{style:'thin',color:{argb:'FF1D2A3C'}}, right:{style:'thin',color:{argb:'FF1D2A3C'}} };
}

function applyExcelBodyStyle(cell, fill='FF0F1620', align='left'){
  cell.font = { color:{ argb:'FFD8D2C0' }, name:'Microsoft YaHei', size:10 };
  cell.fill = { type:'pattern', pattern:'solid', fgColor:{ argb:fill } };
  cell.alignment = { vertical:'middle', horizontal:align, wrapText:true };
  cell.border = { top:{style:'thin',color:{argb:'FF172232'}}, left:{style:'thin',color:{argb:'FF172232'}}, bottom:{style:'thin',color:{argb:'FF172232'}}, right:{style:'thin',color:{argb:'FF172232'}} };
}

function finishWorksheetLayout(ws, widths, frozenRows=5){
  ws.columns = widths.map(w=>({ width:w }));
  ws.views = [{ state:'frozen', ySplit:frozenRows }];
  if(ws.rowCount >= frozenRows){
    ws.autoFilter = { from: { row:frozenRows, column:1 }, to: { row:frozenRows, column:widths.length } };
  }
}

function buildWorkbookCover(ws, title, subtitle, colCount){
  ws.mergeCells(1,1,1,colCount);
  ws.getCell(1,1).value = title;
  ws.getCell(1,1).font = { size:18, bold:true, color:{ argb:'FFE8C86A' }, name:'Microsoft YaHei' };
  ws.getCell(1,1).fill = { type:'pattern', pattern:'solid', fgColor:{ argb:'FF0B1019' } };
  ws.getCell(1,1).alignment = { vertical:'middle', horizontal:'left' };
  ws.mergeCells(2,1,2,colCount);
  ws.getCell(2,1).value = subtitle;
  ws.getCell(2,1).font = { size:10, color:{ argb:'FF8EA0B3' }, name:'Microsoft YaHei' };
  ws.getCell(2,1).fill = { type:'pattern', pattern:'solid', fgColor:{ argb:'FF0F1620' } };
}

function exportTeamReportExcel(){
  if(!_trData) return exportCurrentPageTableGeneric();
  const wb = new ExcelJS.Workbook();
  wb.creator = '率土战场指挥台';
  wb.created = new Date();
  const ws = wb.addWorksheet('团数据');
  const dim = document.getElementById('tr-dim')?.value||'group';
  const isGroup = dim==='group';
  const periodName = {today:'今日',yesterday:'昨日',week:'本周',lastweek:'上周',all:'全部'}[_trPeriod]||'全部';
  const s = _trData.summary || {};
  const rows = _trData.rows || [];
  buildWorkbookCover(ws, `团数据统计报告（${isGroup?'按分组':'按成员'}）`, `统计周期：${periodName}  ·  导出时间：${new Date().toLocaleString('zh-CN',{hour12:false})}`, isGroup ? 12 : 11);
  ws.addRow([]);
  const statLabelRow = ws.addRow(['总战报','胜率','参战人数','平局','攻城场次','总功勋']);
  const statValueRow = ws.addRow([s.total_battles||0, (s.win_rate||0)/100, s.total_players||0, s.total_draws||0, s.total_city||0, s.total_gongxun||0]);
  statLabelRow.eachCell(c=>applyExcelHeaderStyle(c,'FF111A28','FFE8C86A',10));
  statValueRow.eachCell((c,idx)=>{ applyExcelBodyStyle(c,'FF101929', idx===2?'center':'right'); c.numFmt = idx===2 ? '0%' : '#,##0'; });
  ws.addRow([]);
  const headers = isGroup
    ? ['排名','分组','人数','战报','胜','败','平','胜率','攻城','总功勋','平均武勋','平均势力值']
    : ['排名','成员','分组','战报','胜','败','平','胜率','攻城','功勋','势力值'];
  const headerRow = ws.addRow(headers);
  headerRow.eachCell(c=>applyExcelHeaderStyle(c));
  rows.forEach((r,i)=>{
    const row = ws.addRow(isGroup
      ? [i+1, r.name||'', r.player_cnt||0, r.battles||0, r.wins||0, r.loses||0, r.draws||0, (Number(r.win_rate||0))/100, r.city_battles||0, r.total_gongxun||0, Math.round(Number(r.avg_gongxun||0)), Math.round(Number(r.avg_power||0))]
      : [i+1, r.name||'', r.group_name||'', r.battles||0, r.wins||0, r.loses||0, r.draws||0, (Number(r.win_rate||0))/100, r.city_battles||0, r.total_gongxun||0, r.power||0]
    );
    row.eachCell((c,col)=>{
      applyExcelBodyStyle(c, i%2===0?'FF0D1420':'FF101929', [2,3].includes(col)?'left':'right');
      if(col===8) c.numFmt = '0%';
      else if(col>=1) c.numFmt = col===2 || col===3 ? '@' : '#,##0';
    });
    row.getCell(2).font = { ...row.getCell(2).font, bold:true, color:{ argb:'FFE8C86A' } };
    row.getCell(8).fill = { type:'pattern', pattern:'solid', fgColor:{ argb:(Number(r.win_rate||0)>=60?'FF163020':Number(r.win_rate||0)>=40?'FF3A3318':'FF301818') } };
  });
  finishWorksheetLayout(ws, isGroup?[8,16,10,10,8,8,8,10,10,14,12,14]:[8,14,12,10,8,8,8,10,10,14,14], 6);
  return wb.xlsx.writeBuffer().then(buf=>{
    const blob = new Blob([buf], { type:'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' });
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = `团数据_${periodName}_${new Date().toISOString().slice(0,10)}.xlsx`;
    a.click();
    URL.revokeObjectURL(a.href);
  });
}

function exportTeamUsersExcel(){
  if(!_tuData?.length) return exportCurrentPageTableGeneric();
  const wb = new ExcelJS.Workbook();
  wb.creator = '率土战场指挥台';
  wb.created = new Date();
  const ws = wb.addWorksheet('同盟成员');
  const totalPower = _tuData.reduce((a,b)=>a+(b.power||0),0);
  const totalWu = _tuData.reduce((a,b)=>a+(b.wuxun||0),0);
  buildWorkbookCover(ws, '同盟成员总览', `导出时间：${new Date().toLocaleString('zh-CN',{hour12:false})}`, 9);
  ws.addRow([]);
  const statLabelRow = ws.addRow(['同盟人数','总势力值','总武勋']);
  const statValueRow = ws.addRow([_tuData.length, totalPower, totalWu]);
  statLabelRow.eachCell(c=>applyExcelHeaderStyle(c,'FF111A28','FFE8C86A',10));
  statValueRow.eachCell(c=>{ applyExcelBodyStyle(c,'FF101929','right'); c.numFmt='#,##0'; });
  ws.addRow([]);
  const headerRow = ws.addRow(['成员','UID','职位','势力值','武勋','周贡献','总贡献','分组','加入日期']);
  headerRow.eachCell(c=>applyExcelHeaderStyle(c));
  _tuData.forEach((r,i)=>{
    const row = ws.addRow([r.name||'', r.uid||0, POS_MAP[r.pos]||('职位'+(r.pos||0)), r.power||0, r.wuxun||0, r.contribute_week||0, r.contribute_total||0, r.group_name||'未分组', r.join_time ? new Date(r.join_time*1000).toLocaleDateString('zh-CN') : '' ]);
    row.eachCell((c,col)=>{ applyExcelBodyStyle(c, i%2===0?'FF0D1420':'FF101929', [1,3,8,9].includes(col)?'left':'right'); if([2,4,5,6,7].includes(col)) c.numFmt='#,##0'; });
    row.getCell(1).font = { ...row.getCell(1).font, bold:true, color:{ argb:'FFE8C86A' } };
    row.getCell(4).fill = { type:'pattern', pattern:'solid', fgColor:{ argb:'FF2A2410' } };
    row.getCell(5).fill = { type:'pattern', pattern:'solid', fgColor:{ argb:'FF102430' } };
  });
  finishWorksheetLayout(ws, [14,14,12,14,14,12,12,12,12], 6);
  return wb.xlsx.writeBuffer().then(buf=>{
    const blob = new Blob([buf], { type:'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' });
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = `同盟成员_${new Date().toISOString().slice(0,10)}.xlsx`;
    a.click();
    URL.revokeObjectURL(a.href);
  });
}

function exportUnionListExcel(){
  if(!_ulData?.length) return exportCurrentPageTableGeneric();
  const wb = new ExcelJS.Workbook();
  wb.creator = '率土战场指挥台';
  wb.created = new Date();
  const ws = wb.addWorksheet('联盟列表');
  const totalPower = _ulData.reduce((s,r)=>s+(r.power||0),0);
  const totalMember = _ulData.reduce((s,r)=>s+(r.total_member||0),0);
  buildWorkbookCover(ws, '联盟列表总览', `导出时间：${new Date().toLocaleString('zh-CN',{hour12:false})}`, 9);
  ws.addRow([]);
  const statLabelRow = ws.addRow(['联盟数','总势力值','总人数']);
  const statValueRow = ws.addRow([_ulData.length, totalPower, totalMember]);
  statLabelRow.eachCell(c=>applyExcelHeaderStyle(c,'FF111A28','FFE8C86A',10));
  statValueRow.eachCell(c=>{ applyExcelBodyStyle(c,'FF101929','right'); c.numFmt='#,##0'; });
  ws.addRow([]);
  const headerRow = ws.addRow(['排名','联盟','等级','势力值','人数','占领值','NPC城','区域','更新时间']);
  headerRow.eachCell(c=>applyExcelHeaderStyle(c));
  _ulData.forEach((r,i)=>{
    const row = ws.addRow([r.rank||i+1, r.name||'', r.level||0, r.power||0, r.total_member||0, r.occupy_city_value||0, r.total_npc_city||0, r.region||'', r.updated_at ? r.updated_at.slice(5,16) : '' ]);
    row.eachCell((c,col)=>{ applyExcelBodyStyle(c, i%2===0?'FF0D1420':'FF101929', [2,8,9].includes(col)?'left':'right'); if([1,3,4,5,6,7].includes(col)) c.numFmt='#,##0'; });
    row.getCell(1).font = { ...row.getCell(1).font, bold:true, color:{ argb:i<3?'FFE8C86A':'FFD8D2C0' } };
    row.getCell(2).font = { ...row.getCell(2).font, bold:true, color:{ argb:'FFE8C86A' } };
    row.getCell(4).fill = { type:'pattern', pattern:'solid', fgColor:{ argb:'FF2A2410' } };
  });
  finishWorksheetLayout(ws, [8,18,8,14,10,10,10,12,12], 6);
  return wb.xlsx.writeBuffer().then(buf=>{
    const blob = new Blob([buf], { type:'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' });
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = `联盟列表_${new Date().toISOString().slice(0,10)}.xlsx`;
    a.click();
    URL.revokeObjectURL(a.href);
  });
}

function exportBattlesAllExcel(){
  const pageEl = document.getElementById('tab10');
  const tbody = pageEl?.querySelector('#ba-body');
  if(!tbody || !tbody.children.length) return exportCurrentPageTableGeneric();
  const wb = new ExcelJS.Workbook();
  wb.creator = '率土战场指挥台';
  wb.created = new Date();
  const ws = wb.addWorksheet('全部战报');
  buildWorkbookCover(ws, '全部战报导出', `导出时间：${new Date().toLocaleString('zh-CN',{hour12:false})}`, 7);
  ws.addRow([]);
  const filterDesc = [
    `玩家：${document.getElementById('ba-player')?.value||'全部'}`,
    `联盟：${document.getElementById('ba-union')?.value||'全部'}`,
    `结果：${document.getElementById('ba-result')?.value||'全部'}`,
    `类型：${document.getElementById('ba-ftype')?.value||'全部'}`,
    `周期：${document.getElementById('ba-period')?.value||'全部'}`
  ].join('  ·  ');
  ws.mergeCells(4,1,4,7);
  ws.getCell(4,1).value = filterDesc;
  ws.getCell(4,1).font = { size:10, color:{ argb:'FF8EA0B3' }, name:'Microsoft YaHei' };
  ws.getCell(4,1).fill = { type:'pattern', pattern:'solid', fgColor:{ argb:'FF0F1620' } };
  const headerRow = ws.addRow(['时间','攻方','攻方武将','结果','守方武将','守方','查看']);
  headerRow.eachCell(c=>applyExcelHeaderStyle(c));
  [...tbody.querySelectorAll('tr')].forEach((tr,i)=>{
    const cells = [...tr.querySelectorAll('td')].map(td=>td.innerText.trim().replace(/\n+/g,' / '));
    const row = ws.addRow(cells);
    row.eachCell((c,col)=>{ applyExcelBodyStyle(c, i%2===0?'FF0D1420':'FF101929', [1,2,3,5,6].includes(col)?'left':'center'); });
    if(String(cells[3]||'').includes('胜')) row.getCell(4).font = { ...row.getCell(4).font, bold:true, color:{ argb:'FF46B06E' } };
    else if(String(cells[3]||'').includes('败')) row.getCell(4).font = { ...row.getCell(4).font, bold:true, color:{ argb:'FFE05050' } };
    else row.getCell(4).font = { ...row.getCell(4).font, bold:true, color:{ argb:'FFC8A044' } };
  });
  finishWorksheetLayout(ws, [20,18,28,10,28,18,10], 5);
  return wb.xlsx.writeBuffer().then(buf=>{
    const blob = new Blob([buf], { type:'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' });
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = `全部战报_${new Date().toISOString().slice(0,10)}.xlsx`;
    a.click();
    URL.revokeObjectURL(a.href);
  });
}

async function exportCurrentPageTableGeneric(){
  if(!ensureExportLibraries()) return;
  const pageEl = getCurrentPageEl();
  const snap = getPageExportSnapshot(pageEl);
  if(!snap || !snap.tables.length){
    showToast('当前页面没有可导出的表格','var(--red)');
    return;
  }
  const wb = new ExcelJS.Workbook();
  wb.creator = '率土战场指挥台';
  wb.created = new Date();
  snap.tables.forEach((table, idx)=>{
    const ws = wb.addWorksheet((table.title || `表格${idx+1}`).slice(0,31));
    const title = snap.title || '页面导出';
    const colCount = Math.max(1, table.headers.length || (table.rows[0]?.length || 1));
    buildWorkbookCover(ws, title, `${table.title || `表格 ${idx+1}`}  ·  导出时间：${new Date().toLocaleString('zh-CN', { hour12:false })}`, colCount);
    if(snap.stats.length){
      ws.addRow([]);
      const labels = snap.stats.map(s=>s.label);
      const values = snap.stats.map(s=>s.value);
      const r1 = ws.addRow(labels);
      const r2 = ws.addRow(values);
      r1.eachCell(c=>applyExcelHeaderStyle(c,'FF111A28','FFE8C86A',10));
      r2.eachCell(c=>applyExcelBodyStyle(c,'FF101929','right'));
      ws.addRow([]);
    } else {
      ws.addRow([]);
    }
    const headerRow = ws.addRow(table.headers);
    headerRow.eachCell(c=>applyExcelHeaderStyle(c));
    table.rows.forEach((row, ridx)=>{
      const excelRow = ws.addRow(row);
      excelRow.eachCell(cell=>applyExcelBodyStyle(cell, ridx % 2 === 0 ? 'FF0D1420' : 'FF101929'));
    });
    const widths = Array.from({length: colCount}, (_, i)=>Math.min(40, Math.max(12, Math.max(...ws.getColumn(i+1).values.filter(Boolean).map(v=>String(v).length)) + 2)));
    finishWorksheetLayout(ws, widths, snap.stats.length ? 6 : 4);
  });
  const buf = await wb.xlsx.writeBuffer();
  const blob = new Blob([buf], { type:'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' });
  const a = document.createElement('a');
  a.href = URL.createObjectURL(blob);
  a.download = `${String(snap.title || '页面导出').replace(/[\\/:*?"<>|]/g,'_')}_${new Date().toISOString().slice(0,10)}.xlsx`;
  a.click();
  URL.revokeObjectURL(a.href);
}

async function exportCurrentPageTable(){
  const pageEl = getCurrentPageEl();
  const id = pageEl?.id || '';
  if(id === 'tab23') return exportTeamReportExcel();
  if(id === 'tab14') return exportTeamUsersExcel();
  if(id === 'tab18') return exportUnionListExcel();
  if(id === 'tab10') return exportBattlesAllExcel();
  return exportCurrentPageTableGeneric();
}

function injectPageExportButtons(){
  document.querySelectorAll('.page').forEach(page=>{
    const head = page.querySelector('.tbl-head');
    if(!head || head.dataset.exportInjected === '1') return;
    let controls = head.querySelector('.tbl-controls');
    if(!controls){
      controls = document.createElement('div');
      controls.className = 'tbl-controls';
      head.appendChild(controls);
    }
    const pdfBtn = document.createElement('button');
    pdfBtn.className = 'btn';
    pdfBtn.textContent = '📄 导出PDF';
    pdfBtn.onclick = ()=>exportCurrentPagePDF();
    const tableBtn = document.createElement('button');
    tableBtn.className = 'btn';
    tableBtn.textContent = '📋 导出表格';
    tableBtn.onclick = ()=>exportCurrentPageTable();
    controls.appendChild(pdfBtn);
    controls.appendChild(tableBtn);
    head.dataset.exportInjected = '1';
  });
}

// 添加样式
(function(){
  const s = document.createElement('style');
  s.textContent = `.msg-filter-btn{padding:4px 14px;background:var(--panel2);border:1px solid var(--border);color:var(--text2);border-radius:3px;cursor:pointer;font-size:.78rem;transition:all .2s}
.msg-filter-btn.active{background:#1a2535;color:var(--gold);border-color:var(--gold)}`;
  document.head.appendChild(s);
})();

function filterMsg(kind){
  _msgFilter = kind;
  document.querySelectorAll('.msg-filter-btn').forEach(b=>b.classList.remove('active'));
  const btnMap = {all:'msg-btn-all', chat:'msg-btn-chat', battle_notice:'msg-btn-notice'};
  const btn = document.getElementById(btnMap[kind]);
  if(btn) btn.classList.add('active');
  renderMsgList();
}

function clearMsgList(){
  _msgList = [];
  _msgChatCount = 0;
  _msgNoticeCount = 0;
  document.getElementById('msg-chat-count').textContent = '0';
  document.getElementById('msg-notice-count').textContent = '0';
  renderMsgList();
}

function renderMsgList(){
  const b = document.getElementById('msg-body');
  if(!b) return;
  const q = (document.getElementById('msg-search')||{}).value||'';
  const filtered = _msgList.filter(m=>{
    if(_msgFilter !== 'all' && m.kind !== _msgFilter) return false;
    if(q){
      const s = JSON.stringify(m).toLowerCase();
      if(!s.includes(q.toLowerCase())) return false;
    }
    return true;
  });
  b.innerHTML = filtered.slice(0,300).map(m=>{
    if(m.kind === 'chat'){
      return `<tr>
        <td style='color:var(--text2);font-size:.68rem;white-space:nowrap'>${esc(m.time_str||'')}</td>
        <td><span class='badge' style='background:#1a1a2e;color:var(--cyan)'>💬 聊天</span></td>
        <td style='color:var(--gold)'><b>${esc(m.sender||'')}</b></td>
        <td style='color:var(--text2);font-size:.72rem'>${esc(m.union||'')}</td>
        <td>${esc(m.text||'')}</td>
      </tr>`;
    } else {
      const resClass = m.result===1||m.result===7||m.result===11?'badge-win':m.result===2||m.result===6||m.result===12?'badge-lose':'badge-draw';
      return `<tr>
        <td style='color:var(--text2);font-size:.68rem;white-space:nowrap'>${esc(m.time_str||'')}</td>
        <td><span class='badge' style='background:#1a2010;color:var(--green)'>⚔ 战斗</span></td>
        <td style='color:var(--red)'><b>${esc(m.atk_name||'')}</b></td>
        <td style='color:var(--text2);font-size:.72rem'>${esc(m.def_union||'')}</td>
        <td><span class='badge ${resClass}'>${esc(m.result_desc||'')}</span>
          <span style='color:var(--text2);font-size:.7rem'> wx=${fmt(m.atk_gongxun||0)}</span>
          <span style='color:var(--text2);font-size:.7rem'> ${esc(m.fight_type_name||'')} wid=${m.wid||''}</span>
        </td>
      </tr>`;
    }
  }).join('');
}

function onMsg834(evt){
  const d = evt.data||{};
  if(evt.type === 'chat_834'){
    _msgChatCount++;
    document.getElementById('msg-chat-count').textContent = _msgChatCount;
    _msgList.unshift({kind:'chat', ...d});
  } else if(evt.type === 'battle_notice'){
    _msgNoticeCount++;
    const el=document.getElementById('msg-notice-count');
    if(el) el.textContent = _msgNoticeCount;
    _msgList.unshift({kind:'battle_notice', ...d});
  }
  if(_msgList.length > 500) _msgList.length = 500;
  // 只有当前在 tab21 时才实时刷新
  if(document.getElementById('tab21')&&document.getElementById('tab21').classList.contains('active')){
    renderMsgList();
  }
}
*/
