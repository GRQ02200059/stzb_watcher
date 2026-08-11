// 世界场景看板 (5026/5028 协议读模型)
// 只读消费 /api/world/viewport | /api/world/armies | /api/world/marches
// 三个子视图：地图视野 / 实时行军 / 战场监控。严格只读，不发包、不执行动作。

let _wsView = 'map';

// 城池类型映射（与旧地图页保持一致的着色/命名习惯）
const WS_CITY_TYPE = {
  8:'攻城营垒',11:'斥候营地',12:'大型要塞',13:'关卡',14:'皇城',17:'联盟城池',20:'战场',
  70:'铁矿场',71:'铜矿场',72:'银矿场',73:'金矿场',74:'玉矿场',75:'石矿场',76:'采矿场',77:'采矿场',78:'采矿场'
};
const WS_CITY_COLOR = {
  8:'var(--blue)',11:'var(--cyan)',12:'var(--gold)',13:'var(--purple)',14:'var(--red)',17:'var(--green)',20:'var(--text2)',
  70:'#888',71:'#b87333',72:'#c0c0c0',73:'#ffd700',74:'#00e5ff',75:'#aaa',76:'#a0714f',77:'#a0714f',78:'#a0714f'
};

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
  _wsView = view;
  document.querySelectorAll('.ws-subtab').forEach(b=>b.classList.remove('active'));
  if(el) el.classList.add('active');
  document.getElementById('ws-view-map').style.display   = view==='map'   ? '' : 'none';
  document.getElementById('ws-view-march').style.display = view==='march' ? '' : 'none';
  document.getElementById('ws-view-army').style.display  = view==='army'  ? '' : 'none';
}

async function loadWorldScene(){
  const upd = document.getElementById('ws-updated');
  if(upd) upd.textContent = '加载中…';

  const rowUp    = parseInt(document.getElementById('ws-row-up').value)||0;
  const rowDown  = parseInt(document.getElementById('ws-row-down').value)||99999;
  const colLeft  = parseInt(document.getElementById('ws-col-left').value)||0;
  const colRight = parseInt(document.getElementById('ws-col-right').value)||99999;

  const [vp, armies, marches] = await Promise.all([
    apiFetch(`/api/world/viewport?rowUp=${rowUp}&rowDown=${rowDown}&colLeft=${colLeft}&colRight=${colRight}`),
    apiFetch('/api/world/armies'),
    apiFetch('/api/world/marches')
  ]);

  const tiles     = (vp && vp.tiles)      || [];
  const armyRows  = (armies && armies.armies)   || [];
  const marchRows = (marches && marches.marches) || [];

  renderWsCards(tiles, armyRows, marchRows);
  renderWsMap(tiles);
  renderWsMarch(marchRows);
  renderWsArmy(armyRows);

  if(upd) upd.textContent = '更新于 ' + new Date().toLocaleTimeString('zh-CN',{hour12:false});
}

function renderWsCards(tiles, armies, marches){
  const wrap = document.getElementById('ws-cards');
  if(!wrap) return;
  const cities = tiles.filter(t=>t.name).length;
  wrap.innerHTML =
    `<div class='stat-card'><div class='val'>${tiles.length}</div><div class='lbl'>视窗城池格</div></div>` +
    `<div class='stat-card'><div class='val' style='color:var(--gold)'>${cities}</div><div class='lbl'>具名城池</div></div>` +
    `<div class='stat-card'><div class='val' style='color:var(--cyan)'>${armies.length}</div><div class='lbl'>活跃部队</div></div>` +
    `<div class='stat-card'><div class='val' style='color:var(--green)'>${marches.length}</div><div class='lbl'>实时行军</div></div>`;
}

function renderWsMap(tiles){
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
      <td>${esc(wsWid(m.last_wid))}</td>
      <td style='color:var(--cyan)'>${esc(wsWid(m.current_wid))}</td>
      <td style='color:var(--gold)'>${esc(wsWid(m.next_wid))}</td>
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
    return `<tr>
      <td style='font-family:var(--font-mono)'>${esc(a.army_id)}</td>
      <td>${esc(a.state)}</td>
      <td>${esc(owner)}</td>
      <td>${esc(a.owner_union_name||'')}</td>
      <td>${esc(wsWid(a.wid_from))}</td>
      <td style='color:var(--gold)'>${esc(wsWid(a.wid_to))}</td>
      <td>${esc(target)}</td>
      <td>${a.target_type?esc(a.target_type):''}</td>
      <td>${esc(wsWid(a.reside_wid))}</td>
      <td>${esc(wsWid(a.stay_wid))}</td>
      <td style='font-size:.78rem'>${esc(a.army_hero_type||'')}</td>
      <td>${a.morale?esc(a.morale):''}</td>
      <td style='font-size:.78rem;color:var(--text2)'>${esc(a.buff_ids||'')}</td>
      <td>${esc(wsWid(a.obstacle_wid))}</td>
      <td style='color:var(--text2)'>${a.real_march_id?esc(a.real_march_id):''}</td>
      <td style='font-size:.78rem'>${esc(wsTime(a.begin_time))}</td>
      <td style='font-size:.78rem'>${esc(wsTime(a.end_time))}</td>
      <td style='font-size:.78rem;max-width:180px'>${esc(a.battle_show||'')}</td>
    </tr>`;
  }).join('');
}
