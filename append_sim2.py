# -*- coding: utf-8 -*-
code = r"""
        background:" + (i < (hero.up||0) ? '#e4dd9e' : '#2a3a4a') + ";border-radius:50%"></div>"
    ).join('');

    // 位置颜色
    const posColors = {'大营':'#3ab8c8','中军':'#c8a044','前锋':'#e05050'};
    const posColor = posColors[pos] || '#7a8a9a';

    const card = document.createElement('div');
    card.style.cssText = `
      position:relative;width:calc(33% - 6px);min-width:100px;
      background:linear-gradient(160deg,#0d1520,#080c14);
      border:1.5px solid ${cc};
      border-radius:6px;overflow:hidden;
      box-shadow:0 0 12px ${cc}30,0 4px 20px #00000080;
      flex-shrink:0;
    `;
    card.innerHTML = `
      <!-- 顶色条 -->
      <div style="height:2px;background:linear-gradient(90deg,${cc},${cc}44)"></div>

      <!-- 武将图 + 信息 -->
      <div style="position:relative">
        <!-- 大图背景 -->
        <img src="${imgUrl}"
          style="width:100%;aspect-ratio:3/4;object-fit:cover;object-position:left top;
                 display:block;opacity:.85"
          onerror="this.style.opacity=0">
        <!-- 渐变遮罩 -->
        <div style="position:absolute;inset:0;
          background:linear-gradient(to bottom,
            transparent 40%,
            #00000088 70%,
            #000000cc 100%)"></div>

        <!-- 阵营色块 左上 -->
        <div style="position:absolute;top:4px;left:4px;
          background:${cc};color:#fff;
          font-size:.55rem;padding:1px 5px;border-radius:2px;
          letter-spacing:.05em;font-weight:700">
          ${SIM_CAMP_NAME[hinfo.camp]||''} ${SIM_ARMY_NAME[hinfo.army]||''}
        </div>

        <!-- 位置标 右上 -->
        <div style="position:absolute;top:4px;right:4px;
          color:${posColor};font-size:.6rem;
          background:#00000088;padding:1px 5px;border-radius:2px;
          border:1px solid ${posColor}66">
          ${pos}
        </div>

        <!-- 兵力 左下 -->
        <div style="position:absolute;left:5px;bottom:36px;font-size:.6rem;color:#fff;font-family:monospace">
          Lv<span style="color:#e4dd9e">${hero.level}</span>
          <span style="color:#9ab8d0;margin-left:4px">进阶★${hero.up||0}</span>
        </div>

        <!-- 武将名 底部 -->
        <div style="position:absolute;bottom:8px;left:0;right:0;text-align:center;
          color:#e4dd9e;font-size:.82rem;font-weight:700;
          letter-spacing:.1em;text-shadow:0 1px 4px #000">
          ${esc(hinfo.name||'?')}
        </div>

        <!-- 星级 右下 -->
        <div style="position:absolute;bottom:5px;right:4px;display:flex;gap:1px">
          ${stars}
        </div>

        <!-- 删除按钮 -->
        <button onclick="simRemoveHero('${camp}',${idx})"
          style="position:absolute;top:22px;right:3px;
            background:#00000060;border:1px solid #3a4a5a;color:#7a8a9a;
            border-radius:50%;width:16px;height:16px;cursor:pointer;
            font-size:.55rem;line-height:1;display:flex;align-items:center;justify-content:center;
            padding:0">
          x
        </button>
      </div>

      <!-- 武将选择 -->
      <div style="padding:4px 5px 2px">
        <select onchange="simChangeHero('${camp}',${idx},+this.value)"
          style="width:100%;background:#060a10;color:#c0c8d0;border:1px solid #1e2e40;
                 padding:2px 4px;border-radius:3px;font-size:.65rem">
          ${heroOpts}
        </select>
      </div>

      <!-- 等级/进阶 -->
      <div style="display:flex;gap:4px;align-items:center;padding:2px 5px">
        <span style="font-size:.58rem;color:#5a7a9a">Lv</span>
        <input type="number" value="${hero.level}" min="1" max="45"
          style="width:36px;font-size:.65rem;background:#060a10;color:#c0d8f0;
                 border:1px solid #1e2e40;padding:1px 3px;border-radius:2px"
          onchange="simSetProp('${camp}',${idx},'level',+this.value)">
        <span style="font-size:.58rem;color:#5a7a9a">进阶</span>
        <input type="number" value="${hero.up||0}" min="0" max="9"
          style="width:28px;font-size:.65rem;background:#060a10;color:#e4dd9e;
                 border:1px solid #1e2e40;padding:1px 3px;border-radius:2px"
          onchange="simSetProp('${camp}',${idx},'up',+this.value);_renderCards('${camp}')">
      </div>

      <!-- 战法槽 -->
      <div style="padding:4px 5px 6px;border-top:1px solid #1a2a3a;margin-top:2px">
        <div style="font-size:.56rem;color:#3a5a7a;margin-bottom:3px;letter-spacing:.05em">装备战法</div>
        ${skillSlots}
      </div>
    `;
    container.appendChild(card);
  });
}
"""
with open('static/sim.js', 'a', encoding='utf-8') as f:
    f.write(code)
print('part2 ok')

