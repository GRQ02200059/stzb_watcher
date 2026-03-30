<script>
function renderUnion(){
  if(!unionData.length)return;
  const maxP=unionData[0].power||1;
  document.getElementById('s-total').textContent=unionData.length;
  document.getElementById('hc-unions').textContent=unionData.length;
  document.getElementById('s-maxpow').textContent=fmt(unionData[0].power);
  document.getElementById('s-maxname').textContent=unionData[0].name;
  const mm=[...unionData].sort((a,b)=>b.total_member-a.total_member)[0];
  document.getElementById('s-maxmem').textContent=mm.total_member;
  document.getElementById('s-maxmemname').textContent=mm.name;
  const ml=[...unionData].sort((a,b)=>b.level-a.level)[0];
  document.getElementById('s-maxlv').textContent='Lv.'+ml.level;
  document.getElementById('s-maxlvname').textContent=ml.name;
  document.getElementById('union-ts').textContent='共 '+unionData.length+' 个联盟';

  const medals=['\u2460','\u2461','\u2462'];
  const tb=document.getElementById('union-tbody');
  tb.innerHTML=unionData.map((u,i)=>{
    const pct=Math.round(u.power/maxP*100);
    const rncls=i===0?'r1':i===1?'r2':i===2?'r3':'';
    const sym=i<3?medals[i]:i+1;
    return `<tr>
      <td><span class="rn ${rncls}">${sym}</span></td>
      <td><span class="uname">${esc(u.name)}</span></td>
      <td><span class="chip-l">Lv.${u.level}</span></td>
      <td><div class="pbar-w"><div class="pbar"><div class="pbar-f" style="width:${pct}%"></div></div><span class="pval">${fmt(u.power)}</span></div></td>
      <td><span class="chip-m">${u.total_member}人</span></td>
      <td><span class="rn">${u.region}</span></td>
    </tr>`;
  }).join('');

  const top=unionData.slice(0,20),ch=document.getElementById('union-chart');
  ch.innerHTML=top.map((u,i)=>{
    const h=Math.max(8,Math.round(u.power/maxP*200));
    const delay=(i*.04).toFixed(2);
    return `<div class="bi" style="animation-delay:${delay}s"><div class="b" style="height:${h}px" title="${esc(u.name)}: ${fmt(u.power)}"></div><div class="blbl">${esc(u.name.slice(0,4))}</div></div>`;
  }).join('');
}

function renderBattle(){
  const el=document.getElementById('battle-list');
  if(!battleData.length){
    el.innerHTML='<div class="empty">暂无战报数据<br>请在游戏中触发战斗后刷新</div>';
    ['b-total','b-atkwin','b-players','b-unions'].forEach(id=>{
      document.getElementById(id).textContent=id==='b-total'||id==='b-players'||id==='b-unions'?'0':'--';
    });
    document.getElementById('hc-battles').textContent='0';
    document.getElementById('hc-players').textContent='0';
    return;
  }
  const atkWins=battleData.filter(b=>b.result===1).length;
  const players=new Set([...battleData.map(b=>b.attack_name),...battleData.map(b=>b.defend_name)].filter(Boolean));
  const unions=new Set([...battleData.map(b=>b.attack_union_name),...battleData.map(b=>b.defend_union_name)].filter(Boolean));
  document.getElementById('b-total').textContent=battleData.length;
  document.getElementById('b-atkwin').textContent=Math.round(atkWins/battleData.length*100)+'%';
  document.getElementById('b-players').textContent=players.size;
  document.getElementById('b-unions').textContent=unions.size;
  document.getElementById('b-ts').textContent='共 '+battleData.length+' 场';
  document.getElementById('hc-battles').textContent=battleData.length;
  document.getElementById('hc-players').textContent=players.size;

  el.innerHTML=battleData.slice(0,20).map((b,i)=>{
    const win=b.result===1;
    const ah=parseHeroes(b.attack_all_hero_info);
    const dh=parseHeroes(b.defend_all_hero_info);
    const delay=(i*.04).toFixed(2);
    return `<div class="bcard ${win?'win':'lose'}" onclick="showBattle(${i})" style="animation-delay:${delay}s">
      <div class="bh"><span class="bid">#${b.battle_id}</span><span class="bres ${win?'win':'lose'}">${win?'攻方胜':'守方胜'}</span></div>
      <div class="bvs">
        <div class="bside">
          <div class="sname">${esc(b.attack_name||'未知')}</div>
          <div class="sunion">${esc(b.attack_union_name||'无联盟')}</div>
          <div class="sheroes">${ah.slice(0,4).map(h=>{const av=heroAvatar(h);return av?`<span class="hc" style="padding:0;overflow:hidden;display:inline-flex;align-items:center;gap:4px"><img src="${av}" style="width:18px;height:18px;object-fit:cover;flex-shrink:0;" onerror="this.style.display='none'">${heroName(h)}</span>`:`<span class="hc">${heroName(h)}</span>`;}).join('')}</div>
        </div>
        <div class="vs-wrap"><div class="vs-line"></div><div class="vs">VS</div><div class="vs-line"></div></div>
        <div class="bside r">
          <div class="sname">${esc(b.defend_name||'未知')}</div>
          <div class="sunion">${esc(b.defend_union_name||'无联盟')}</div>
          <div class="sheroes r">${dh.slice(0,4).map(h=>{const av=heroAvatar(h);return av?`<span class="hc" style="padding:0;overflow:hidden;display:inline-flex;align-items:center;gap:4px"><img src="${av}" style="width:18px;height:18px;object-fit:cover;flex-shrink:0;" onerror="this.style.display='none'">${heroName(h)}</span>`:`<span class="hc">${heroName(h)}</span>`;}).join('')}</div>
        </div>
      </div>
    </div>`;
  }).join('');

  const defWins=battleData.length-atkWins;
  const ap=Math.round(atkWins/battleData.length*100);
  document.getElementById('winlose-chart').innerHTML=`
    <div style="margin-bottom:20px">
      <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:10px">
        <div style="display:flex;align-items:center;gap:8px">
          <div style="width:8px;height:8px;background:var(--win);border-radius:50%"></div>
          <span style="font-size:11px;color:var(--text2)">攻方胜利</span>
          <span style="font-family:'Share Tech Mono',monospace;font-size:14px;color:var(--win);font-weight:700">${atkWins}</span>
        </div>
        <div style="display:flex;align-items:center;gap:8px">
          <span style="font-family:'Share Tech Mono',monospace;font-size:14px;color:var(--lose);font-weight:700">${defWins}</span>
          <span style="font-size:11px;color:var(--text2)">守方胜利</span>
          <div style="width:8px;height:8px;background:var(--lose);border-radius:50%"></div>
        </div>
      </div>
      <div style="height:12px;background:var(--bg4);border-radius:6px;overflow:hidden;border:1px solid var(--border)">
        <div style="height:100%;width:${ap}%;background:linear-gradient(90deg,var(--win),#27ae60);border-radius:6px;transition:width 1.2s cubic-bezier(.4,0,.2,1);box-shadow:0 0 12px rgba(46,204,113,.3)"></div>
      </div>
      <div style="display:flex;justify-content:space-between;font-family:'Share Tech Mono',monospace;font-size:10px;margin-top:5px">
        <span style="color:var(--win)">${ap}% 攻方</span>
        <span style="color:var(--lose)">${100-ap}% 守方</span>
      </div>
    </div>`;

  const heroCount={};
  battleData.forEach(b=>{
    [...parseHeroes(b.attack_all_hero_info),...parseHeroes(b.defend_all_hero_info)]
      .forEach(h=>{heroCount[h]=(heroCount[h]||0)+1;});
  });
  const topHeroes=Object.entries(heroCount).sort((a,b)=>b[1]-a[1]).slice(0,10);
  const maxH=topHeroes[0]?.[1]||1;
  document.getElementById('hero-freq').innerHTML=topHeroes.map(([id,cnt],i)=>{
    const av=heroAvatar(parseInt(id));
    return`<div class="type-row" style="animation-delay:${(i*.05).toFixed(2)}s">
      <span class="type-lbl" style="display:flex;align-items:center;gap:6px">${av?`<img src="${av}" style="width:22px;height:22px;object-fit:cover;flex-shrink:0;border:1px solid var(--border2);" onerror="this.style.display='none'">`:''}<span>${heroName(parseInt(id))}</span></span>
      <div style="flex:1"><div class="type-bar" style="width:${Math.round(cnt/maxH*100)}%"></div></div>
      <span class="type-cnt">${cnt}场</span>
    </div>`;
  }).join('');
}

function renderOverview(stats,total){
  const sorted=Object.entries(stats).sort((a,b)=>b[1]-a[1]);
  document.getElementById('o-types').textContent=sorted.length;
  document.getElementById('o-files').textContent=total;
  const maxC=sorted[0]?.[1]||1;
  document.getElementById('type-chart').innerHTML=sorted.map(([t,c],i)=>`
    <div class="type-row" style="animation-delay:${(i*.04).toFixed(2)}s">
      <span class="type-lbl" style="font-family:'Share Tech Mono',monospace;font-size:10px;color:var(--gold)">${t}</span>
      <div style="flex:1"><div class="type-bar" style="width:${Math.round(c/maxC*100)}%"></div></div>
      <span class="type-cnt">${c}</span>
    </div>`).join('');
  document.getElementById('type-tbody').innerHTML=Object.entries(MSG_NAMES).map(([t,name])=>`
    <tr>
      <td style="padding-left:18px"><span style="font-family:'Share Tech Mono',monospace;font-size:10px;color:var(--gold)">${t}</span></td>
      <td>${name}</td>
      <td><span class="chip-g">已知</span></td>
    </tr>`).join('');
}

function showBattle(i){
  const b=battleData[i];
  if(!b)return;
  const win=b.result===1;
  const ah=parseHeroes(b.attack_all_hero_info);
  const dh=parseHeroes(b.defend_all_hero_info);
  document.getElementById('modal-title').textContent='战报 #'+b.battle_id+' · '+(win?'攻方胜利':'守方胜利');
  document.getElementById('modal-body').innerHTML=`
    <div class="hero-grid">
      <div class="hside atk">
        <div class="hside-t atk">攻击方 &middot; ${esc(b.attack_name||'未知')}<span style="color:var(--text3);font-weight:400"> [${esc(b.attack_union_name||'无联盟')}]</span></div>
        ${ah.length?ah.map(h=>{const av=heroAvatar(h);const detail=HERO_DETAIL[h]||HERO_DETAIL[String(h)]||{};return`<div class="hero-row"><div class="hav" style="${av?'padding:0;overflow:hidden;':''}">${av?`<img src="${av}" style="width:100%;height:100%;object-fit:cover;" onerror="this.parentNode.innerHTML='将'">`:'将'}</div><div class="hi"><div class="hn">${heroName(h)}</div><div class="hl">${detail.country||''}${detail.quality?'&nbsp;'+detail.quality:''}&nbsp;id:${h}</div></div></div>`}).join(''):'<div style="color:var(--text3);font-size:12px;padding:8px 0">无武将数据</div>'}
      </div>
      <div class="hside def">
        <div class="hside-t def">防守方 &middot; ${esc(b.defend_name||'未知')}<span style="color:var(--text3);font-weight:400"> [${esc(b.defend_union_name||'无联盟')}]</span></div>
        ${dh.length?dh.map(h=>{const av=heroAvatar(h);const detail=HERO_DETAIL[h]||HERO_DETAIL[String(h)]||{};return`<div class="hero-row"><div class="hav" style="${av?'padding:0;overflow:hidden;':''}">${av?`<img src="${av}" style="width:100%;height:100%;object-fit:cover;" onerror="this.parentNode.innerHTML='将'">`:'将'}</div><div class="hi"><div class="hn">${heroName(h)}</div><div class="hl">${detail.country||''}${detail.quality?'&nbsp;'+detail.quality:''}&nbsp;id:${h}</div></div></div>`}).join(''):'<div style="color:var(--text3);font-size:12px;padding:8px 0">无武将数据</div>'}
      </div>
    </div>
    <div class="bmeta">
      <div class="bmi"><div class="bmk">战斗 ID</div><div class="bmv">${b.battle_id}</div></div>
      <div class="bmi"><div class="bmk">城池类型</div><div class="bmv">${b.city_type??'--'}</div></div>
      <div class="bmi"><div class="bmk">攻方 HP</div><div class="bmv">${b.attack_hp??'--'}</div></div>
      <div class="bmi"><div class="bmk">攻方等级</div><div class="bmv">${b.attack_base_level??'--'}</div></div>
      <div class="bmi"><div class="bmk">守方等级</div><div class="bmv">${b.defend_base_level??'--'}</div></div>
      <div class="bmi"><div class="bmk">战斗结果</div><div class="bmv" style="color:${win?'var(--win)':'var(--lose)'}">${win?'攻方胜':'守方胜'}</div></div>
    </div>`;
  document.getElementById('modal-overlay').classList.add('show');
}

function closeModal(){
  document.getElementById('modal-overlay').classList.remove('show');
}
document.getElementById('modal-overlay').addEventListener('click',function(e){
  if(e.target===this)closeModal();
});

// ===== 战报统计 =====
let battleStats=null;

async function loadBattleStats(){
  try{
    const r=await fetch('battle_stats.json');
    if(!r.ok)return;
    battleStats=await r.json();
    renderBattleStats();
  }catch(e){console.log('battle_stats load err',e);}
}

function renderBattleStats(){
  if(!battleStats)return;
  const d=battleStats;
  const total=d.total||0;
  document.getElementById('bs-total').textContent=total;

  // 攻方胜率
  const atkWin=d.result_dist['攻方胜']||0;
  const atkWinPct=total?Math.round(atkWin/total*100):0;
  document.getElementById('bs-atkwin').textContent=atkWinPct+'%';

  // 最热武将
  const heroFreq=Object.entries(d.hero_freq||{}).sort((a,b)=>b[1]-a[1]);
  if(heroFreq.length){
    document.getElementById('bs-tophero').textContent=heroFreq[0][0];
    document.getElementById('bs-tophero-sub').textContent='出场 '+heroFreq[0][1]+' 次';
  }
  document.getElementById('bs-hero-ts').textContent='共 '+heroFreq.length+' 种武将';

  // 最强组合
  const combos=Object.entries(d.atk_combo_top||{}).sort((a,b)=>b[1]-a[1]);
  if(combos.length) document.getElementById('bs-topcombo').textContent=combos[0][0].replace(/\+/g,' + ');

  // 武将出场频次图
  const maxH=heroFreq[0]?.[1]||1;
  document.getElementById('bs-hero-chart').innerHTML=heroFreq.slice(0,30).map(([name,cnt],i)=>{
    const av=Object.entries(HERO_NAMES).find(([id,n])=>n===name)?.[0];
    const imgUrl=av?heroAvatar(parseInt(av)):'';
    return`<div class="type-row" style="animation-delay:${(i*.03).toFixed(2)}s">
      <span class="type-lbl" style="display:flex;align-items:center;gap:5px;width:90px">
        ${imgUrl?`<img src="${imgUrl}" style="width:20px;height:20px;object-fit:cover;border:1px solid var(--border2);flex-shrink:0" onerror="this.style.display='none'">`:''}
        <span style="overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:10px">${esc(name)}</span>
      </span>
      <div style="flex:1"><div class="type-bar" style="width:${Math.round(cnt/maxH*100)}%"></div></div>
      <span class="type-cnt">${cnt}</span>
    </div>`;
  }).join('');

  // 战斗结果分布
  const resultColors={'攻方胜':'var(--win)','守方胜':'var(--blue2)','守方胜(NPC)':'var(--text3)','平局/进行中':'var(--gold)','未知(8)':'var(--lose)'};
  const results=Object.entries(d.result_dist||{}).sort((a,b)=>b[1]-a[1]);
  const maxR=results[0]?.[1]||1;
  document.getElementById('bs-result-chart').innerHTML=results.map(([name,cnt])=>{
    const color=resultColors[name]||'var(--gold)';
    const pct=total?Math.round(cnt/total*100):0;
    return`<div class="type-row">
      <span class="type-lbl" style="width:110px;color:${color}">${esc(name)}</span>
      <div style="flex:1"><div class="type-bar" style="width:${Math.round(cnt/maxR*100)}%;background:${color};box-shadow:none"></div></div>
      <span class="type-cnt">${cnt} <span style="color:var(--text3);font-size:9px">${pct}%</span></span>
    </div>`;
  }).join('');

  // 攻方武将组合TOP10
  const maxC=combos[0]?.[1]||1;
  document.getElementById('bs-combo-chart').innerHTML=combos.slice(0,10).map(([combo,cnt],i)=>`
    <div class="type-row" style="animation-delay:${(i*.04).toFixed(2)}s">
      <span class="type-lbl" style="width:180px;white-space:normal;line-height:1.3;font-size:9px;color:var(--text)">${esc(combo.replace(/\+/g,' + '))}</span>
      <div style="flex:1"><div class="type-bar" style="width:${Math.round(cnt/maxC*100)}%"></div></div>
      <span class="type-cnt">${cnt}</span>
    </div>`).join('');

  // 联盟战绩排行
  const unions=(d.union_stats||[]).filter(u=>u.total>=2).sort((a,b)=>b.total-a.total).slice(0,15);
  const medals=['①','②','③'];
  document.getElementById('bs-union-tbody').innerHTML=unions.map((u,i)=>{
    const rncls=i===0?'r1':i===1?'r2':i===2?'r3':'';
    const sym=i<3?medals[i]:i+1;
    const winColor=u.win_rate>=60?'var(--win)':u.win_rate>=40?'var(--gold)':'var(--lose)';
    return`<tr>
      <td style="padding-left:18px"><span class="rn ${rncls}">${sym}</span></td>
      <td><span class="uname">${esc(u.name)}</span></td>
      <td><span class="chip-m">${u.total}场</span></td>
      <td><span class="chip-g">${u.wins}胜</span></td>
      <td><span style="font-family:'Share Tech Mono',monospace;font-size:12px;font-weight:700;color:${winColor}">${u.win_rate}%</span></td>
    </tr>`;
  }).join('');

  // 城池类型分布
  const cities=Object.entries(d.city_dist||{}).sort((a,b)=>b[1]-a[1]);
  const maxCity=cities[0]?.[1]||1;
  document.getElementById('bs-city-chart').innerHTML=cities.map(([name,cnt])=>`
    <div class="type-row">
      <span class="type-lbl" style="width:80px;color:var(--text)">${esc(name)}</span>
      <div style="flex:1"><div class="type-bar" style="width:${Math.round(cnt/maxCity*100)}%;background:linear-gradient(90deg,var(--blue2),var(--blue3))"></div></div>
      <span class="type-cnt">${cnt}</span>
    </div>`).join('');
}

// ===== 玩家统计 =====
let playerData=[];

async function loadPlayerStats(){
  try{
    const r=await fetch('player_stats.json');
    if(!r.ok)return;
    playerData=await r.json();
    renderPlayer();
  }catch(e){console.log('player_stats load err',e);}
}

function renderPlayer(){
  if(!playerData.length)return;
  const medals=['①','②','③'];

  // 顶部统计卡
  document.getElementById('p-total').textContent=playerData.length;
  document.getElementById('p-ts').textContent='共 '+playerData.length+' 位玩家';
  const top=playerData[0];
  document.getElementById('p-top').textContent=top.name;
  document.getElementById('p-top-sub').textContent=top.total+'场 胜率'+top.win_rate.toFixed(0)+'%';
  const avgWin=(playerData.reduce((s,p)=>s+p.win_rate,0)/playerData.length).toFixed(1);
  document.getElementById('p-avgwin').textContent=avgWin+'%';

  // 最热武将
  const heroCount={};
  playerData.forEach(p=>p.heroes.forEach(h=>{heroCount[h.name]=(heroCount[h.name]||0)+h.count;}));
  const topHero=Object.entries(heroCount).sort((a,b)=>b[1]-a[1])[0];
  if(topHero)document.getElementById('p-top-hero').textContent=topHero[0];

  // 玩家排行表格
  const tb=document.getElementById('player-tbody');
  tb.innerHTML=playerData.map((p,i)=>{
    const rncls=i===0?'r1':i===1?'r2':i===2?'r3':'';
    const sym=i<3?medals[i]:i+1;
    const winColor=p.win_rate>=60?'var(--win)':p.win_rate>=40?'var(--gold)':'var(--lose)';
    const heroStr=p.heroes.slice(0,3).map(h=>`<span class="hc">${esc(h.name)}</span>`).join('');
    const av=heroAvatar(Object.entries(HERO_NAMES).find(([id,n])=>p.heroes[0]&&n===p.heroes[0].name)?.[0]);
    return `<tr>
      <td><span class="rn ${rncls}">${sym}</span></td>
      <td><span class="uname">${esc(p.name)}</span></td>
      <td><span class="chip-l" style="font-size:9px;max-width:100px;overflow:hidden;text-overflow:ellipsis;display:inline-block;white-space:nowrap">${esc(p.unions[0]||'--')}</span></td>
      <td><span class="chip-m">${p.total}场</span> <span style="font-size:10px;color:var(--text3)">${p.wins}胜${p.losses}负</span></td>
      <td><span style="font-family:'Share Tech Mono',monospace;font-size:12px;font-weight:700;color:${winColor}">${p.win_rate.toFixed(0)}%</span></td>
      <td>${heroStr}</td>
    </tr>`;
  }).join('');

  // 武将出场频次图
  const topHeroes=Object.entries(heroCount).sort((a,b)=>b[1]-a[1]).slice(0,12);
  const maxH=topHeroes[0]?.[1]||1;
  document.getElementById('player-hero-chart').innerHTML=topHeroes.map(([name,cnt],i)=>{
    const av=Object.entries(HERO_NAMES).find(([id,n])=>n===name)?.[0];
    const imgUrl=av?heroAvatar(parseInt(av)):'';
    return`<div class="type-row" style="animation-delay:${(i*.04).toFixed(2)}s">
      <span class="type-lbl" style="display:flex;align-items:center;gap:5px">
        ${imgUrl?`<img src="${imgUrl}" style="width:20px;height:20px;object-fit:cover;border:1px solid var(--border2);flex-shrink:0" onerror="this.style.display='none'">`:''}
        <span style="overflow:hidden;text-overflow:ellipsis;white-space:nowrap">${esc(name)}</span>
      </span>
      <div style="flex:1"><div class="type-bar" style="width:${Math.round(cnt/maxH*100)}%"></div></div>
      <span class="type-cnt">${cnt}</span>
    </div>`;
  }).join('');

  // 联盟玩家数分布
  const unionCount={};
  playerData.forEach(p=>p.unions.forEach(u=>{if(u)unionCount[u]=(unionCount[u]||0)+1;}));
  const topUnions=Object.entries(unionCount).sort((a,b)=>b[1]-a[1]).slice(0,10);
  const maxU=topUnions[0]?.[1]||1;
  document.getElementById('player-union-chart').innerHTML=topUnions.map(([name,cnt],i)=>`
    <div class="type-row" style="animation-delay:${(i*.04).toFixed(2)}s">
      <span class="type-lbl" style="width:110px;color:var(--text);font-family:'Noto Serif SC',serif">${esc(name.slice(0,8))}</span>
      <div style="flex:1"><div class="type-bar" style="width:${Math.round(cnt/maxU*100)}%;background:linear-gradient(90deg,var(--blue2),var(--blue3))"></div></div>
      <span class="type-cnt">${cnt}人</span>
    </div>`).join('');
}

loadAll();
loadPlayerStats();
loadBattleStats();
</script></body></html>
