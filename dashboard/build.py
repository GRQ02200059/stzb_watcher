# -*- coding: utf-8 -*-
import os

PATH = 'd:/nettest/dashboard/index.html'

PART1 = '''<!DOCTYPE html>
<html lang="zh-CN"><head><meta charset="UTF-8">
<title>率土之滨 · 战情指挥台</title>
<style>
@import url('https://fonts.googleapis.com/css2?family=Noto+Serif+SC:wght@400;700;900&family=Share+Tech+Mono&display=swap');
:root{--bg:#0a0c10;--bg2:#0f1318;--bg3:#161b24;--border:#1e2d42;--gold:#c9a84c;--gold2:#f0d080;--red:#c0392b;--blue2:#2980b9;--text:#d4c9a8;--text2:#8a9bb0;--win:#27ae60;--lose:#c0392b;}
*{box-sizing:border-box;margin:0;padding:0}
body{background:var(--bg);color:var(--text);font-family:'Noto Serif SC',serif;min-height:100vh}
body::before{content:'';position:fixed;inset:0;pointer-events:none;z-index:0;
  background:repeating-linear-gradient(0deg,transparent,transparent 59px,rgba(30,45,66,.18) 60px),
  repeating-linear-gradient(90deg,transparent,transparent 59px,rgba(30,45,66,.18) 60px);}
header{position:relative;z-index:10;display:flex;align-items:center;justify-content:space-between;
  padding:0 36px;height:66px;border-bottom:1px solid var(--border);background:rgba(10,12,16,.97);}
.logo{display:flex;align-items:center;gap:14px}
.logo-hex{width:36px;height:36px;display:flex;align-items:center;justify-content:center;
  clip-path:polygon(50% 0%,100% 25%,100% 75%,50% 100%,0% 75%,0% 25%);
  background:rgba(201,168,76,.1);border:1.5px solid var(--gold);color:var(--gold);font-size:17px;}
.logo-t{font-size:19px;font-weight:900;color:var(--gold2);letter-spacing:4px;text-shadow:0 0 28px rgba(201,168,76,.35)}
.logo-s{font-size:9px;color:var(--text2);letter-spacing:2px;margin-top:2px;font-family:'Share Tech Mono',monospace}
.hdr-r{display:flex;align-items:center;gap:18px}
.live{display:flex;align-items:center;gap:6px;font-size:11px;color:var(--text2);font-family:'Share Tech Mono',monospace}
.dot{width:7px;height:7px;border-radius:50%;background:var(--win);animation:pulse 2s infinite}
@keyframes pulse{0%,100%{box-shadow:0 0 0 0 rgba(39,174,96,.5)}50%{box-shadow:0 0 0 5px rgba(39,174,96,0)}}
#clock{font-family:'Share Tech Mono',monospace;font-size:13px;color:var(--gold);letter-spacing:2px}
#load-status{font-family:'Share Tech Mono',monospace;font-size:11px;color:var(--text2)}
.btn{padding:6px 16px;border:1px solid var(--gold);background:transparent;color:var(--gold);font-size:12px;
  cursor:pointer;letter-spacing:2px;transition:all .25s;clip-path:polygon(8px 0%,100% 0%,calc(100% - 8px) 100%,0% 100%);font-family:'Noto Serif SC',serif}
.btn:hover{background:rgba(201,168,76,.12);box-shadow:0 0 16px rgba(201,168,76,.2)}
.tabs{position:relative;z-index:10;display:flex;gap:2px;padding:0 36px;background:var(--bg2);border-bottom:1px solid var(--border);}
.tab{padding:12px 24px;font-size:12px;letter-spacing:2px;cursor:pointer;color:var(--text2);border-bottom:2px solid transparent;transition:all .25s;user-select:none}
.tab:hover{color:var(--text)}.tab.active{color:var(--gold2);border-bottom-color:var(--gold)}
.main{position:relative;z-index:1;padding:22px 36px;max-width:1600px;margin:0 auto}
.page{display:none}.page.active{display:block}
.stat-row{display:grid;grid-template-columns:repeat(4,1fr);gap:14px;margin-bottom:22px}
.scard{background:var(--bg2);border:1px solid var(--border);padding:18px 20px;position:relative;overflow:hidden;animation:fadeUp .5s ease both}
.scard::before{content:'';position:absolute;top:0;left:0;right:0;height:2px;background:linear-gradient(90deg,transparent,var(--gold),transparent);opacity:.5}
.scard .lbl{font-size:10px;color:var(--text2);letter-spacing:2px;margin-bottom:8px}
.scard .val{font-size:28px;font-weight:900;color:var(--gold2);line-height:1;text-shadow:0 0 16px rgba(201,168,76,.3)}
.scard .sub{font-size:11px;color:var(--text2);margin-top:5px;font-family:'Share Tech Mono',monospace;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
@keyframes fadeUp{from{opacity:0;transform:translateY(12px)}to{opacity:1;transform:translateY(0)}}
.pgrid{display:grid;gap:18px}.pgrid2{grid-template-columns:1fr 1fr}.pgrid3{grid-template-columns:3fr 2fr}
.panel{background:var(--bg2);border:1px solid var(--border);animation:fadeUp .6s ease both}
.ph{display:flex;align-items:center;justify-content:space-between;padding:12px 16px;border-bottom:1px solid var(--border);background:rgba(201,168,76,.03)}
.pt{font-size:12px;letter-spacing:3px;color:var(--gold);display:flex;align-items:center;gap:8px}
.pt::before{content:'';display:inline-block;width:3px;height:12px;background:var(--gold)}
.pb{font-size:10px;font-family:'Share Tech Mono',monospace;color:var(--text2);background:var(--bg3);border:1px solid var(--border);padding:2px 8px}
.pbody{padding:14px 16px}
.rtbl{width:100%;border-collapse:collapse}
.rtbl th{text-align:left;font-size:10px;letter-spacing:2px;color:var(--text2);padding:0 10px 8px;border-bottom:1px solid var(--border);font-weight:400}
.rtbl td{padding:9px 10px;font-size:12px;border-bottom:1px solid rgba(30,45,66,.4);transition:background .2s}
.rtbl tr:hover td{background:rgba(201,168,76,.04)}
.rn{font-family:'Share Tech Mono',monospace;color:var(--text2);font-size:11px;width:30px;text-align:center}
.rn.r1{color:#FFD700;font-weight:900}.rn.r2{color:#C0C0C0;font-weight:900}.rn.r3{color:#CD7F32;font-weight:900}
.uname{font-weight:700;max-width:150px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.pbar-w{display:flex;align-items:center;gap:8px}
.pbar{flex:1;height:3px;background:var(--bg3);max-width:90px;border-radius:2px;overflow:hidden}
.pbar-f{height:100%;background:linear-gradient(90deg,var(--gold),var(--gold2));border-radius:2px;transition:width 1s ease}
.pval{font-family:'Share Tech Mono',monospace;font-size:11px;color:var(--gold);min-width:52px;text-align:right}
.chip-m{font-family:'Share Tech Mono',monospace;font-size:10px;padding:1px 6px;background:rgba(41,128,185,.15);border:1px solid rgba(41,128,185,.35);color:var(--blue2)}
.chip-l{font-family:'Share Tech Mono',monospace;font-size:10px;padding:1px 6px;background:rgba(201,168,76,.1);border:1px solid rgba(201,168,76,.3);color:var(--gold)}
.chart-w{padding:12px 16px;height:230px;display:flex;align-items:flex-end;gap:5px;overflow-x:auto}
.bi{display:flex;flex-direction:column;align-items:center;gap:3px;flex:1;min-width:26px;max-width:50px;animation:growUp .8s ease both}
@keyframes growUp{from{transform:scaleY(0);transform-origin:bottom;opacity:0}to{transform:scaleY(1);transform-origin:bottom;opacity:1}}
.b{width:100%;background:linear-gradient(180deg,var(--gold2),var(--gold) 60%,rgba(201,168,76,.2));border-radius:2px 2px 0 0;cursor:pointer;transition:filter .2s}
.b:hover{filter:brightness(1.35)}.blbl{font-size:8px;color:var(--text2);text-align:center;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.blist{display:flex;flex-direction:column;gap:10px}
.bcard{background:var(--bg3);border:1px solid var(--border);padding:12px 14px;position:relative;overflow:hidden;cursor:pointer;transition:border-color .2s;animation:fadeUp .5s ease both}
.bcard:hover{border-color:rgba(201,168,76,.4)}
.bcard.win::before,.bcard.lose::before{content:'';position:absolute;left:0;top:0;bottom:0;width:3px}
.bcard.win::before{background:var(--win)}.bcard.lose::before{background:var(--lose)}
.bh{display:flex;justify-content:space-between;align-items:center;margin-bottom:8px}
.bid{font-family:'Share Tech Mono',monospace;font-size:10px;color:var(--text2)}
.bres{font-size:10px;font-weight:700;letter-spacing:2px;padding:2px 8px}
.bres.win{color:var(--win);border:1px solid var(--win);background:rgba(39,174,96,.1)}
.bres.lose{color:var(--lose);border:1px solid var(--lose);background:rgba(192,57,43,.1)}
.bvs{display:grid;grid-template-columns:1fr auto 1fr;align-items:center;gap:10px}
.bside{display:flex;flex-direction:column;gap:2px}.bside.r{text-align:right;align-items:flex-end}
.sname{font-size:13px;font-weight:700;max-width:120px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.sunion{font-size:10px;color:var(--text2);max-width:120px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.sheroes{display:flex;gap:3px;margin-top:3px;flex-wrap:wrap}.sheroes.r{justify-content:flex-end}
.hc{font-size:9px;padding:1px 5px;background:rgba(201,168,76,.1);border:1px solid rgba(201,168,76,.2);color:var(--gold);font-family:'Share Tech Mono',monospace}
.vs{font-size:15px;font-weight:900;color:var(--red);text-shadow:0 0 14px rgba(192,57,43,.5)}
.overlay{position:fixed;inset:0;background:rgba(0,0,0,.78);z-index:100;display:none;align-items:center;justify-content:center}
.overlay.show{display:flex}
.modal{background:var(--bg2);border:1px solid var(--border);width:780px;max-width:95vw;max-height:85vh;overflow-y:auto;animation:fadeUp .3s ease}
.modal-h{display:flex;align-items:center;justify-content:space-between;padding:14px 18px;border-bottom:1px solid var(--border);background:rgba(201,168,76,.04);position:sticky;top:0;z-index:1;}
.modal-t{font-size:14px;font-weight:700;color:var(--gold2);letter-spacing:2px}
.close-btn{background:none;border:none;color:var(--text2);font-size:20px;cursor:pointer;line-height:1}
.close-btn:hover{color:var(--text)}
.modal-b{padding:18px}
.hero-grid{display:grid;grid-template-columns:1fr 1fr;gap:16px;margin-bottom:14px}
.hside{background:var(--bg3);border:1px solid var(--border);padding:12px}
.hside-t{font-size:11px;letter-spacing:2px;margin-bottom:8px;padding-bottom:6px;border-bottom:1px solid var(--border)}
.hside-t.atk{color:#e67e22}.hside-t.def{color:var(--blue2)}
.hero-row{display:flex;align-items:center;gap:10px;margin-bottom:6px}
.hav{width:38px;height:38px;border:1px solid var(--border);background:var(--bg);display:flex;align-items:center;justify-content:center;font-size:10px;color:var(--text2);flex-shrink:0}
.hi .hn{font-size:12px;font-weight:700}.hi .hl{font-size:10px;color:var(--text2);font-family:'Share Tech Mono',monospace;margin-top:1px}
.bmeta{display:grid;grid-template-columns:repeat(3,1fr);gap:10px}
.bmi{background:var(--bg3);border:1px solid var(--border);padding:10px 12px}
.bmk{font-size:10px;color:var(--text2);letter-spacing:1px;margin-bottom:4px}
.bmv{font-size:14px;font-weight:700;color:var(--gold2);font-family:'Share Tech Mono',monospace}
.empty{text-align:center;padding:40px;color:var(--text2);font-size:13px}
.type-row{display:flex;align-items:center;gap:10px;margin-bottom:8px}
.type-bar{height:6px;background:linear-gradient(90deg,var(--gold),var(--gold2));border-radius:3px;transition:width .8s ease;min-width:2px}
.type-lbl{font-family:'Share Tech Mono',monospace;font-size:10px;color:var(--text2);width:80px;flex-shrink:0}
.type-cnt{font-family:'Share Tech Mono',monospace;font-size:10px;color:var(--gold);margin-left:auto;flex-shrink:0}
::-webkit-scrollbar{width:4px;height:4px}::-webkit-scrollbar-track{background:var(--bg)}::-webkit-scrollbar-thumb{background:var(--border);border-radius:2px}
</style>
</head>
<body>
'''

with open(PATH, 'w', encoding='utf-8') as f:
    f.write(PART1)
print('Part1 written:', len(PART1))
