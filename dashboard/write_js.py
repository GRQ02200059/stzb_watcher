# -*- coding: utf-8 -*-
import os

js_part = '''
<script>
const MSG_NAMES = {
  '0000000a': '战斗详情',
  '000002bc': '联盟排行榜',
  '00015f95': '数据库同步推送',
  '0000029f': '武将解锁记录',
  '00000fd4': '武将解锁(配套)',
  '00000064': '联盟详情',
  '000009fe': '联盟详情(配套)',
  '0000262a': '玩家列表',
  '00002624': '玩家列表(配套)',
  '00001857': '数据库Schema',
  '0003072e': '全量Schema',
  '00002ca2': '行军状态',
  '00001fe6': '联盟排行(变体)',
  '000146a4': '玩家排行榜',
};

const HERO_NAMES = {
  100001:"曹操",100004:"夏侯惇",100005:"司马懿",100006:"郭嘉",100007:"张辽",
  100008:"许褚",100009:"典韦",100010:"荀彧",100025:"刘备",100026:"关羽",
  100027:"张飞",100028:"赵云",100029:"诸葛亮",100030:"黄忠",100031:"马超",
  100032:"魏延",100033:"庞统",100034:"姜维",100037:"吕布",100038:"貂蝉",
  100039:"董卓",100040:"袁绍",100050:"孙权",100051:"孙策",100052:"周瑜",
  100053:"大乔",100054:"小乔",100055:"鲁肃",100056:"甘宁",100057:"太史慈",
  100058:"陆逊",100059:"吕蒙",100067:"华佗",100085:"曹仁",100086:"徐晃",
  100087:"张郃",100090:"夏侯渊",100098:"邓艾",100099:"钟会",100110:"蔡文姬",
  100111:"大乔",100113:"小乔",100115:"孙尚香",100127:"刘璋",100132:"马岱",
  100155:"黄月英",100156:"孟获",100178:"孙坚",100187:"周泰",100188:"蒋钦",
  100189:"韩当",100190:"程普",100191:"黄盖",100199:"凌统",100224:"张宝",
  100225:"管亥",100240:"张绣",100243:"吕布",100245:"陈宫",100247:"臧霸",
  100266:"颜良",100267:"文丑",100280:"陶谦",100281:"公孙瓒",100321:"丁原",
  100330:"管辂",100331:"司马徽",100355:"徐庶",100371:"庞德",100396:"梁兴",
  100414:"少帝",100420:"夏侯渊",100421:"徐晃",100426:"凌统",100430:"张宝",
  100439:"黄盖",100442:"黄忠",100492:"司马懿",100493:"吕布",100520:"周瑜",
  100635:"荀彧",100636:"邓艾",100637:"姜维",100639:"钟会",100640:"陆抗",
  100643:"羊祜",100718:"诸葛亮EX",100725:"司马懿EX",100744:"关羽EX",
  100754:"赵云EX",100760:"马超EX",100764:"黄忠EX",
};

function heroName(id){return HERO_NAMES[id]||("武将"+id);}
function fmt(n){return n>=10000?(n/10000).toFixed(1)+"万":String(n);}

let unionData=[], battleData=[];

function updateClock(){
  const n=new Date(),p=x=>String(x).padStart(2,"0");
  document.getElementById("clock").textContent=
    n.getFullYear()+"-"+p(n.getMonth()+1)+"-"+p(n.getDate())+" "+p(n.getHours())+":"+p(n.getMinutes())+":"+p(n.getSeconds());
}
setInterval(updateClock,1000); updateClock();

function switchTab(id,el){
  document.querySelectorAll(".tab").forEach(t=>t.classList.remove("active"));
  document.querySelectorAll(".page").forEach(p=>p.classList.remove("active"));
  el.classList.add("active");
  document.getElementById("page-"+id).classList.add("active");
}

async function fetchJSON(url){
  try{const r=await fetch(url);if(!r.ok)return null;return await r.json();}catch{return null;}
}

async function scanFiles(dir){
  try{
    const r=await fetch(dir+"/");
    if(!r.ok)return [];
    const html=await r.text();
    return [...html.matchAll(/href="(decompressed_[^"]+\.json)"/g)].map(m=>dir+"/"+m[1]);
  }catch{return [];}
}

async function loadAll(){
  document.getElementById("load-status").textContent="加载中...";
  await Promise.all([loadUnionData(), loadBattleData(), loadTypeStats()]);
  document.getElementById("load-status").textContent="已更新 "+new Date().toLocaleTimeString();
}

async function loadUnionData(){
  const dirs=["../decompressed_data_report/000002bc","../capture_new/000002bc",
               "../decompressed_data_report/00001fe6","../capture_new/00001fe6"];
  let best=null, bestN=0;
  for(const d of dirs){
    const files=await scanFiles(d);
    for(const f of files){
      const data=await fetchJSON(f);
      if(!data||!Array.isArray(data))continue;
      const arr=data[4];
      if(!Array.isArray(arr))continue;
      const unions=arr.map(item=>item[1]).filter(u=>u&&u.name);
      if(unions.length>bestN){bestN=unions.length;best=unions;}
    }
  }
  if(best){unionData=best.sort((a,b)=>b.power-a.power);renderUnion();}
}

async function loadBattleData(){
  const dirs=["../decompressed_data_report/0000000a","../capture_new/0000000a"];
  const battles=[];
  for(const d of dirs){
    const files=await scanFiles(d);
    for(const f of files){
      const data=await fetchJSON(f);
      if(!data)continue;
      // format: [battle_id, [battle_obj, ...]]
      let arr=Array.isArray(data)?data:[];
      let objs=[];
      if(arr.length>=2&&Array.isArray(arr[1]))objs=arr[1];
      else if(Array.isArray(arr[0]))objs=arr;
      for(const o of objs){
        if(o&&o.battle_id)battles.push(o);
      }
    }
  }
  battleData=battles;
  renderBattle();
}

async function loadTypeStats(){
  const roots=["../decompressed_data_report","../capture_new"];
  const stats={};
  let total=0;
  for(const root of roots){
    try{
      const r=await fetch(root+"/");
      if(!r.ok)continue;
      const html=await r.text();
      // 找子目录
      const dirs=[...html.matchAll(/href="([0-9a-f]{8})\//g)].map(m=>m[1]);
      for(const d of dirs){
        const files=await scanFiles(root+"/"+d);
        stats[d]=(stats[d]||0)+files.length;
        total+=files.length;
      }
      // 根目录json
      const rootFiles=[...html.matchAll(/href="(decompressed_[^"]+\.json)"/g)];
      stats["(根目录)"]=(stats["(根目录)"]||0)+rootFiles.length;
      total+=rootFiles.length;
    }catch{}
  }
  renderOverview(stats, total);
}

// ===== RENDER UNION =====
function renderUnion(){
  if(!unionData.length)return;
  const maxP=unionData[0].power||1;
  
  document.getElementById("s-total").textContent=unionData.length;
  document.getElementById("s-maxpow").textContent=fmt(unionData[0].power);
  document.getElementById("s-maxname").textContent=unionData[0].name;
  const maxMem=[...unionData].sort((a,b)=>b.total_member-a.total_member)[0];
  document.getElementById("s-maxmem").textContent=maxMem.total_member;
  document.getElementById("s-maxmemname").textContent=maxMem.name;
  const maxLv=[...unionData].sort((a,b)=>b.level-a.level)[0];
  document.getElementById("s-maxlv").textContent="Lv."+maxLv.level;
  document.getElementById("s-maxlvname").textContent=maxLv.name;
  document.getElementById("union-ts").textContent="共 "+unionData.length+" 个联盟";

  // table
  const tb=document.getElementById("union-tbody");
  tb.innerHTML=unionData.map((u,i)=>{
    const pct=Math.round(u.power/maxP*100);
    const rncls=i===0?"rn r1":i===1?"rn r2":i===2?"rn r3":"rn";
    const rnTxt=i===0?"①":i===1?"②":i===2?"③":String(i+1);
    return `<tr>
      <td><span class="${rncls}">${rnTxt}</span></td>
      <td><span class="uname">${u.name}</span></td>
      <td><span class="chip-l">Lv.${u.level}</span></td>
      <td><div class="pbar-w"><div class="pbar"><div class="pbar-f" style="width:${pct}%"></div></div><span class="pval">${fmt(u.power)}</span></div></td>
      <td><span class="chip-m">${u.total_member}人</span></td>
      <td><span class="rn">${u.region}</span></td>
    </tr>`;
  }).join("");

  // bar chart (top 20)
  const top=unionData.slice(0,20);
  const chartH=200;
  const chart=document.getElementById("union-chart");
  chart.innerHTML=top.map((u,i)=>{
    const h=Math.max(8,Math.round(u.power/maxP*chartH));
    const delay=(i*0.04).toFixed(2);
    return `<div class="bi" style="animation-delay:${delay}s">
      <div class="b" style="height:${h}px" title="${u.name}: ${fmt(u.power)}"></div>
      <div class="blbl">${u.name.slice(0,4)}</div>
    </div>`;
  }).join("");
}

// ===== RENDER BATTLE =====
function renderBattle(){
  if(!battleData.length){
    document.getElementById("battle-list").innerHTML=`<div class="empty">暂无战报数据<br>请先在游戏中触发战斗</div>`;
    return;
  }

  const atkWins=battleData.filter(b=>b.result===1).length;
  const players=new Set([...battleData.map(b=>b.attack_name),...battleData.map(b=>b.defend_name)]);
  const unions=new Set([...battleData.map(b=>b.attack_union_name),...battleData.map(b=>b.defend_union_name)]);

  document.getElementById("b-total").textContent=battleData.length;
  document.getElementById("b-atkwin").textContent=battleData.length>0?Math.round(atkWins/battleData.length*100)+"%":"--";
  document.getElementById("b-players").textContent=[...players].filter(Boolean).length;
  document.getElementById("b-unions").textContent=[...unions].filter(Boolean).length;
  document.getElementById("b-ts").textContent="共 "+battleData.length+" 场战斗";

  // battle list
  const list=document.getElementById("battle-list");
  list.innerHTML=battleData.slice(0,20).map((b,i)=>{
    const win=b.result===1;
    const atkHeroes=parseHeroes(b.attack_all_hero_info);
    const defHeroes=parseHeroes(b.defend_all_hero_info);
    return `<div class="bcard ${win?"win":"lose"}" onclick="showBattle(${i})" style="animation-delay:${i*0.05}s">
      <div class="bh">
        <span class="bid">战报 #${b.battle_id}</span>
        <span class="bres ${win?"win":"lose"}">${win?"攻方胜":"守方胜"}</span>
      </div>
      <div class="bvs">
        <div class="bside">
          <div class="sname">${b.attack_name||"未知"}</div>
          <div class="sunion">${b.attack_union_name||"无联盟"}</div>
          <div class="sheroes">${atkHeroes.map(h=>`<span class="hc">${heroName(h)}</span>`).join("")}</div>
        </div>
        <div class="vs">VS</div>
        <div class="bside r">
          <div class="sname">${b.defend_name||"未知"}</div>
          <div class="sunion">${b.defend_union_name||"无联盟"}</div>
          <div class="sheroes r">${defHeroes.map(h=>`<span class="hc">${heroName(h)}</span>`).join("")}</div>
        </div>
      </div>
    </div>`;
  }).join("");

  // winlose chart
  const defWins=battleData.length-atkWins;
  const wlEl=document.getElementById("winlose-chart");
  const aPct=battleData.length>0?Math.round(atkWins/battleData.length*100):0;
  wlEl.innerHTML=`
    <div style="margin-bottom:12px">
      <div 
