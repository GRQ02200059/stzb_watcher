# -*- coding: utf-8 -*-
part3 = r"""

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
"""
with open('static/sim.js','a',encoding='utf-8') as f:
    f.write(part3)
print('part3 ok')

