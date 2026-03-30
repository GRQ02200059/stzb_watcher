# -*- coding: utf-8 -*-
part2 = r"""
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
"""
with open('static/sim.js','a',encoding='utf-8') as f:
    f.write(part2)
print('part2 ok')

