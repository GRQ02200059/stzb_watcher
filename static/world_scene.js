// 世界场景看板 (5026/5028 协议读模型)
// 只读消费 /api/world/viewport | /api/world/armies | /api/world/marches
// 三个子视图：地图视野 / 实时行军 / 战场监控。严格只读，不发包、不执行动作。

let _wsView = 'map';
let _wsStreamStarted = false;
let _wsReloadTimer = null;
const _wsDirty = {march:true,army:true,entity:true};
const _wsCache = {march:[],army:[],entity:[]};

// 城池类型映射（与旧地图页保持一致的着色/命名习惯）
const WS_CITY_TYPE = STZB_META.cityTypes;
const WS_CITY_COLOR = STZB_META.cityTypeColors;

// 行军类型（realMarch marchType）常见枚举，未知则原样显示
const WS_MARCH_TYPE = {0:'普通',1:'集结',2:'返回',3:'调动'};

function wsWid(v){
  if(v===null||v===undefined||v==='') return '';
  const n = Number(v);
  if(!n) return '';
  return `${Math.floor(n/10000)},${n%10000}`;
}

function wsTime(sec){
  if(!sec) return '';
  const n = Number(sec);
  if(!n || n<0) return '';
  // 协议时间多为秒级时间戳；若小于 1e10 视作秒
  const ms = n < 1e12 ? n*1000 : n;
  const d = new Date(ms);
  if(isNaN(d.getTime())) return String(sec);
  return d.toLocaleString('zh-CN',{hour12:false,month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',second:'2-digit'});
}

function wsSwitch(view, el){
  _wsView = ['map','march','army','entity'].includes(view) ? view : 'map';
  window.IntelligenceCenter?.openView(_wsView);
}

async function loadWorldScene(){
  const target = _wsView === 'map' ? 'map' : _wsView;
  const button = [...document.querySelectorAll('nav button')].find(candidate =>
    String(candidate.getAttribute('onclick') || '').includes('switchTab(33,')
  );
  if (!document.getElementById('tab33')?.classList.contains('active')) {
    window.switchTab?.(33, button);
  }
  if (target === 'map') {
    window.IntelligenceCenter?.openView('map');
    return window.loadIntelligenceCenter?.();
  }
  return window.WorldScenePanel?.load(target, true);
}

async function loadWorldScenePanel(view, force=false){
  const delegate = window.WorldScenePanel?.load;
  if (delegate && delegate !== loadWorldScenePanel) {
    return delegate(view, force);
  }
  initWorldSceneStream();
  const upd = document.getElementById('ws-updated');
  if(upd) upd.textContent = '加载中…';
  const normalized = ['march','army','entity'].includes(view) ? view : 'march';
  _wsView = normalized;
  if (!force && !_wsDirty[normalized]) {
    renderWorldScenePanel(normalized, _wsCache[normalized]);
    if(upd) upd.textContent = '缓存于 ' + new Date().toLocaleTimeString('zh-CN',{hour12:false});
    return;
  }
  const endpoint = {
    march:'/api/world/marches',
    army:'/api/world/armies',
    entity:'/api/world/entities'
  }[normalized];
  const data = await apiFetch(endpoint);
  const rows = normalized === 'march'
    ? (data?.marches || [])
    : normalized === 'army'
      ? (data?.armies || [])
      : (data?.entities || []);
  _wsCache[normalized] = rows;
  _wsDirty[normalized] = false;
  renderWorldScenePanel(normalized, rows);
  if(upd) upd.textContent = '更新于 ' + new Date().toLocaleTimeString('zh-CN',{hour12:false});
}

function renderWorldScenePanel(view, rows){
  if(view==='march') renderWsMarch(rows);
  else if(view==='army') renderWsArmy(rows);
  else renderWsEntities(rows);
}

function initWorldSceneStream(){
  if(_wsStreamStarted) return;
  _wsStreamStarted = true;
  window.addEventListener('stzb:stream-event', event => {
    const msg = event.detail || {};
    if(!['world_snapshot_complete','world_scene_delta'].includes(msg.type)) return;
    const src = document.getElementById('ws-source');
    if(src) src.textContent = `实时推送 · ${msg.type} · seq ${msg.data&&msg.data.seq || ''}`;
    _wsDirty.march = true;
    _wsDirty.army = true;
    _wsDirty.entity = true;
    if(document.getElementById('tab33')?.classList.contains('active') && _wsView !== 'map'){
      clearTimeout(_wsReloadTimer);
      _wsReloadTimer = setTimeout(() => loadWorldScenePanel(_wsView, true), 350);
    }
  });
}

function renderWsCards(tiles, armies, marches, entities, visualField){
  const wrap = document.getElementById('ws-cards');
  if(!wrap) return;
  const cities = tiles.filter(t=>t.name).length;
  const vf = visualField && visualField.raw ? 1 : 0;
  wrap.innerHTML =
    `<div class='stat-card'><div class='val'>${tiles.length}</div><div class='lbl'>视窗城池格</div></div>` +
    `<div class='stat-card'><div class='val' style='color:var(--gold)'>${cities}</div><div class='lbl'>具名城池</div></div>` +
    `<div class='stat-card'><div class='val' style='color:var(--cyan)'>${armies.length}</div><div class='lbl'>活跃部队</div></div>` +
    `<div class='stat-card'><div class='val' style='color:var(--green)'>${marches.length}</div><div class='lbl'>实时行军</div></div>` +
    `<div class='stat-card'><div class='val' style='color:var(--purple)'>${entities.length}</div><div class='lbl'>广度实体</div></div>` +
    `<div class='stat-card'><div class='val' style='color:var(--text2)'>${vf}</div><div class='lbl'>visualField</div></div>`;
}

function renderWsMap(tiles, armies, visualField, bounds){
  renderWsGrid(tiles, armies, visualField, bounds);
  const body = document.getElementById('ws-map-body');
  const cnt  = document.getElementById('ws-map-count');
  if(!body) return;
  if(cnt) cnt.textContent = `${tiles.length} 格`;
  if(!tiles.length){
    body.innerHTML = `<tr><td colspan='11'><div class='ws-empty'>暂无世界城池数据。当客户端抓到 5026/5028 报文后，这里会显示 WORLD_CITY 投影。</div></td></tr>`;
    return;
  }
  body.innerHTML = tiles.map(t=>{
    const tname = WS_CITY_TYPE[t.city_type] || ('type'+t.city_type);
    const tcolor = WS_CITY_COLOR[t.city_type] || 'var(--text)';
    return `<tr>
      <td style='font-family:var(--font-mono)'>${esc(t.wid)}</td>
      <td>${t.row},${t.col}</td>
      <td style='color:${tcolor}'>${esc(tname)}</td>
      <td>${esc(t.name||'')}</td>
      <td>${t.user_id?esc(t.user_id):'<span style="color:var(--text2)">-</span>'}</td>
      <td>${t.union_id?esc(t.union_id):'<span style="color:var(--text2)">-</span>'}</td>
      <td>${t.force?esc(t.force):''}</td>
      <td style='font-size:.78rem'>${esc(wsTime(t.protect_end_time))}</td>
      <td style='font-size:.78rem'>${esc(wsTime(t.guard_end_time))}</td>
      <td>${t.view_range_add?esc(t.view_range_add):''}</td>
      <td style='color:var(--text2)'>${t.state_id!=null?esc(t.state_id):''}</td>
    </tr>`;
  }).join('');
}

function visualWidSet(visualField){
  const s = new Set();
  const raw = visualField && visualField.raw;
  if(!raw) return s;
  if(Array.isArray(raw)){
    raw.forEach(v=>{
      const n = Number(v);
      if(Number.isInteger(n) && n > 0 && n < 1000000000) s.add(n);
    });
    return s;
  }
  Object.keys(raw).forEach(k=>{
    const n = Number(k);
    if(Number.isInteger(n) && n > 0) s.add(n);
  });
  return s;
}

function renderWsGrid(tiles, armies, visualField, bounds){
  const grid = document.getElementById('ws-grid');
  if(!grid) return;
  const rows = Math.max(0, bounds.rowDown - bounds.rowUp + 1);
  const cols = Math.max(0, bounds.colRight - bounds.colLeft + 1);
  if(rows <= 0 || cols <= 0 || rows * cols > 1600){
    grid.style.display = '';
    grid.style.gridTemplateColumns = '';
    grid.innerHTML = `<div class='ws-empty'>视窗过大，网格渲染限制为 1600 格以内。请缩小 row/col 范围。</div>`;
    return;
  }
  const byWid = new Map(tiles.map(t=>[Number(t.wid), t]));
  const armyByWid = new Map();
  (armies||[]).forEach(a=>{
    const wid = Number(a.stay_wid || a.reside_wid || a.wid_to || 0);
    if(wid) armyByWid.set(wid, (armyByWid.get(wid)||0)+1);
  });
  const visual = visualWidSet(visualField);
  const cells = [];
  for(let r=bounds.rowUp; r<=bounds.rowDown; r++){
    for(let c=bounds.colLeft; c<=bounds.colRight; c++){
      const wid = r*10000+c;
      const t = byWid.get(wid);
      const visible = !!t || visual.has(wid);
      const city = !!(t && (t.name || t.city_type));
      const army = armyByWid.has(wid);
      const label = t && t.name ? t.name : `${r},${c}`;
      const type = t ? (WS_CITY_TYPE[t.city_type] || ('type'+t.city_type)) : '';
      cells.push(`<div class='ws-cell ${visible?'visible':'fog'} ${city?'city':''} ${army?'army':''}' title='WID ${wid} ${esc(type)} ${esc(t&&t.name||'')}'>
        <span>${c}</span><span class='name'>${esc(label)}</span>
      </div>`);
    }
  }
  grid.style.gridTemplateColumns = `repeat(${cols}, minmax(34px, 1fr))`;
  grid.innerHTML = cells.join('');
}

function renderWsMarch(marches){
  const body = document.getElementById('ws-march-body');
  const cnt  = document.getElementById('ws-march-count');
  if(!body) return;
  if(cnt) cnt.textContent = `${marches.length} 条`;
  if(!marches.length){
    body.innerHTML = `<tr><td colspan='11'><div class='ws-empty'>暂无实时行军。realMarch (slot[29]) 会记录部队的上一格/当前格/下一格与逐跳时间。</div></td></tr>`;
    return;
  }
  body.innerHTML = marches.map(m=>{
    const mtype = WS_MARCH_TYPE[m.march_type] != null ? WS_MARCH_TYPE[m.march_type] : ('type'+m.march_type);
    return `<tr>
      <td style='font-family:var(--font-mono)'>${esc(m.real_march_id)}</td>
      <td>${wsWidButton(m.last_wid)}</td>
      <td style='color:var(--cyan)'>${wsWidButton(m.current_wid)}</td>
      <td style='color:var(--gold)'>${wsWidButton(m.next_wid)}</td>
      <td style='font-size:.78rem'>${esc(wsTime(m.start_time))}</td>
      <td style='font-size:.78rem'>${esc(wsTime(m.next_time))}</td>
      <td style='font-size:.78rem'>${esc(wsTime(m.end_time))}</td>
      <td style='color:var(--text2)'>${m.path_id?esc(m.path_id):''}</td>
      <td>${m.unit_time_cost?esc(m.unit_time_cost):''}</td>
      <td>${esc(mtype)}</td>
      <td>${m.belong_id?esc(m.belong_id):''}</td>
    </tr>`;
  }).join('');
}

function renderWsArmy(armies){
  const body = document.getElementById('ws-army-body');
  const cnt  = document.getElementById('ws-army-count');
  if(!body) return;
  if(cnt) cnt.textContent = `${armies.length} 支`;
  if(!armies.length){
    body.innerHTML = `<tr><td colspan='18'><div class='ws-empty'>暂无活跃部队。MapArmyTuple (slot[6]) 会记录部队出发/目标/状态/士气与战报串。</div></td></tr>`;
    return;
  }
  body.innerHTML = armies.map(a=>{
    const owner = a.owner_name ? `${a.owner_name} (${a.user_id})` : (a.user_id || '');
    const target = a.target_name || '';
    const locateWid = Number(a.wid_to || a.stay_wid || a.reside_wid || 0);
    return `<tr class='ws-locatable-row' data-world-wid='${locateWid}' onclick='WorldScenePanel.locateWid(${locateWid})'>
      <td style='font-family:var(--font-mono)'>${esc(a.army_id)}</td>
      <td>${esc(a.state)}</td>
      <td>${esc(owner)}</td>
      <td>${esc(a.owner_union_name||'')}</td>
      <td>${wsWidButton(a.wid_from)}</td>
      <td style='color:var(--gold)'>${wsWidButton(a.wid_to)}</td>
      <td>${esc(target)}</td>
      <td>${a.target_type?esc(a.target_type):''}</td>
      <td>${wsWidButton(a.reside_wid)}</td>
      <td>${wsWidButton(a.stay_wid)}</td>
      <td style='font-size:.78rem'>${esc(a.army_hero_type||'')}</td>
      <td>${a.morale?esc(a.morale):''}</td>
      <td style='font-size:.78rem;color:var(--text2)'>${esc(a.buff_ids||'')}</td>
      <td>${wsWidButton(a.obstacle_wid)}</td>
      <td style='color:var(--text2)'>${a.real_march_id?esc(a.real_march_id):''}</td>
      <td style='font-size:.78rem'>${esc(wsTime(a.begin_time))}</td>
      <td style='font-size:.78rem'>${esc(wsTime(a.end_time))}</td>
      <td style='font-size:.78rem;max-width:180px'>${esc(a.battle_show||'')}</td>
    </tr>`;
  }).join('');
}

function renderWsEntities(rows){
  const body = document.getElementById('ws-entity-body');
  const cnt = document.getElementById('ws-entity-count');
  const summary = document.getElementById('ws-entity-summary');
  if(!body) return;
  if(cnt) cnt.textContent = `${rows.length} 条`;
  const counts = {};
  rows.forEach(r=>{ counts[r.category]=(counts[r.category]||0)+1; });
  if(summary){
    summary.innerHTML = Object.keys(counts).sort().map(k=>`<span class='ws-entity-pill'>${esc(k)} · ${counts[k]}</span>`).join('') || `<span class='ws-entity-pill'>暂无广度实体</span>`;
  }
  if(!rows.length){
    body.innerHTML = `<tr><td colspan='4'><div class='ws-empty'>暂无 warShips / assistArmies / armyGroups / shortMessages 等广度槽位数据。</div></td></tr>`;
    return;
  }
  body.innerHTML = rows.map(r=>`<tr>
    <td>${esc(r.category)}</td>
    <td style='font-family:var(--font-mono)'>${esc(r.entity_id)}</td>
    <td>${esc(r.source_seq)}</td>
    <td style='font-family:var(--font-mono);font-size:.72rem;max-width:720px'>${esc(JSON.stringify(r.raw))}</td>
  </tr>`).join('');
}

function wsWidButton(wid){
  const value = Number(wid || 0);
  if(!value) return '<span style="color:var(--text2)">-</span>';
  return `<button class='ws-wid-link' type='button' onclick='event.stopPropagation();WorldScenePanel.locateWid(${value})'>${esc(wsWid(value))}</button>`;
}

function locateWorldSceneWid(wid){
  const value = Number(wid || 0);
  if(!value) return;
  window.IntelligenceCenter?.openView('map');
  window.IntelligenceCenter?.locateWid(value);
}

window.WorldScenePanel = {
  load: loadWorldScenePanel,
  renderMarches: renderWsMarch,
  renderArmies: renderWsArmy,
  renderEntities: renderWsEntities,
  locateWid: locateWorldSceneWid,
  markDirty(){
    _wsDirty.march = true;
    _wsDirty.army = true;
    _wsDirty.entity = true;
  },
  get currentView(){ return _wsView; }
};
