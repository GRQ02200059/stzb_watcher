// ===== 战斗模拟器 - 精确复刻 stzbBattleSimulator 风格 =====
let _simHeroes = null;
let _simSkills = null;
let _simState  = { blue: [], red: [] };
let _simSkillPicker = null; // {camp, heroIdx, slot}

const SIM_POS       = ['大营','中军','前锋'];
const SIM_CAMP_NAME = ['','蜀','魏','吴','汉','群','晋'];
const SIM_CAMP_CLS  = ['','shu','wei','wu','han','qun','jin'];
const SIM_CAMP_COL  = {1:'#46b06e',2:'#4a8fe0',3:'#e05050',4:'#c8a044',5:'#9060d0',6:'#3ab8c8'};
const SIM_ARMY_NAME = ['','弓','步','骑'];
const SIM_SK_TYPE   = ['','指挥','主动','追击','被动'];
const SIM_SK_COL    = ['','#3ab8c8','#e05050','#c8a044','#9060d0'];
const SIM_SK_LVL_COL= {S:'#e4a020',A:'#c060d0',B:'#4a8fe0',C:'#7a8a9a'};

// ─── 初始化
async function initSimulator() {
  if (_simHeroes && _simSkills && _simSkills.length >= 200) { _renderSim(); return; }
  const r = await apiFetch('/api/simulate/heroes');
  if (!r || !r.ok) { showToast('加载武将数据失败','var(--red)'); return; }
  _simHeroes = r.heroes.sort((a,b) => a.id - b.id);
  _simSkills = r.skills.sort((a,b) => a.id - b.id);
  // 使用后端真实6位数 hero id，对应原项目默认阵容
  // 蓝方：张辽(100027) 刘备(100016) 太史慈(100090)
  // 红方：马超(100013) 魏延(100649) 曹操(100023)
  _simState.blue = [
    {id:100027,level:40,up:5,equip_skills:[0,0]},
    {id:100016,level:40,up:5,equip_skills:[0,0]},
    {id:100090,level:40,up:5,equip_skills:[0,0]},
  ];
  _simState.red = [
    {id:100013,level:40,up:5,equip_skills:[0,0]},
    {id:100649,level:40,up:5,equip_skills:[0,0]},
    {id:100023,level:40,up:5,equip_skills:[0,0]},
  ];
  _renderSim();
}

// ─── 武将图片
function _heroIconId(heroId) {
  if (typeof HERO_CFG === 'undefined') return heroId;
  const hi = (_simHeroes||[]).find(h => h.id===heroId);
  if (!hi) return heroId;
  let best = null;
  for (const cfg of Object.values(HERO_CFG)) {
    if (cfg.name===hi.name && (!best||(cfg.quality||0)>(best.quality||0))) best=cfg;
  }
  return best ? best.iconId : heroId;
}
function _heroImg(heroId) {
  const id = _heroIconId(heroId);
  return `https://g0.gph.netease.com/ngsocial/community/stzb/cn/cards/cut/card_medium_${id}.jpg?gameid=g10`;
}
function _heroImgLong(heroId) {
  const id = _heroIconId(heroId);
  return `https://g0.gph.netease.com/ngsocial/community/stzb/cn/cards/cut/card_long_${id}.jpg?gameid=g10`;
}
function _skillImg(skillId) {
  if (!skillId||skillId<=0) return '';
  if (skillId<200000) {
    const n = String(skillId-1000).padStart(3,'0');
    return `https://g0.gph.netease.com/ngsocial/community/stzb/front_end/img/jineng/tactics_${n}.png?gameid=g10`;
  }
  return '';
}

// ─── 主渲染
function _renderSim() {
  _renderTeamCards('blue');
  _renderTeamCards('red');
}

// ─── 渲染一侧卡牌区（精确复刻原项目 cards 布局）
function _renderTeamCards(camp) {
  const container = document.getElementById(`sim-${camp}-heroes`);
  if (!container) return;
  container.innerHTML = '';

  _simState[camp].forEach((hero, idx) => {
    const pos   = SIM_POS[idx]||`P${idx+1}`;
    const hinfo = (_simHeroes||[]).find(h=>h.id===hero.id)||{};
    const cc    = SIM_CAMP_COL[hinfo.camp]||'#7a8a9a';
    const cls   = SIM_CAMP_CLS[hinfo.camp]||'';
    const imgUrl= _heroImg(hero.id);
    const imgLong=_heroImgLong(hero.id);

    // 星级
    const STAR_COUNT = 5;
    const starsHtml = Array.from({length:STAR_COUNT},(_,i)=> {
      const lit = i < (hero.up||0);
      return `<div style="width:.9vw;height:.9vw;min-width:9px;min-height:9px;
        background:${lit?'#e4dd9e':'#2a3040'};border-radius:50%;
        box-shadow:${lit?'0 0 4px #e4dd9e80':'none'};flex-shrink:0"></div>`;
    }).join('');

    // 战法槽 HTML
    const sk0 = (_simSkills||[]).find(s=>s.id===(hero.equip_skills[0]||0));
    const sk1 = (_simSkills||[]).find(s=>s.id===(hero.equip_skills[1]||0));
    const skSlot = (sk, si) => {
      const tc = sk ? SIM_SK_COL[sk.skill_type]||'#7a8a9a' : '#2a3a4a';
      const tt = sk ? SIM_SK_TYPE[sk.skill_type]||'' : '';
      const img = sk ? _skillImg(sk.id) : '';
      return `<div onclick="_simOpenSkillPicker('${camp}',${idx},${si})"
        style="display:flex;align-items:center;gap:4px;cursor:pointer;
               background:#0a0e16;border:1px solid ${tc}66;
               border-radius:3px;padding:3px 5px;margin-bottom:2px;
               transition:border-color .15s"
        onmouseover="this.style.borderColor='${tc}'" onmouseout="this.style.borderColor='${tc}66'">
        <div style="width:20px;height:20px;border-radius:2px;flex-shrink:0;
          border:1px solid ${tc};overflow:hidden;background:#060810;
          display:flex;align-items:center;justify-content:center">
          ${img
            ? `<img src="${img}" style="width:100%;height:100%;object-fit:cover" onerror="this.style.display='none'">`
            : `<span style="font-size:.5rem;color:${tc}">${tt||'+'}</span>`
          }
        </div>
        <span style="font-size:.62rem;color:${sk?tc:'#3a4a5a'};flex:1;white-space:nowrap;overflow:hidden;text-overflow:ellipsis">
          ${sk ? esc(sk.name) : '点击装备战法'}
        </span>
        ${sk ? `<span onclick="event.stopPropagation();simClearSkill('${camp}',${idx},${si})"
          style="color:#3a4a5a;font-size:.6rem;padding:0 2px;cursor:pointer">×</span>` : ''}
      </div>`;
    };

    const card = document.createElement('div');
    card.dataset.camp = camp;
    card.dataset.idx  = idx;
    // 精确复刻原项目 .card 样式：竖排武将名、侧边位置标
    card.style.cssText = `
      position:relative;
      width:calc(33.33% - 6px);
      min-width:90px;
      background:#1a1a22;
      border:1.5px solid ${cc}66;
      border-radius:4px;
      overflow:hidden;
      box-shadow:0 2px 16px #00000080,0 0 0 1px ${cc}22;
      display:flex;
      flex-direction:column;
      flex-shrink:0;
    `;
    card.innerHTML = `
      <!-- 顶色条 -->
      <div style="height:2px;background:linear-gradient(90deg,${cc},${cc}22);flex-shrink:0"></div>

      <!-- 武将图区 -->
      <div style="position:relative;width:100%;padding-top:130%;flex-shrink:0;overflow:hidden">
        <!-- 背景大图 -->
        <img src="${imgUrl}"
          style="position:absolute;inset:0;width:100%;height:100%;
                 object-fit:cover;object-position:left top;opacity:.9"
          onerror="this.style.opacity=0">
        <!-- 渐变遮罩 -->
        <div style="position:absolute;inset:0;
          background:linear-gradient(to bottom,
            transparent 30%,#00000066 60%,#000000aa 100%)"></div>

        <!-- 位置标（左侧竖排）-->
        <div style="position:absolute;top:0;left:0;
          writing-mode:vertical-rl;font-size:.75rem;
          color:${cc};background:#000000aa;
          padding:3px 2px;letter-spacing:.1em;
          border-right:1px solid ${cc}44;font-weight:700">
          ${pos}
        </div>

        <!-- 阵营 兵种 右上 -->
        <div style="position:absolute;top:3px;right:3px;
          background:${cc};color:#fff;
          font-size:.5rem;padding:1px 4px;border-radius:2px;
          letter-spacing:.05em">
          ${SIM_CAMP_NAME[hinfo.camp]||''} ${SIM_ARMY_NAME[hinfo.army]||''}
        </div>

        <!-- 删除 -->
        <button onclick="simRemoveHero('${camp}',${idx})"
          style="position:absolute;top:22px;right:3px;
            background:#00000080;border:1px solid #3a4a5a;
            color:#7a8a9a;border-radius:50%;
            width:15px;height:15px;cursor:pointer;
            font-size:.5rem;padding:0;line-height:1">×</button>

        <!-- 武将名（底部横排）-->
        <div style="position:absolute;bottom:16px;left:14px;right:3px;
          color:#e4dd9e;font-size:.82rem;font-weight:700;
          letter-spacing:.12em;text-shadow:0 1px 6px #000">
          ${esc(hinfo.name||'')}
        </div>

        <!-- Lv + 进阶 -->
        <div style="position:absolute;bottom:3px;left:14px;
          
          font-size:.58rem;color:#8ab0c0;font-family:monospace">
          Lv<span style="color:#e4dd9e">${hero.level}</span>
          <span style="margin-left:4px;color:#c8a04488">★${hero.up||0}</span>
        </div>

        <!-- 星级 右下 -->
        <div style="position:absolute;bottom:3px;right:3px;display:flex;gap:2px">
          ${starsHtml}
        </div>
      </div>

      <!-- 武将选择下拉 -->
      <div style="padding:3px 4px 1px;flex-shrink:0">
        <select onchange="simChangeHero('${camp}',${idx},+this.value)"
          style="width:100%;background:#060810;color:#b0c0d0;
                 border:1px solid #1e2e40;padding:2px 3px;
                 border-radius:3px;font-size:.6rem">
          ${(_simHeroes||[]).map(h=>`<option value="${h.id}" ${h.id===hero.id?'selected':''}>${esc(h.name)} [${SIM_CAMP_NAME[h.camp]||''}]</option>`).join('')}
        </select>
      </div>

      <!-- Lv/进阶输入 -->
      <div style="display:flex;gap:3px;align-items:center;padding:1px 4px 2px;flex-shrink:0">
        <span style="font-size:.55rem;color:#3a5a7a">Lv</span>
        <input type="number" value="${hero.level}" min="1" max="45"
          style="width:34px;font-size:.6rem;background:#060810;
                 color:#c0d8f0;border:1px solid #1e2e40;
                 padding:1px 3px;border-radius:2px"
          onchange="simSetProp('${camp}',${idx},'level',+this.value)">
        <span style="font-size:.55rem;color:#3a5a7a">★</span>
        <input type="number" value="${hero.up||0}" min="0" max="9"
          style="width:26px;font-size:.6rem;background:#060810;
                 color:#e4dd9e;border:1px solid #1e2e40;
                 padding:1px 3px;border-radius:2px"
          onchange="simSetProp('${camp}',${idx},'up',+this.value);_renderTeamCards('${camp}')">
      </div>

      <!-- 战法槽 -->
      <div style="padding:2px 4px 5px;border-top:1px solid #1a2430;flex-shrink:0">
        <div style="font-size:.52rem;color:#2a4a6a;margin-bottom:2px;letter-spacing:.08em">装备战法</div>
        ${skSlot(sk0,0)}${skSlot(sk1,1)}
      </div>
    `;
    container.appendChild(card);
  });
}

function simAddHero(camp) {
  if (_simState[camp].length >= 3) { showToast('最多3名武将','var(--gold)'); return; }
  if (!_simHeroes||!_simHeroes.length) { showToast('武将数据未加载','var(--red)'); return; }
  _simState[camp].push({id:_simHeroes[0].id,level:40,up:5,equip_skills:[0,0]});
  _renderTeamCards(camp);
}

function simRemoveHero(camp, idx) {
  if (_simState[camp].length <= 1) { showToast('至少保留1名武将','var(--gold)'); return; }
  _simState[camp].splice(idx,1);
  _renderTeamCards(camp);
}

function simChangeHero(camp, idx, hid) {
  _simState[camp][idx].id = hid;
  _simState[camp][idx].equip_skills = [0,0];
  _renderTeamCards(camp);
}

function simSetProp(camp, idx, key, val) { _simState[camp][idx][key] = val; }

function simClearSkill(camp, idx, slot) {
  _simState[camp][idx].equip_skills[slot] = 0;
  _renderTeamCards(camp);
}

function _simOpenSkillPicker(camp, heroIdx, slot) {
  _simSkillPicker = {camp, heroIdx, slot};
  const overlay = document.getElementById('sim-skill-overlay');
  if (!overlay) { _buildSkillOverlay(); } else { overlay.style.display='flex'; }
  _renderSkillList();
}

function _buildSkillOverlay() {
  const el = document.createElement('div');
  el.id = 'sim-skill-overlay';
  el.style.cssText = `
    position:fixed;inset:0;background:#000000cc;z-index:999;
    display:flex;align-items:center;justify-content:center;
    font-family:'SimSun','宋体',serif;
  `;
  el.innerHTML = `
    <div style="
      background:linear-gradient(160deg,#0d1520,#080c14);
      border:1px solid #e4dd9e44;border-radius:8px;
      width:min(700px,95vw);max-height:85vh;
      display:flex;flex-direction:column;overflow:hidden;
      box-shadow:0 0 40px #00000080;
    ">
      <!-- 标题栏 -->
      <div style="display:flex;align-items:center;justify-content:space-between;
        padding:10px 16px;border-bottom:1px solid #1e2e40;
        background:linear-gradient(90deg,#0d1a2e,#0a1218);flex-shrink:0">
        <span style="color:#e4dd9e;font-size:.9rem;letter-spacing:.2em">学习战法</span>
        <div style="display:flex;gap:6px;align-items:center">
          <input id="sim-sk-search" placeholder="搜索战法..."
            oninput="_renderSkillList()"
            style="background:#060a10;border:1px solid #2a3a4a;color:#b0c0d0;
                   padding:3px 8px;border-radius:3px;font-size:.72rem;width:120px">
          <button onclick="document.getElementById('sim-skill-overlay').style.display='none'"
            style="background:none;border:1px solid #3a4a5a;color:#7a8a9a;
                   border-radius:50%;width:22px;height:22px;cursor:pointer;
                   font-size:.75rem;padding:0;line-height:1">×</button>
        </div>
      </div>
      <!-- 战法列表 -->
      <div id="sim-sk-list" style="overflow-y:auto;padding:10px 14px;
        scrollbar-width:thin;scrollbar-color:#2a3a4a transparent;flex:1">
      </div>
    </div>
  `;
  document.body.appendChild(el);
  el.addEventListener('click', e => { if(e.target===el) el.style.display='none'; });
}

function _renderSkillList() {
  const box = document.getElementById('sim-sk-list');
  if (!box) return;
  const q = (document.getElementById('sim-sk-search')||{}).value||'';
  const allSk = (_simSkills||[]).filter(s =>
    (s.study || s.id >= 200000) &&
    (!q || s.name.includes(q) || (s.desc||'').includes(q))
  );
  // 按类型分组
  const groups = {1:[],2:[],3:[],4:[]};
  allSk.forEach(s => { if(groups[s.skill_type]) groups[s.skill_type].push(s); });
  const typeNames = {1:'指挥',2:'主动',3:'追击',4:'被动'};
  const typeColors = {1:'#3ab8c8',2:'#e05050',3:'#c8a044',4:'#9060d0'};
  let html = '<div style="display:flex;gap:8px;flex-wrap:wrap;margin-bottom:8px">';
  html += `<span onclick="_simPickSkill(0)" style="cursor:pointer;font-size:.68rem;
    color:#3a5a7a;border:1px solid #1e2e40;padding:2px 8px;border-radius:3px">不装备</span>`;
  html += '</div>';
  [1,2,3,4].forEach(t => {
    if (!groups[t].length) return;
    const tc = typeColors[t];
    html += `<div style="margin-bottom:10px">
      <div style="font-size:.68rem;color:${tc};letter-spacing:.1em;
        margin-bottom:5px;border-left:2px solid ${tc};padding-left:6px">
        ${typeNames[t]} (${groups[t].length})
      </div>
      <div style="display:flex;flex-wrap:wrap;gap:4px">`;
    groups[t].forEach(s => {
      const lc = SIM_SK_LVL_COL[s.level]||'#7a8a9a';
      const img = _skillImg(s.id);
      html += `<div onclick="_simPickSkill(${s.id})"
        title="${esc(s.desc||s.name)}"
        style="display:flex;align-items:center;gap:4px;cursor:pointer;
               background:#0a0e16;border:1px solid ${tc}44;
               border-radius:4px;padding:3px 7px;
               transition:all .12s;min-width:80px"
        onmouseover="this.style.borderColor='${tc}';this.style.background='#0f1620'"
        onmouseout="this.style.borderColor='${tc}44';this.style.background='#0a0e16'">
        ${img ? `<img src="${img}" style="width:16px;height:16px;border-radius:2px;object-fit:cover" onerror="this.style.display='none'">` : ''}
        <span style="font-size:.65rem;color:#c0c8d0">${esc(s.name)}</span>
        <span style="font-size:.5rem;color:${lc};margin-left:auto">${s.level||''}</span>
      </div>`;
    });
    html += '</div></div>';
  });
  box.innerHTML = html;
}

function _simPickSkill(sid) {
  if (!_simSkillPicker) return;
  const {camp, heroIdx, slot} = _simSkillPicker;
  while (_simState[camp][heroIdx].equip_skills.length < 2)
    _simState[camp][heroIdx].equip_skills.push(0);
  _simState[camp][heroIdx].equip_skills[slot] = sid;
  document.getElementById('sim-skill-overlay').style.display='none';
  _renderTeamCards(camp);
}


function _buildConfig() {
  const mapH = h => ({id:h.id,level:h.level,up:h.up,
    equip_skills:(h.equip_skills||[]).filter(s=>s>0),extra_attrs:{}});
  return {
    blue:{morale:+(document.getElementById('sim-blue-morale').value||100),heros:_simState.blue.map(mapH)},
    red: {morale:+(document.getElementById('sim-red-morale').value||100), heros:_simState.red.map(mapH)}
  };
}

async function runSimulate(repeat) {
  const statusEl = document.getElementById('sim-status');
  statusEl.textContent = '模拟中...';
  statusEl.style.color = '#c8a044';
  ['sim-btn-single','sim-btn-100','sim-btn-1000'].forEach(id=>{
    const el=document.getElementById(id); if(el) el.disabled=true;
  });
  const r = await apiFetch('/api/simulate',{
    method:'POST',
    headers:{'Content-Type':'application/json'},
    body:JSON.stringify({..._buildConfig(),repeat})
  });
  ['sim-btn-single','sim-btn-100','sim-btn-1000'].forEach(id=>{
    const el=document.getElementById(id); if(el) el.disabled=false;
  });
  if (!r||!r.ok) {
    statusEl.textContent='失败: '+(r&&r.error||'未知');
    statusEl.style.color='#e05050';
    return;
  }
  statusEl.textContent = repeat===1 ? '战斗结束' : `完成${repeat}次`;
  statusEl.style.color = '#46b06e';
  if (repeat===1) _renderSingleResult(r.result);
  else _renderMultiResult(r);
  _updateHP(r, repeat);
}

function _updateHP(r, repeat) {
  if (repeat!==1) return;
  const res = r.result;
  const bt = res.blue.total_arms+(res.blue.hurt_arms||0);
  const rt = res.red.total_arms+(res.red.hurt_arms||0);
  const bp = bt>0 ? Math.round(res.blue.total_arms/bt*100) : 0;
  const rp = rt>0 ? Math.round(res.red.total_arms/rt*100) : 0;
  const bh=document.getElementById('sim-blue-hpbar');
  const rh=document.getElementById('sim-red-hpbar');
  const bt2=document.getElementById('sim-blue-hp-text');
  const rt2=document.getElementById('sim-red-hp-text');
  if(bh) bh.style.width=bp+'%';
  if(rh) rh.style.width=rp+'%';
  if(bt2) bt2.textContent=res.blue.total_arms+'/'+bt;
  if(rt2) rt2.textContent=res.red.total_arms+'/'+rt;
}

function _renderSingleResult(res) {
  document.getElementById('sim-stat-box').style.display='block';
  document.getElementById('sim-log-box').style.display='block';
  const isBlue=res.winner.includes('攻方');
  const isRed=res.winner.includes('守方');
  const wc=isBlue?'#5aaaf0':isRed?'#e05050':'#c8a044';
  const wl=isBlue?'攻方胜利':isRed?'守方胜利':'平  局';
  const side=(s,label,color)=> {
    const cards=s.heros.map(h=>{
      const tot=h.arms+(h.hurt||0);
      const pct=tot>0?Math.round(h.arms/tot*100):0;
      const hd=(_simHeroes||[]).find(x=>x.name===h.name)||{};
      const img=hd.id?_heroImg(hd.id):'';
      const alive=h.arms>0;
      const cc=SIM_CAMP_COL[hd.camp]||color;
      return `<div style="display:flex;gap:6px;align-items:center;
        background:#0a0e16;border:1px solid ${alive?cc:'#1e2e40'};
        border-radius:4px;padding:6px 8px;opacity:${alive?1:.5}">
        ${img?`<img src="${img}" style="width:36px;height:48px;
          object-fit:cover;object-position:left top;
          border-radius:3px;border:1px solid ${cc};flex-shrink:0"
          onerror="this.style.display='none'">`:''}
        <div style="flex:1;min-width:0">
          <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:3px">
            <span style="font-size:.72rem;font-weight:700;color:#e4dd9e">${esc(h.pos)}·${esc(h.name)}</span>
            <span style="font-size:.58rem;padding:1px 5px;border-radius:2px;
              background:${alive?'#46b06e22':'#e0505022'};
              color:${alive?'#46b06e':'#e05050'}">${alive?'存活':'阵亡'}</span>
          </div>
          <div style="font-size:.6rem;color:#5a7a9a;margin-bottom:3px">
            兵力<b style="color:#c0d8f0;margin:0 4px">${fmt(h.arms)}</b>
            伤兵<b style="color:#c8a044;margin:0 4px">${fmt(h.hurt||0)}</b>
          </div>
          <div style="height:2px;background:#0a1020;border-radius:1px;overflow:hidden">
            <div style="height:100%;width:${pct}%;background:${cc};transition:width .8s"></div>
          </div>
        </div>
      </div>`;
    }).join('');
    return `<div style="flex:1;min-width:160px">
      <div style="font-size:.65rem;color:${color};
        border-left:2px solid ${color};padding-left:6px;
        margin-bottom:6px;letter-spacing:.1em">${label}</div>
      <div style="display:flex;flex-direction:column;gap:4px">${cards}</div>
    </div>`;
  };
  document.getElementById('sim-stat-cards').innerHTML=`
    <div style="width:100%;display:flex;flex-direction:column;gap:8px">
      <div style="display:flex;align-items:center;justify-content:center;padding:8px;
        background:${wc}12;border:1px solid ${wc}44;border-radius:5px">
        <span style="font-size:.95rem;font-weight:700;color:${wc};
          letter-spacing:.3em;text-shadow:0 0 10px ${wc}60">${wl}</span>
        <span style="font-size:.65rem;color:#4a5a6a;margin-left:12px">第${res.rounds_played}回合</span>
      </div>
      <div style="display:flex;gap:8px;flex-wrap:wrap">
        ${side(res.blue,'⚔ 攻方','#5aaaf0')}${side(res.red,'🛡 守方','#e05050')}
      </div>
    </div>`;
  const logEl=document.getElementById('sim-log-content');
  logEl.innerHTML=(res.records||[]).map(line=>{
    let c='#3a4a5a';
    if(line.includes('=====')) c='#e4dd9e';
    else if(line.includes('发动')||line.includes('追击')||line.includes('效果')) c='#3ab8c8';
    else if(line.includes('损失')||line.includes('阵亡')||line.includes('混乱')) c='#c06060';
    else if(line.includes('恢复')) c='#46b06e';
    else if(line.includes('【')) c='#a0b0c0';
    const isRound=line.includes('=====');
    const isSub=line.startsWith('  ');
    return `<div style="color:${c};padding:${isRound?'4px 0':'1px 0'};
      ${isRound?'text-align:center;background:#0a0e16;margin:2px 0;letter-spacing:.2em':''}
      ${isSub?'padding-left:16px':''}">${esc(line)}</div>`;
  }).join('');
  logEl.scrollTop=0;
}

function _renderMultiResult(r) {
  document.getElementById('sim-stat-box').style.display='block';
  document.getElementById('sim-log-box').style.display='none';
  document.getElementById('sim-stat-cards').innerHTML=`
    <div style="width:100%;display:flex;flex-direction:column;gap:8px">
      <div style="display:flex;gap:6px;flex-wrap:wrap">
        <div style="flex:1;min-width:90px;background:#0a0e16;border:1px solid #5aaaf044;
          border-radius:4px;padding:8px;text-align:center">
          <div style="font-size:1.5rem;font-weight:700;color:#5aaaf0">${r.blue_rate}%</div>
          <div style="font-size:.6rem;color:#4a6a8a">攻方胜率 (${r.blue_wins})</div>
        </div>
        <div style="flex:1;min-width:90px;background:#0a0e16;border:1px solid #e0505044;
          border-radius:4px;padding:8px;text-align:center">
          <div style="font-size:1.5rem;font-weight:700;color:#e05050">${r.red_rate}%</div>
          <div style="font-size:.6rem;color:#6a4a4a">守方胜率 (${r.red_wins})</div>
        </div>
        <div style="flex:1;min-width:90px;background:#0a0e16;border:1px solid #c8a04444;
          border-radius:4px;padding:8px;text-align:center">
          <div style="font-size:1.5rem;font-weight:700;color:#c8a044">${r.draw_rate}%</div>
          <div style="font-size:.6rem;color:#6a6a4a">平局 (${r.draws})</div>
        </div>
      </div>
      <div style="height:24px;display:flex;gap:1px;border-radius:3px;overflow:hidden">
        <div style="flex:${r.blue_wins||.01};background:linear-gradient(90deg,#2a5a90,#5aaaf0);
          display:flex;align-items:center;justify-content:center;
          font-size:.65rem;color:#fff;font-weight:700">${r.blue_rate>8?r.blue_rate+'%':''}</div>
        <div style="flex:${r.draws||.01};background:linear-gradient(90deg,#705020,#c8a044);
          display:flex;align-items:center;justify-content:center;font-size:.6rem;color:#fff">${r.draw_rate>8?r.draw_rate+'%':''}</div>
        <div style="flex:${r.red_wins||.01};background:linear-gradient(270deg,#902020,#e05050);
          display:flex;align-items:center;justify-content:center;
          font-size:.65rem;color:#fff;font-weight:700">${r.red_rate>8?r.red_rate+'%':''}</div>
      </div>
      <div style="display:flex;justify-content:space-between;font-size:.58rem;color:#3a4a5a;padding:0 3px">
        <span style="color:#5aaaf0">⚔ 攻方</span><span>平局</span><span style="color:#e05050">守方 🛡</span>
      </div>
    </div>`;
}

function simToggleLog() {
  const box=document.getElementById('sim-log-content');
  const btn=document.getElementById('sim-log-toggle');
  if(!box||!btn) return;
  const h=box.style.display==='none';
  box.style.display=h?'':'none';
  btn.textContent=h?'收起':'展开';
}
