# -*- coding: utf-8 -*-
"""
start_pipeline.py
一键启动：抓包脚本 + API服务器（含实时写库 + SSE推送）
"""
import subprocess, sys, os, time, signal, threading

BASE = os.path.dirname(os.path.abspath(__file__))

procs = []

def start(name, cmd):
    print(f'[start] {name}: {" ".join(cmd)}')
    p = subprocess.Popen(cmd, cwd=BASE)
    procs.append((name, p))
    return p

def stop_all():
    print('\n[stop] 停止所有进程...')
    for name, p in procs:
        try:
            p.terminate()
            p.wait(timeout=3)
            print(f'  stopped {name}')
        except Exception as e:
            print(f'  kill {name}: {e}')
            try: p.kill()
            except: pass

def monitor():
    while True:
        time.sleep(5)
        for name, p in procs:
            if p.poll() is not None:
                print(f'[WARN] {name} 已退出 (code={p.returncode})')

def main():
    print('=' * 50)
    print('  率土之滨 · 一条龙服务启动')
    print('=' * 50)
    print()

    # 1. 抓包脚本（需要管理员权限）
    sniffer = start('sniffer', [sys.executable, os.path.join(BASE, 'scrapy_v2.py')])
    time.sleep(1)

    # 2. API服务器（含 realtime_writer + SSE）
    api = start('api_server', [sys.executable, os.path.join(BASE, 'api_server.py')])
    time.sleep(2)

    print()
    print('=' * 50)
    print('  服务已启动：')
    print('  前端看板: http://127.0.0.1:8765/')
    print('  API状态:  http://127.0.0.1:8765/api/status')
    print('  SSE流:    http://127.0.0.1:8765/api/stream')
    print('  按 Ctrl+C 停止所有服务')
    print('=' * 50)
    print()

    # 监控线程
    t = threading.Thread(target=monitor, daemon=True)
    t.start()

    try:
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        stop_all()

if __name__ == '__main__':
    main()

