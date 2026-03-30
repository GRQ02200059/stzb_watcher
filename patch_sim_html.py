# -*- coding: utf-8 -*-
src = open('static/dashboard.html', encoding='utf-8').read()

old_start = '<!-- TAB 24: 战斗模拟 -->'
old_end = '</div>\n\n<script src="/static/hero_data.js">'

si = src.index(old_start)
ei = src.index(old_end)

new_tab = '''<!-- TAB 24: 战斗模拟 -->
<div class='page' id='tab24'>

<!-- ===== 游戏风格战斗模拟器 ===== -->
<div id="stzb-sim" style="
  width:100%;min-height:60vh;
  background:linear-gradient(160deg,#08080e 0%,#0e1018 40%,#0a1210 100%);
  position:relative;overflow:hidden;
  font-family:'SimSun','宋体',serif;
">

<!-- 背景纹理 -->
<div style="position:absolute;inset:0;pointer-events:none;
  background-image:radial-gradient(ellipse 80% 60% at 50% 0%,#1a2a1a22 0%,transparent 70%),
  repeating-linear-gradient(0deg,transparent,transparent 39px,#ffffff05 39px,#ffffff05 40px),
  repeating-linear-gradient(90deg,transparent,transparent 39px,#ffffff05 39px,#ffffff05 40px);"></div>

<!-- 顶部血条 header -->
<div style="position:relative;display:flex;align-items:stretch;justify-content:space-between;
  padding:10px 18px 0;gap:10px;">

  <!-- 攻方 -->
  <div style="flex:1;display:flex;flex-direction:column;gap:4px">
    <div style="color:#e4dd9e;font-size:.78rem;letter-spacing:.1em">攻方队伍</div>
    <div style="display:flex;align-items:center;gap:8px">
      <div style="flex:1;height:8px;background:#000;border-radius:2px;overflow:hidden;position:relative">
        <div id="sim-blue-hpbar-hurt" style="position:absolute;height:100%;width:100%;background:#598ed770;transition:width .6s"></div>
        <div id="sim-blue-hpbar" style="position:absolute;height:100%;width:100%;
          background:linear-gradient(90deg,#3a6eb0,#5aaaf0);transition:width .6s"></div>
      </div>
      <span id="sim-blue-hp-text" style="font-size:.68rem;color:#aac8f0;white-space:nowrap;font-family:monospace"></span>
    </div>
    <div style="color:#fff;font-size:.7rem;opacity:.6">攻方 | 战斗模拟</div>
  </div>

  <!-- 中间VS -->
  <div style="display:flex;flex-direction:column;align-items:center;justify-content:center;padding:0 14px;gap:3px">
    <div style="color:#e4dd9e;font-size:1.1rem;font-weight:700;letter-spacing:.2em;text-shadow:0 0 12px #c8a04460">VS</div>
    <div style="display:flex;gap:6px;margin-top:4px">
      <button onclick="runSimulate(1)" id="sim-btn-single"
        style="background:linear-gradient(135deg,#c8a044,#a07830);border:none;color:#1a1200;font-weight:700;
               font-size:.75rem;padding:5px 14px;border-radius:4px;cursor:pointer;font-family:inherit;
               letter-spacing:.1em;box-shadow:0 2px 8px #c8a04440">
        开始战斗
      </button>
      <button onclick="runSimulate(100)" id="sim-btn-100"
        style="background:#1a2030;border:1px solid #4a6090;color:#8ab0d0;
               font-size:.72rem;padding:5px 10px;border-radius:4px;cursor:pointer;font-family:inherit">
        x100
      </button>
      <button onclick="runSimulate(1000)" id="sim-btn-1000"
        style="background:#1a2030;border:1px solid #4a6090;color:#8ab0d0;
               font-size:.72rem;padding:5px 10px;border-radius:4px;cursor:pointer;font-family:inherit">
        x1000
      </button>
    </div>
    <span id="sim-status" style="font-size:.65rem;color:#46b06e;margin-top:2px"></span>
  </div>

  <!-- 守方 -->
  <div style="flex:1;display:flex;flex-direction:column;gap:4px;align-items:flex-end">
    <div style="color:#e4dd9e;font-size:.78rem;letter-spacing:.1em">守方队伍</div>
    <div style="display:flex;align-items:center;gap:8px;width:100%">
      <span id="sim-red-hp-text" style="font-size:.68rem;color:#f0a0a0;white-space:nowrap;font-family:monospace"></span>
      <div style="flex:1;height:8px;background:#000;border-radius:2px;overflow:hidden;position:relative">
        <div id="sim-red-hpbar-hurt" style="position:absolute;height:100%;right:0;width:100%;background:#d04040a0;transition:width .6s"></div>
        <div id="sim-red-hpbar" style="position:absolute;height:100%;right:0;width:100%;
          background:linear-gradient(270deg,#b03030,#e05050);transition:width .6s"></div>
      </div>
    </div>
    <div style="color:#fff;font-size:.7rem;opacity:.6">守方 | 战斗模拟</div>
  </div>

</div>

<!-- 士气行 -->
<div style="display:flex;justify-content:space-between;padding:6px 18px;">
  <div style="display:flex;align-items:center;gap:6px">
    <span style="font-size:.65rem;color:#8ab0d0">士气</span>
    <input id="sim-blue-morale" type="number" value="100" min="0" max="200"
      style="width:52px;font-size:.72rem;background:#0d1520;border:1px solid #2a4060;
             color:#aac8f0;padding:2px 6px;border-radius:3px;text-align:center">
    <button onclick="simAddHero('blue')"
      style="background:none;border:1px solid #4a8fe0;color:#4a8fe0;font-size:.65rem;
             padding:2px 8px;border-radius:3px;cursor:pointer;font-family:inherit">+ 武将</button>
  </div>
  <div style="display:flex;align-items:center;gap:6px">
    <button onclick="simAddHero('red')"
      style="background:none;border:1px solid #e05050;color:#e05050;font-size:.65rem;
             padding:2px 8px;border-radius:3px;cursor:pointer;font-family:inherit">+ 武将</button>
    <span style="font-size:.65rem;color:#f0a0a0">士气</span>
    <input id="sim-red-morale" type="number" value="100" min="0" max="200"
      style="width:52px;font-size:.72rem;background:#200d0d;border:1px solid #602a2a;
             color:#f0a0a0;padding:2px 6px;border-radius:3px;text-align:center">
  </div>
</div>

<!-- 卡牌区 -->
<div style="display:flex;justify-content:space-between;padding:0 12px 12px;gap:10px">

  <!-- 攻方卡牌 -->
  <div id="sim-blue-heroes" style="flex:1;display:flex;gap:8px;justify-content:flex-end"></div>

  <!-- 中间间距 -->
  <div style="width:80px;flex-shrink:0"></div>

  <!-- 守方卡牌 -->
  <div id="sim-red-heroes" style="flex:1;display:flex;gap:8px;justify-content:flex-start"></div>

</div>

<!-- 结果区 -->
<div id="sim-stat-box" style="display:none;margin:0 12px 12px;
  background:#00000040;border:1px solid #2a3a2a;border-radius:6px;padding:12px">
  <div class="cards-row" id="sim-stat-cards"></div>
</div>

<!-- 战斗日志 -->
<div id="sim-log-box" style="display:none;margin:0 12px 12px">
  <div style="display:flex;align-items:center;justify-content:space-between;
    padding:6px 12px;background:#00000060;border-top:1px solid #e4dd9e30;">
    <span style="color:#e4dd9e;font-size:.78rem;letter-spacing:.1em">战斗记录</span>
    <button onclick="simToggleLog()" id="sim-log-toggle"
      style="background:none;border:1px solid #3a4a5a;color:#7a8a9a;
             font-size:.68rem;padding:2px 10px;border-radius:3px;cursor:pointer">收起</button>
  </div>
  <div id="sim-log-content"
    style="padding:8px 14px;font-family:monospace;font-size:.7rem;line-height:2;
           max-height:420px;overflow-y:auto;background:#06080c;
           scrollbar-width:thin;scrollbar-color:#2a3a4a transparent"></div>
</div>

</div><!-- /stzb-sim -->

</div>
'''

new_src = src[:si] + new_tab + old_end
open('static/dashboard.html', 'w', encoding='utf-8').write(new_src)
print('done, new len:', len(new_src))

