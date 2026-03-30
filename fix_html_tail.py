# -*- coding: utf-8 -*-
src = open('static/dashboard.html', encoding='utf-8').read()
script_tail = '</script><script src="/static/herocfg.js"></script><script src="/static/app1.js"></script><script src="/static/app2.js"></script><script src="/static/sim.js?v=3"></script></body></html>'
tail_check = '<script src="/static/hero_data.js">'
if src.endswith(tail_check):
    open('static/dashboard.html', 'w', encoding='utf-8').write(src + script_tail)
    print('fixed ok')
else:
    print('unexpected end:', repr(src[-80:]))

