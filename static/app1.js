const API='';
let _battles=0,_wx=0,_players=new Set(),_sync=0,_evtCnt=0;

const RESULT_MAP={0:'平局',1:'攻方胜',2:'守方胜',3:'攻方溃',4:'守方溃',5:'双溃',6:'守方胜(NPC)',7:'攻方胜',8:'攻方溃',9:'守方溃',10:'平局',11:'攻方胜',12:'守方胜',13:'攻方溃',14:'守方溃',15:'双溃'};
const FIGHT_MAP={0:'野战',1:'援军',2:'援军',11:'攻城',27:'宝物',33:'大城',35:'援军',80:'攻城',102:'攻城',140:'攻城',141:'攻城',184:'攻城',194:'攻城',209:'攻城',224:'攻城'};

function resultDesc(r,d){return r===0?'败':r===1?'胜':String(r);}
function fightDesc(f,d){return String(f);}

function esc(s){return String(s||'').replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');}
function fmt(n){return Number(n||0).toLocaleString();}
function showToast(m,c='var(--green)'){const t=document.getElementById('toast');t.textContent=m;t.style.borderColor=c;t.style.color=c;t.style.opacity='1';setTimeout(()=>t.style.opacity='0',3000);}

function switchTab(i,el){
  document.querySelectorAll('.page').forEach(p=>p.classList.remove('active'));
  const target=document.getElementById('tab'+i);
  if(target) target.classList.add('active');
  document.querySelectorAll('nav button').forEach(b=>b.classList.remove('active'));
  el.classList.add('active');
  localStorage.setItem('lastTab', i);
  if(i===1 && typeof loadRanking==='function') loadRanking();
  else if(i===2 && typeof loadWuxun==='function') loadWuxun();
  else if(i===3 && typeof loadPower==='function') loadPower();
  else if(i===4 && typeof loadAttendance==='function') loadAttendance();
  else if(i===6 && typeof loadAnalysis==='function') loadAnalysis();
  else if(i===7){ if(typeof loadTeams==='function') loadTeams(); if(typeof loadPlayerBattleTeams==='function') loadPlayerBattleTeams(); }
  else if(i===10 && typeof loadBattlesAll==='function') loadBattlesAll(1);
  else if(i===11 && typeof loadTeamStats==='function') loadTeamStats();
  else if(i===12 && typeof loadMapStats==='function') loadMapStats();
  else if(i===13 && typeof loadPlayerStats==='function') loadPlayerStats();
  else if(i===14 && typeof loadTeamUsers==='function') loadTeamUsers();
  else if(i===15 && typeof loadGroupWu==='function') loadGroupWu();
  else if(i===16 && typeof loadTasks==='function') loadTasks();
  else if(i===17 && typeof loadBattleField==='function') loadBattleField();
  else if(i===22 && typeof loadHeroCombo==='function') loadHeroCombo();
  else if(i===23 && typeof loadTeamReport==='function') loadTeamReport('all');
  else if(i===24 && typeof initSimulator==='function') initSimulator();
  else if(i===18 && typeof loadUnionList==='function') loadUnionList();
  else if(i===19 && typeof loadAnnouncements==='function') loadAnnouncements();
  else if(i===20 && typeof loadZonePlayers==='function') loadZonePlayers();
}

// 页面加载时恢复上次的 tab
document.addEventListener('DOMContentLoaded', ()=>{
  const last = parseInt(localStorage.getItem('lastTab'));
  if(!isNaN(last) && last >= 0){
    const btns = document.querySelectorAll('nav button');
    // 找到对应 tab 的按钮（通过 onclick 属性匹配）
    const btn = [...btns].find(b=>b.onclick&&b.onclick.toString().includes(`switchTab(${last},`)&&b.style.display!=='none');
    if(btn) switchTab(last, btn);
  }
});

setInterval(()=>{document.getElementById('hc-time').textContent=new Date().toLocaleTimeString('zh-CN',{hour12:false});},1000);

async function apiFetch(url,opts){
  try{
    const finalOpts = Object.assign({cache:'no-store'}, opts||{});
    const r=await fetch(API+url,finalOpts);
    return await r.json();
  }catch(e){
    showToast('请求失败: '+e,'var(--red)');
    return null;
  }
}

// ===== 战报详情弹窗 =====
function openModal(){document.getElementById('modal-overlay').style.display='flex';}
function closeModal(){document.getElementById('modal-overlay').style.display='none';}
document.addEventListener('keydown',e=>{if(e.key==='Escape')closeModal();});
document.addEventListener('DOMContentLoaded',()=>{
  document.getElementById('modal-overlay').addEventListener('click',e=>{if(e.target.id==='modal-overlay')closeModal();});
  document.getElementById('srch-battle').addEventListener('input',applyBattleSearch);
  loadProfiles();
});

async function loadProfiles(){
  const data = await apiFetch('/api/profiles');
  if(!data) return;
  const sel = document.getElementById('profile-select');
  // 临时移除 onchange 防止重填时误触发
  const prevOnchange = sel.onchange;
  sel.onchange = null;
  sel.innerHTML = '';
  data.profiles.forEach(p=>{
    const opt = document.createElement('option');
    opt.value = p.profile_id;
    const label = p.role_name ? `${p.role_name} (${p.server_name})` : p.profile_id;
    opt.textContent = label;
    if(p.profile_id === data.current_id) opt.selected = true;
    sel.appendChild(opt);
  });
  sel.onchange = prevOnchange;
}

async function switchProfile(pid){
  if(!pid) return;
  const r = await apiFetch('/api/switch_profile', {
    method:'POST',
    headers:{'Content-Type':'application/json'},
    body: JSON.stringify({profile_id: pid})
  });
  if(r && r.ok){
    showToast(`已切换账号: ${r.profile.role_name||pid}`, 'var(--cyan)');
    // 切换账号后刷新页面数据
    setTimeout(()=> location.reload(), 800);
  } else {
    showToast('切换失败', 'var(--red)');
  }
}

async function showBattleDetail(bid){
  document.getElementById('modal-title').textContent='载入中...';
  document.getElementById('modal-body').innerHTML='<div style="color:var(--text2);text-align:center;padding:30px">加载中...</div>';
  openModal();
  const r=await apiFetch(`/api/battles_v2/${bid}`);
  if(!r||r.error){document.getElementById('modal-body').innerHTML='<div style="color:var(--red)">加载失败</div>';return;}
  const b=r.battle, heroes=r.heroes||[], ex=r.extra||{};
  const rc=resultDesc(b.result,b.result_desc);
  const rcCls=b.result===1?'badge-win':b.result===0?'badge-lose':'badge-draw';
  const ft=String(b.fight_type);
  document.getElementById('modal-title').textContent=`⚔ 战报 #${bid}`;

  // 解析技能
  const skillMap=typeof SKILL_CFG!=='undefined'?SKILL_CFG:(typeof SKILL_MAP!=='undefined'?SKILL_MAP:{});
  function parseSkills(s){
    const res={};
    if(!s)return res;
    s.split(';').forEach(part=>{
      const p=part.trim().split(',');
      if(p.length>=2&&parseInt(p[0])){
        const pos=parseInt(p[0]);
        const sks=[];
        for(let i=1;i<p.length;i+=2){
          const sid=parseInt(p[i]);
          if(sid>0){
            const sc = skillMap[String(sid)]||skillMap[sid];
            const sname = sc ? (sc.name||(sc.type?sc.type+' ':'')+sid) : ('技能'+sid);
            sks.push(sname);
          }
        }
        if(sks.length)res[pos]=sks;
      }
    });
    return res;
  }
  // 解析advance红度：攻方跳过第0段取1/2/3，守方倒序取2/1/0（与stzbHelper一致）
  function parseAtkAdvance(s){
    if(!s)return [0,0,0];
    const segs=s.split(';').filter(x=>x.trim());
    // 攻方：跳过第0段，取第1/2/3段的第1个数字
    const r=[0,0,0];
    for(let i=1;i<=3;i++){
      if(segs[i])r[i-1]=parseInt(segs[i].split(',')[0])||0;
    }
    return r;
  }
  function parseDefAdvance(s){
    if(!s)return [0,0,0];
    const segs=s.split(';').filter(x=>x.trim());
    // 守方：跳过第3段，倒序：i=0→hero3, i=1→hero2, i=2→hero1
    const r=[0,0,0];
    if(segs[0])r[2]=parseInt(segs[0].split(',')[0])||0; // hero3
    if(segs[1])r[1]=parseInt(segs[1].split(',')[0])||0; // hero2
    if(segs[2])r[0]=parseInt(segs[2].split(',')[0])||0; // hero1
    return r;
  }
  // 兼容旧版API：extra 里没有时从 battle 对象取
  const skillInfo=ex.all_skill_info||b.all_skill_info||'';
  const atkAdvStr=ex.atk_advance||b.atk_advance||b.attack_advance||'';
  const defAdvStr=ex.def_advance||b.def_advance||b.defend_advance||'';
  const skills=parseSkills(skillInfo);
  const atkAdv=parseAtkAdvance(atkAdvStr);
  const defAdv=parseDefAdvance(defAdvStr);
  // 从 extra 或 battle 取各武将红度（用??避免0被||短路）
  const _gs=(a,c)=>(a!=null?a:(c!=null?c:0));
  const atkStars=[0,
    _gs(ex.atk_hero1_star,b.atk_hero1_star),
    _gs(ex.atk_hero2_star,b.atk_hero2_star),
    _gs(ex.atk_hero3_star,b.atk_hero3_star)];
  const defStars=[0,
    _gs(ex.def_hero1_star,b.def_hero1_star),
    _gs(ex.def_hero2_star,b.def_hero2_star),
    _gs(ex.def_hero3_star,b.def_hero3_star)];

  // 基本信息
  const isNPC=ex.is_npc||0;
  const weather=ex.weather?['','晴','雨','雪','雾'][ex.weather]||'':'无';
  const night=ex.in_night?'夜战':'';
  let html=`
  <div style='display:grid;grid-template-columns:1fr 1fr;gap:6px 16px;font-size:.78rem;margin-bottom:14px;padding-bottom:12px;border-bottom:1px solid var(--border)'>
    <div><span style='color:var(--text2)'>时间</span> <b>${esc(b.time_str||'')}</b></div>
    <div><span style='color:var(--text2)'>结果</span> <span class='badge ${rcCls}'>${esc(rc)}</span></div>
    <div><span style='color:var(--text2)'>类型</span> <span class='badge badge-city'>${esc(ft)}</span></div>
    <div><span style='color:var(--text2)'>地块</span> ${esc(ex.wid_name||b.wid_code||'')}</div>
    <div><span style='color:var(--text2)'>攻方</span> <b>${esc(b.atk_name||'')}</b> ${esc(b.atk_union?'['+b.atk_union+']':'')}</div>
    <div><span style='color:var(--text2)'>守方</span> <b>${esc(b.def_name||'')}</b> ${esc(b.def_union?'['+b.def_union+']':'')}</div>
    <div><span style='color:var(--text2)'>武勋</span> <b style='color:var(--gold)'>${fmt(b.atk_gongxun)}</b></div>
    <div><span style='color:var(--text2)'>势力</span> <b style='color:var(--cyan)'>${fmt(b.atk_power)}</b></div>
    ${weather?`<div><span style='color:var(--text2)'>天气</span> ${weather}${night?' '+night:''}</div>`:''}  
    ${isNPC?`<div><span class='badge badge-draw'>NPC</span></div>`:''}
  </div>`;

  // 武将卡片函数
  function heroCard(h,idx,advArr,skillArr,starVal){
    const hero=typeof getHero==='function'?getHero(h.hero_id):{};
    // 优先用 HERO_CFG（stzbHelper 完整武将表）
    const hcfg=(typeof HERO_CFG!=='undefined')?(HERO_CFG[h.hero_id]||HERO_CFG[String(h.hero_id)]||{}):{};
    const name=hcfg.name||hero.name||h.hero_name||('武将'+h.hero_id);
    const country=hcfg.country||'';
    const htype=hcfg.type||'';
    const quality=hcfg.quality||0;
    const avatar=hero.avatar||'';
    const rarity=hero.rarity||'';
    const rarColor={'R':'#8899aa','SR':'#9060d0','SSR':'#c8a044','UR':'#e05050'}[rarity]||'var(--text2)';
    const qualityColor=quality>=5?'#e05050':quality>=4?'#c8a044':'var(--text2)';
    const hpPct=h.max_hp>0?Math.round(h.remain_hp/h.max_hp*100):0;
    const hpColor=hpPct>60?'var(--green)':hpPct>30?'var(--gold)':'var(--red)';
    const hasAdv=advArr&&advArr[idx];
    const star=advArr&&advArr[idx]!=null?advArr[idx]:(starVal||0);
    const skList=skillArr||[];
    const iconId=hcfg.iconId||h.hero_id;
    const imgUrl=`https://g0.gph.netease.com/ngsocial/community/stzb/cn/cards/cut/card_medium_${iconId}.jpg?gameid=g10`;
    const starStr=star>0?'★'.repeat(Math.min(star,9))+(star>9?'+':''):'—';
    const starColor=star>=7?'#e05050':star>=4?'#c8a044':'var(--text2)';
    const borderColor=star>=7?'#e05050':star>=4?'var(--gold)':(hasAdv?'var(--gold)':'var(--border)');
    return `<div style='background:var(--panel2);border:1px solid ${borderColor};border-radius:6px;padding:8px;min-width:140px;max-width:170px;position:relative'>
      <div style='position:absolute;top:4px;right:6px;font-size:.62rem;color:${starColor};letter-spacing:0'>${starStr}</div>
      <div style='display:flex;gap:8px;align-items:center;margin-bottom:6px'>
        <img src='${imgUrl}' style='width:40px;height:40px;border-radius:4px;object-fit:cover;object-position:left top;border:1px solid var(--border)' onerror='this.src="";this.style.display="none"'>
        <div>
          <div style='font-size:.8rem;font-weight:700;color:var(--text)'>${esc(name)}</div>
          <div style='font-size:.65rem;color:${qualityColor}'>${country?country+' ':''}${htype||''} Lv.${h.level||0}</div>
        </div>
      </div>
      <div style='font-size:.68rem;color:var(--text2);margin-bottom:4px;display:flex;justify-content:space-between'><span>HP</span><span style='color:${hpColor}'>${h.remain_hp||0}/${h.max_hp||0}</span></div>
      <div style='height:3px;background:var(--border);border-radius:2px;overflow:hidden;margin-bottom:6px'><div style='height:100%;width:${hpPct}%;background:${hpColor};transition:width .5s'></div></div>
      ${h.damage_taken>0?`<div style='font-size:.67rem;color:var(--red);margin-bottom:4px'>受伤: ${fmt(h.damage_taken)}</div>`:''}
      ${skList.length?`<div style='border-top:1px solid var(--border);margin-top:5px;padding-top:5px'>${skList.map(s=>`<div style='font-size:.65rem;color:var(--cyan);line-height:1.6;display:flex;align-items:center;gap:3px'><span style='color:var(--muted)'>▸</span>${esc(s)}</div>`).join('')}</div>`:''}
    </div>`;
  }

  // 攻方武将（只取 side=atk，按 pos 排序去重）
  const atkHeroes=[...new Map(heroes.filter(h=>h.side==='atk').sort((a,b)=>a.pos-b.pos).map(h=>[h.pos,h])).values()];
  const defHeroes=[...new Map(heroes.filter(h=>h.side==='def').sort((a,b)=>a.pos-b.pos).map(h=>[h.pos,h])).values()];

  if(atkHeroes.length>0||defHeroes.length>0){
    html+=`<div style='margin-bottom:10px'>`;
    if(atkHeroes.length>0){
      html+=`<div style='font-size:.73rem;color:var(--text2);letter-spacing:.1em;margin-bottom:6px'>⚔ 攻方武将</div>`;
      html+=`<div style='display:flex;flex-wrap:wrap;gap:8px;margin-bottom:12px'>`;
      atkHeroes.forEach((h,i)=>{
        html+=heroCard(h,i,atkAdv,[...(skills[i+1]||[])],atkStars[i+1]);
      });
      html+=`</div>`;
    }
    if(defHeroes.length>0){
      html+=`<div style='font-size:.73rem;color:var(--text2);letter-spacing:.1em;margin-bottom:6px'>🛡 守方武将</div>`;
      html+=`<div style='display:flex;flex-wrap:wrap;gap:8px'>`;
      defHeroes.forEach((h,i)=>{
        html+=heroCard(h,i,defAdv,[...(skills[i+4]||[])],defStars[i+1]);
      });
      html+=`</div>`;
    }
    html+=`</div>`;
  } else {
    html+=`<div style='color:var(--text2);font-size:.75rem'>暂无武将数据</div>`;
  }
  document.getElementById('modal-body').innerHTML=html;
}

// SSE
function connectSSE(){
  const dot=document.getElementById('cdot'),st=document.getElementById('cstat');
  const es=new EventSource(API+'/api/stream');
  es.onopen=()=>{dot.className='conn-dot live';st.textContent='实时';showToast('已连接实时数据流');refreshAll();};
  es.onmessage=(e)=>{
    let evt;try{evt=JSON.parse(e.data);}catch{return;}
    addEvtFeed(evt);
    if(evt.type==='battle')onBattle(evt.data);
    else if(evt.type==='db_sync'){_sync++;document.getElementById('sc0-sync').textContent=_sync;}
    else if(evt.type==='chat_834'||evt.type==='battle_notice'){if(typeof onMsg834==='function')onMsg834(evt);}
    else if(evt.type==='profile_changed'){
      const p=evt.data||{};
      // 清空内存计数和 feed
      _battles=0;_wx=0;_players=new Set();_sync=0;_evtCnt=0;
      document.getElementById('sc0-battles').textContent=0;
      document.getElementById('hc-battles').textContent=0;
      document.getElementById('sc0-wx').textContent=0;
      document.getElementById('hc-wx').textContent=0;
      document.getElementById('sc0-players').textContent=0;
      document.getElementById('sc0-sync').textContent=0;
      document.getElementById('evt-cnt').textContent='0条';
      const bf=document.getElementById('battle-feed');if(bf)bf.innerHTML='';
      const ef=document.getElementById('evt-feed');if(ef)ef.innerHTML='';
      // 清空所有 tab 表格数据，防止残留旧账号数据
      ['rnk-body','wx-body','pw-body','att-body','team-body','sch-body','sync-body',
       'ana-union','ana-top','ptq-body','cs-body','map-table-body',
       'battles-all-body','team-stats-body','player-stats-body','team-users-body',
       'groupwu-body','tasks-body','union-list-body','announcements-body','zone-players-body'
      ].forEach(id=>{const el=document.getElementById(id);if(el)el.innerHTML='';});
      ['rnk-bars','wx-bars','pw-bars','ana-type','ana-hour','map-cards','map-type-bars'].forEach(id=>{const el=document.getElementById(id);if(el)el.innerHTML='';});
      showToast(`切换账号: ${p.role_name||''} @ ${p.server_name||''}`,'var(--cyan)');
      // 重新拉取所有数据
      if(typeof refreshAll==='function')setTimeout(refreshAll,500);
    }
  };
  es.onerror=()=>{dot.className='conn-dot';st.textContent='断开';es.close();setTimeout(connectSSE,4000);};
}

function onBattle(b){
  // 过滤无效战报：atk_name为空或wid=0且无wid_code的，以及NPC战报
  if(!b.atk_name&&!b.def_name&&!b.def_union)return;
  if(!b.atk_name&&b.wid===0)return;
  if(b.is_npc)return;
  _battles++;_wx+=(b.atk_gongxun||0);
  if(b.atk_name)_players.add(b.atk_name);
  document.getElementById('sc0-battles').textContent=_battles;
  document.getElementById('hc-battles').textContent=_battles;
  document.getElementById('sc0-wx').textContent=fmt(_wx);
  document.getElementById('hc-wx').textContent=fmt(_wx);
  document.getElementById('sc0-players').textContent=_players.size;
  addBattleFeed(b);
}

function addBattleFeed(b){
  const f=document.getElementById('battle-feed');
  const rc=resultDesc(b.result,b.result_desc);
  const cls=b.result===1?'badge-win':b.result===0?'badge-lose':'badge-draw';
  const ftName=fightDesc(b.fight_type,b.fight_type_name);
  const ftCls=b.fight_type===80||b.fight_type===33?'badge-city':b.fight_type===0?'badge-field':'';
  const d=document.createElement('div');d.className='feed-item';d.style.cursor='pointer';
  d.dataset.name=(b.atk_name||'').toLowerCase();
  d.title='点击查看详情';
  d.addEventListener('click',()=>{if(b.battle_id)showBattleDetail(b.battle_id);});
  const wxStr=b.atk_gongxun>0?`wx=${fmt(b.atk_gongxun)}`:'';
  const heroStr=(b.heroes||[]).filter(Boolean).join(' · ');
  d.innerHTML=`<span class='feed-time'>${esc(b.time_str||'')}</span><div class='feed-body'><b>${esc(b.atk_name||'')}</b> <span class='badge ${cls}'>${esc(rc)}</span> <span class='badge ${ftCls}'>${esc(ftName)}</span>${wxStr?` <span style='color:var(--gold)'>${wxStr}</span>`:''} <span style='color:var(--text2)'>vs ${esc(b.def_union||b.def_name||'')}</span>${heroStr?`<br><span style='color:var(--text2);font-size:.7rem'>🗡 ${esc(heroStr)}</span>`:''}</div>`;
  f.prepend(d);if(f.children.length>300)f.removeChild(f.lastChild);
  applyBattleSearch();
}

function addEvtFeed(evt){
  _evtCnt++;document.getElementById('evt-cnt').textContent=_evtCnt+'条';
  const f=document.getElementById('evt-feed');
  const d=document.createElement('div');d.className='feed-item';
  const ic={battle:'⚔',db_sync:'🗄',notification:'📢',ping:'💓'}[evt.type]||'•';
  let body='';
  if(evt.type==='battle'){const b=evt.data||{};body=`${esc(b.atk_name||'')} ${esc(b.result_desc||'')} wx=${b.atk_gongxun||0}`;}
  else if(evt.type==='db_sync')body=(evt.data?.tables||[]).map(t=>`<span style='color:var(--cyan)'>${t}</span>`).join(' ');
  else if(evt.type==='notification'){const n=evt.data||{};body=`type=${n.type} ${esc(n.name1||'')} ${esc(n.name2||'')}`;}
  else body=JSON.stringify(evt.data||{}).slice(0,60);
  d.innerHTML=`<span class='feed-time'>${ic} ${evt.ts||''}</span><div class='feed-body'>${body}</div>`;
  f.prepend(d);if(f.children.length>200)f.removeChild(f.lastChild);
}

function applyBattleSearch(){
  const q=(document.getElementById('srch-battle').value||'').toLowerCase();
  document.querySelectorAll('#battle-feed .feed-item').forEach(el=>{
    el.style.display=(!q||(el.dataset.name||'').includes(q))?'':'none';
  });
}
