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
const FT_MAP={0:'野战',1:'援军',2:'援军',3:'野战',7:'野战',11:'攻城',27:'宝物',33:'大城',35:'援军',80:'攻城',102:'攻城',140:'攻城',141:'攻城',184:'攻城',194:'攻城',209:'攻城',224:'攻城'};
const FT_COLOR={0:'var(--green)',3:'var(--green)',7:'var(--green)',11:'var(--red)',33:'var(--red)',80:'var(--red)',102:'var(--red)',140:'var(--red)',141:'var(--red)',1:'var(--cyan)',2:'var(--cyan)',35:'var(--cyan)'};

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
    return;
  }
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
    b.innerHTML+=`<tr>
      <td class='${cls}' style='font-family:Share Tech Mono,monospace'>${i<3?['🥇','🥈','🥉'][i]:(i+1)}</td>
      <td><div style='display:flex;align-items:flex-end;flex-wrap:wrap;gap:2px'>${heroHtml}</div></td>
      <td style='font-family:Share Tech Mono,monospace'>${r.cnt}</td>
      <td style='color:var(--green)'>${r.wins||0}</td>
      <td style='${wrStyle}'>${r.win_rate||0}%</td>
    </tr>`;
  });
}

function exportTeamsCSV(){
  const rows=[['排名','武将组合','使用次数','胜场','胜率']];
  document.querySelectorAll('#team-body tr').forEach((tr,i)=>{
    const cells=[...tr.querySelectorAll('td')];
    if(cells.length>=5){
      // 武将组合取 title 里的文字拼接
      const heroSpans=[...cells[1].querySelectorAll('span:last-child')];
      const heroStr=heroSpans.map(s=>s.textContent).join('+');
      rows.push([cells[0].textContent.replace(/[🥇🥈🥉]/,'').trim()||String(i+1), heroStr, cells[2].textContent, cells[3].textContent, cells[4].textContent]);
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
    b.innerHTML=`<tr><td colspan=6 style='color:var(--text2);text-align:center;padding:20px'>${name?'未找到数据':'请输入玩家名查询'}</td></tr>`;
    return;
  }
  if(cnt)cnt.textContent=`共${data.length}条`;
  data.forEach((r,i)=>{
    const cls=i<3?`rank-${i+1}`:'';
    const sideLabel=r.side==='atk'?`<span style='color:var(--red)'>攻</span>`:`<span style='color:var(--blue)'>守</span>`;
    const wrStyle=r.win_rate>=60?'color:var(--green)':r.win_rate>=40?'color:var(--gold)':'color:var(--red)';
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
      <td style='${wrStyle}'>${r.win_rate}%</td>
    </tr>`;
  });
}

// Custom Scores
async function loadScores(){
  const u=document.getElementById('cs-union').value;
  const data=await apiFetch(`/api/custom_scores?union=${encodeURIComponent(u)}`);
  const b=document.getElementById('cs-body');b.innerHTML='';
  (data||[]).forEach((r,i)=>{
    const cls=i<3?`rank-${i+1}`:'';
    b.innerHTML+=`<tr><td class='${cls}'>${i+1}</td><td>${esc(r.player_name)}</td><td>${esc(r.union_name||'')}</td><td class='${cls}'>${(r.score||0).toFixed(1)}</td><td>${r.battles}</td><td>${r.wins}</td><td>${fmt(r.gongxun_total)}</td><td>${r.main_city_cnt}</td></tr>`;
  });
}
async function recalcScores(){
  const r=await apiFetch('/api/custom_scores/recalc',{method:'POST',headers:{'Content-Type':'application/json'},body:'{}' });
  if(r&&r.ok){showToast(`积分计算完成: ${r.updated}人`);loadScores();}
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
  if(typeof loadBattleField==='function')loadBattleField();
  if(typeof loadUnionList==='function')loadUnionList();
  if(typeof loadAnnouncements==='function')loadAnnouncements();
  if(typeof loadZonePlayers==='function')loadZonePlayers();
}

setInterval(refreshWriterStats,15000);

loadHistory().then(()=>connectSSE());

// ===== 城池地图 =====
let _mapData = [];
async function loadMapStats(){
  const r = await apiFetch('/api/map_stats');
  if(!r) return;
  const CTYPE = {8:'攻城营垒',11:'斥候营地',12:'大型要塞',13:'关卡',14:'皇城',17:'联盟城池',20:'战场',76:'采矿场',77:'采矿场',78:'采矿场',70:'铁矿场',71:'铜矿场',72:'银矿场',73:'金矿场',74:'玉矿场',75:'石矿场'};
  const CCOLOR = {8:'var(--blue)',11:'var(--cyan)',12:'var(--gold)',13:'var(--purple)',14:'var(--red)',17:'var(--green)',20:'var(--text2)',76:'#a0714f',77:'#a0714f',78:'#a0714f',70:'#888',71:'#b87333',72:'#c0c0c0',73:'#ffd700',74:'#00e5ff',75:'#aaa'};
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
  const CTYPE = {8:'攻城营垒',11:'斥候营地',12:'大型要塞',13:'关卡',14:'皇城',17:'联盟城池',20:'战场',76:'采矿场',70:'铁矿场',71:'铜矿场',72:'银矿场',73:'金矿场',74:'玉矿场',75:'石矿场'};
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
  const side=document.getElementById('tm-side').value;
  const url=`/api/player_teams_stats?player=${encodeURIComponent(player)}&side=${side}&limit=200`;
  const data=await apiFetch(url);
  if(!data)return;
  document.getElementById('tm-total').textContent=`共${data.length}条`;
  const b=document.getElementById('tm-body');b.innerHTML='';
  data.forEach((r,i)=>{
    const cls=i<3?`rank-${i+1}`:'';
    const sideLabel=r.side==='atk'?'<span style="color:var(--red)">攻</span>':'<span style="color:var(--blue)">守</span>';
    const wrStyle=r.win_rate>=60?'color:var(--green)':r.win_rate>=40?'color:var(--gold)':'color:var(--red)';
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
      <td style='${wrStyle}'>${r.win_rate}%</td>
    </tr>`;
  });
}

// ===== 玩家队伍一览 =====
async function loadPlayerBattleTeams(){
  const player=document.getElementById('pbt-player').value;
  const side=document.getElementById('pbt-side').value;
  const url=`/api/player_battle_teams?player=${encodeURIComponent(player)}&side=${encodeURIComponent(side)}`;
  const b=document.getElementById('pbt-body');
  const countEl=document.getElementById('pbt-count');
  b.innerHTML=`<tr><td colspan=7 style='color:var(--cyan);text-align:center;padding:20px'>⏳ 加载中...</td></tr>`;
  countEl.textContent='';
  const data=await apiFetch(url);
  if(!data){b.innerHTML=`<tr><td colspan=7 style='color:var(--red);text-align:center;padding:20px'>请求失败</td></tr>`;return;}
  b.innerHTML='';
  if(!data.length){
    countEl.textContent='0条';
    b.innerHTML=`<tr><td colspan=7 style='color:var(--text2);text-align:center;padding:20px'>暂无数据</td></tr>`;
    return;
  }
  countEl.textContent=`共${data.length}条`;
  // 分批渲染：每批80条，用 requestAnimationFrame 避免阻塞主线程
  let rowIdx=0;
  function buildRowHtml(r,i){
    const wrStyle=r.win_rate>=60?'color:var(--green)':r.win_rate>=40?'color:var(--gold)':'color:var(--red)';
    const isNewPlayer=i===0||data[i-1].player_name!==r.player_name;
    if(isNewPlayer) rowIdx++;
    const rowBg=rowIdx%2===0?'background:var(--panel2)':'';
    const heroIds=(r.heroes_str||'').split('+').filter(Boolean);
    const heroStars=r.hero_stars||[0,0,0];
    const skillIds=(r.skills||'').split(',').filter(Boolean);
    const heroLevels=(r.hero_levels||'').split(',').map(l=>Number(l)||0);
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
      return `<span style='display:inline-flex;flex-direction:column;align-items:flex-start;background:var(--panel2);border:1px solid ${countryColor};border-radius:4px;padding:3px 7px 4px 7px;margin-right:5px;margin-bottom:2px;min-width:64px'><span style='display:flex;align-items:center;color:${countryColor};font-size:.70rem;font-weight:bold;white-space:nowrap'>${esc(name)}${starStr}${lvStr}</span><span style='display:flex;flex-wrap:wrap;margin-top:2px'>${skillsHtml}</span></span>`;
    }).join('');
    const unionCell=isNewPlayer?(r.union&&r.clan_name?esc(r.union)+'·'+esc(r.clan_name):esc(r.union||r.clan_name||'')):'';
    return `<tr style='${rowBg}'><td style='color:var(--text2);font-size:.72rem'>${i+1}</td><td>${isNewPlayer?`<b>${esc(r.player_name||'')}</b>`:''}</td><td style='font-size:.72rem;color:var(--text2)'>${unionCell}</td><td style='max-width:480px'>${heroHtml}</td><td style='font-family:Share Tech Mono,monospace'>${r.cnt}</td><td style='color:var(--green)'>${r.wins}</td><td style='${wrStyle}'>${r.win_rate}%</td></tr>`;
  }
  const BATCH=80;
  function renderBatch(start){
    const end=Math.min(start+BATCH,data.length);
    const tmp=document.createElement('tbody');
    let html='';
    for(let i=start;i<end;i++) html+=buildRowHtml(data[i],i);
    tmp.innerHTML=html;
    const frag=document.createDocumentFragment();
    while(tmp.firstChild) frag.appendChild(tmp.firstChild);
    b.appendChild(frag);
    if(end<data.length){
      countEl.textContent=`共${data.length}条（渲染中 ${end}/${data.length}）`;
      requestAnimationFrame(()=>renderBatch(end));
    } else {
      countEl.textContent=`共${data.length}条`;
    }
  }
  renderBatch(0);
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
let _currentTaskDetail = null;

async function loadTasks(){
  const data = await apiFetch('/api/tasks');
  const b = document.getElementById('task-body'); b.innerHTML='';
  const cards = document.getElementById('task-cards'); cards.innerHTML='';
  const cnt = document.getElementById('task-count');
  if(cnt) cnt.textContent=`共${(data||[]).length}个任务`;
  if(!data||!data.length){
    b.innerHTML=`<tr><td colspan=7 style='color:var(--text2);text-align:center;padding:20px'>暂无任务，点击「新建任务」创建</td></tr>`;
    return;
  }
  data.forEach(t=>{
    const timeStr = t.time ? new Date(t.time*1000).toLocaleString('zh-CN') : '-';
    const groups = (t.target_groups||[]).join('、') || '全员';
    const statusBadge = t.status===1
      ? `<span class='badge badge-win'>已统计</span>`
      : `<span class='badge badge-draw'>待考勤</span>`;
    const posLabel = (t.wid_name ? `${esc(t.wid_name)} ` : '') + `<span style='font-size:.65rem;color:var(--text2)'>(${esc(t.pos_xy||t.pos)})</span>`;
    b.innerHTML+=`<tr>
      <td><b>${esc(t.name)}</b> ${statusBadge}</td>
      <td style='font-family:Share Tech Mono,monospace;font-size:.72rem'>${posLabel}</td>
      <td style='font-size:.72rem'>${timeStr}</td>
      <td style='font-size:.72rem;color:var(--cyan)'>${esc(groups)}</td>
      <td>${t.target_user_num}</td>
      <td style='color:var(--${t.complete_user_num>0?"green":"text2"})'>${t.complete_user_num}</td>
      <td>
        <button class='btn' style='font-size:.68rem;padding:2px 6px;border-color:var(--blue);color:var(--blue)' onclick='viewTaskDetail(${t.id})'>考勤详情</button>
        <button class='btn' style='font-size:.68rem;padding:2px 6px;border-color:var(--gold);color:var(--gold)' onclick='doStatistics(${t.id},this)'>开始统计</button>
        <button class='btn' style='font-size:.68rem;padding:2px 6px;border-color:var(--red);color:var(--red)' onclick='deleteTask(${t.id})'>删除</button>
      </td>
    </tr>`;
  });
}

async function viewTaskDetail(tid){
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
  panel.style.display='block';
}

function closeTaskDetail(){
  document.getElementById('task-detail-panel').style.display='none';
  document.getElementById('task-battles-wrap').style.display='none';
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
  wrap.style.display='block';
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

async function doStatistics(tid, btn){
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
  if(!_statsConfirmTid) return;
  const btn = document.getElementById('stats-confirm-btn');
  btn.disabled=true; btn.textContent='统计中...';
  if(_statsConfirmBtn){ _statsConfirmBtn.disabled=true; _statsConfirmBtn.textContent='统计中...'; }
  const r = await apiFetch(`/api/tasks/${_statsConfirmTid}/statistics`,{method:'POST',headers:{'Content-Type':'application/json'},body:'{}'});
  btn.disabled=false; btn.textContent='✅ 确认统计';
  if(_statsConfirmBtn){ _statsConfirmBtn.disabled=false; _statsConfirmBtn.textContent='开始统计'; }
  closeStatsConfirm();
  if(r&&r.ok){ showToast(r.msg,'var(--green)'); loadTasks(); }
  else showToast((r&&r.error)||'统计失败','var(--red)');
}

async function deleteTask(tid){
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

async function loadGroupTags(){
  const data = await apiFetch('/api/team_groups');
  const el = document.getElementById('ct-group-tags');
  if(!el) return;
  el.innerHTML='';
  if(!data||!data.length){ el.innerHTML=`<span style='font-size:.72rem;color:var(--text2)'>暂无分组数据</span>`; return; }
  // 全员按钮
  el.innerHTML+=`<button class='btn' style='font-size:.72rem;padding:2px 8px;border-color:var(--green);color:var(--green)' onclick='setGroupTag("")'>全员</button>`;
  data.forEach(g=>{
    el.innerHTML+=`<button class='btn' style='font-size:.72rem;padding:2px 8px' onclick='toggleGroupTag("${esc(g)}")' data-group='${esc(g)}'>${esc(g)}</button>`;
  });
}

function toggleGroupTag(g){
  const input = document.getElementById('ct-groups');
  const cur = input.value.split(',').map(s=>s.trim()).filter(Boolean);
  const idx = cur.indexOf(g);
  if(idx>=0) cur.splice(idx,1);
  else cur.push(g);
  input.value = cur.join(',');
  // 更新按钮高亮
  document.querySelectorAll('#ct-group-tags button[data-group]').forEach(btn=>{
    const sel = cur.includes(btn.dataset.group);
    btn.style.background = sel?'var(--cyan)':'transparent';
    btn.style.color = sel?'#0a1420':'var(--text)';
  });
}

function setGroupTag(g){
  document.getElementById('ct-groups').value=g;
  document.querySelectorAll('#ct-group-tags button[data-group]').forEach(btn=>{
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
    const CTYPE={8:'攻城营垒',11:'斜候营地',12:'大型要塞',13:'关卡',14:'皇城',17:'联盟城池',20:'战场'};
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

// ===== 联盟列表 (Tab 18) =====
let _ulData = [];
async function loadUnionList(){
  const data = await apiFetch('/api/union_list');
  _ulData = data || [];
  const cards = document.getElementById('ul-cards');
  if(cards){
    const totalPower = _ulData.reduce((s,r)=>s+(r.power||0),0);
    const totalMember = _ulData.reduce((s,r)=>s+(r.total_member||0),0);
    cards.innerHTML=`
      <div class='stat-card'><div class='val'>${_ulData.length}</div><div class='lbl'>联盟数</div></div>
      <div class='stat-card'><div class='val' style='color:var(--gold)'>${fmt(totalPower)}</div><div class='lbl'>总势力值</div></div>
      <div class='stat-card'><div class='val' style='color:var(--cyan)'>${totalMember}</div><div class='lbl'>总人数</div></div>
    `;
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
      <td style='color:var(--text2);font-size:.72rem'>${r.region||''}</td>
      <td style='color:var(--text2);font-size:.68rem'>${updateTime}</td>
    </tr>`;
  });
}

// ===== 游戏公告 (Tab 19) =====
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
async function loadHeroCombo(){
  const min = document.getElementById('combo-min')?.value || 3;
  const cards = document.getElementById('combo-cards');
  const b = document.getElementById('combo-body');
  if(!b) return;
  b.innerHTML = `<tr><td colspan=8 style='text-align:center;color:var(--text2);padding:20px'>计算中...</td></tr>`;
  const data = await apiFetch(`/api/heroes/combo_winrate?min=${min}`);
  if(!data || data.error){
    b.innerHTML = `<tr><td colspan=8 style='text-align:center;color:var(--red);padding:20px'>加载失败</td></tr>`;
    return;
  }
  // 统计卡片
  if(cards){
    const total = data.reduce((s,r)=>s+r.total,0);
    const top = data[0];
    cards.innerHTML = `
      <div class='stat-card'><div class='val' style='color:var(--gold)'>${data.length}</div><div class='lbl'>有效组合数</div></div>
      <div class='stat-card'><div class='val' style='color:var(--cyan)'>${total}</div><div class='lbl'>覆盖战报数</div></div>
      ${top ? `<div class='stat-card'><div class='val' style='color:var(--green);font-size:.9rem'>${top.win_rate}%</div><div class='lbl'>最高胜率组合</div></div>` : ''}
    `;
  }
  if(!data.length){
    b.innerHTML = `<tr><td colspan=8 style='text-align:center;color:var(--text2);padding:20px'>暂无数据（需要有武将出战记录的战报）</td></tr>`;
    return;
  }
  b.innerHTML = data.map((r,i)=>{
    const wr = r.win_rate;
    const barColor = wr>=70?'var(--green)':wr>=50?'var(--gold)':'var(--red)';
    const heroes = r.combo.split('+').map(h=>`<span class='badge' style='background:#1a2535;color:var(--cyan);margin:1px'>${esc(h)}</span>`).join('');
    return `<tr>
      <td style='color:var(--text2);font-family:Share Tech Mono,monospace'>${i+1}</td>
      <td style='max-width:280px'>${heroes}</td>
      <td style='font-family:Share Tech Mono,monospace'>${r.total}</td>
      <td style='color:var(--green);font-weight:600'>${r.win}</td>
      <td style='color:var(--red)'>${r.lose}</td>
      <td style='color:var(--text2)'>${r.draw}</td>
      <td style='font-weight:700;color:${barColor}'>${wr}%</td>
      <td style='min-width:100px'><div style='background:var(--panel2);border-radius:3px;height:8px;overflow:hidden'><div style='width:${wr}%;height:100%;background:${barColor};border-radius:3px;transition:width .4s'></div></div></td>
    </tr>`;
  }).join('');
}

// ===== 团数据 (Tab 23) =====
let _trPeriod = 'all';
let _trData = null;

async function loadTeamReport(period){
  _trPeriod = period || 'all';
  const dim   = document.getElementById('tr-dim')?.value || 'group';
  const group = document.getElementById('tr-group')?.value || '';

  // 高亮按钮
  ['today','yesterday','week','lastweek','all'].forEach(p=>{
    const b = document.getElementById('tr-btn-'+p);
    if(b) b.className = p===_trPeriod ? 'btn btn-primary' : 'btn';
  });

  const tbody = document.getElementById('tr-body');
  const cards = document.getElementById('tr-cards');
  tbody.innerHTML = `<tr><td colspan=10 style='text-align:center;color:var(--text2);padding:20px'>⏳ 统计中...</td></tr>`;

  const url = `/api/team_report?period=${_trPeriod}&dim=${dim}&group=${encodeURIComponent(group)}`;
  const res = await apiFetch(url);
  if(!res){ tbody.innerHTML=`<tr><td colspan=10 style='color:var(--red);text-align:center;padding:20px'>请求失败</td></tr>`; return; }

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
    <div class='stat-card'><div class='val' style='color:var(--gold)'>${s.total_battles||0}</div><div class='lbl'>总战报</div></div>
    <div class='stat-card'><div class='val' style='color:var(--green)'>${s.win_rate||0}%</div><div class='lbl'>胜率</div></div>
    <div class='stat-card'><div class='val' style='color:var(--cyan)'>${s.total_players||0}</div><div class='lbl'>参战人数</div></div>
    <div class='stat-card'><div class='val' style='color:var(--red)'>${s.total_city||0}</div><div class='lbl'>攻城场次</div></div>
    <div class='stat-card'><div class='val' style='color:var(--purple)'>${fmt(s.total_gongxun||0)}</div><div class='lbl'>总功勋</div></div>
  `;

  // 表头
  const isGroup = dim==='group';
  document.getElementById('tr-thead').innerHTML = `<tr>
    <th>#</th>
    <th>${isGroup?'分组':'成员'}</th>
    ${isGroup?`<th>人数</th>`:`<th>分组</th>`}
    <th>战报</th><th>胜</th><th>败</th><th>胜率</th><th>攻城</th><th>功勋</th><th>峰值战力</th>
  </tr>`;
  document.getElementById('tr-table-title').textContent = isGroup ? '📊 分组战斗数据' : '👤 成员战斗数据';

  // 表格行
  tbody.innerHTML = rows.map((r,i)=>{
    const wrColor = r.win_rate>=60?'var(--green)':r.win_rate>=40?'var(--gold)':'var(--red)';
    const bar = `<div style='display:inline-block;width:${r.win_rate||0}%;min-width:2px;height:6px;background:${wrColor};border-radius:3px;vertical-align:middle'></div>`;
    const col2 = isGroup
      ? `<td style='color:var(--text2)'>${r.player_cnt||0}人</td>`
      : `<td><span class='badge' style='color:var(--cyan)'>${esc(r.group_name||'')}</span></td>`;
    return `<tr>
      <td style='color:var(--text2);font-family:Share Tech Mono,monospace'>${i+1}</td>
      <td style='font-weight:600;color:var(--gold2)'>${esc(r.name||'')}</td>
      ${col2}
      <td style='font-family:Share Tech Mono,monospace'>${r.battles}</td>
      <td style='color:var(--green)'>${r.wins}</td>
      <td style='color:var(--red)'>${r.loses}</td>
      <td><span style='color:${wrColor};font-weight:600'>${r.win_rate}%</span> <div style='display:inline-block;width:60px;background:var(--panel2);border-radius:3px;height:6px;vertical-align:middle'>${bar}</div></td>
      <td style='color:var(--cyan)'>${r.city_battles||0}</td>
      <td style='font-family:Share Tech Mono,monospace;color:var(--purple)'>${fmt(r.total_gongxun||0)}</td>
      <td style='font-family:Share Tech Mono,monospace;font-size:.72rem;color:var(--text2)'>${fmt(r.max_power||0)}</td>
    </tr>`;
  }).join('');

  const periodName = {today:'今日',yesterday:'昨日',week:'本周',lastweek:'上周',all:'全部'}[_trPeriod]||'';
  const el = document.getElementById('tr-update-time');
  if(el) el.textContent = `${periodName} · ${rows.length}条 · ${new Date().toLocaleTimeString('zh-CN')}`;
}

function exportTeamReportCSV(){
  if(!_trData) return;
  const dim = document.getElementById('tr-dim')?.value||'group';
  const isGroup = dim==='group';
  const headers = ['#', isGroup?'分组':'成员', isGroup?'人数':'分组', '战报','胜','败','胜率%','攻城','功勋','峰值战力'];
  const rows = (_trData.rows||[]).map((r,i)=>[
    i+1, r.name||'', isGroup?(r.player_cnt||0):(r.group_name||''),
    r.battles, r.wins, r.loses, r.win_rate, r.city_battles||0, r.total_gongxun||0, r.max_power||0
  ]);
  const csv = [headers,...rows].map(r=>r.map(c=>`"${String(c).replace(/"/g,'""')}"`).join(',')).join('\n');
  const blob = new Blob(['\uFEFF'+csv],{type:'text/csv;charset=utf-8'});
  const a = document.createElement('a');
  a.href = URL.createObjectURL(blob);
  a.download = `团数据_${_trPeriod}_${new Date().toISOString().slice(0,10)}.csv`;
  a.click();
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

