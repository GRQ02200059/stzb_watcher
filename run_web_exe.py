"""STZB Web 端一键启动入口。"""
from __future__ import annotations

import argparse
import sys
import threading
import time
import urllib.error
import urllib.request
import webbrowser


def parse_args(argv=None):
    parser = argparse.ArgumentParser(description="STZB Web 本地启动器")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8080)
    parser.add_argument("--no-browser", dest="browser", action="store_false")
    parser.add_argument("--no-sniffer", dest="sniffer", action="store_false")
    parser.set_defaults(browser=True, sniffer=True)
    return parser.parse_args(argv)


def open_browser(url: str) -> None:
    deadline = time.monotonic() + 60
    while time.monotonic() < deadline:
        try:
            with urllib.request.urlopen(url, timeout=1):
                webbrowser.open(url)
            return
        except (OSError, urllib.error.URLError):
            time.sleep(0.25)
    print('[startup] 服务未就绪，未打开浏览器；请检查控制台错误。')


def run_server(**kwargs) -> None:
    from api_server import run_app
    run_app(open_browser=False, **kwargs)


def main(argv=None) -> None:
    if getattr(sys, 'frozen', False):
        for stream in (sys.stdout, sys.stderr):
            if stream is not None:
                stream.reconfigure(encoding='utf-8', errors='backslashreplace')
    args = parse_args(argv)
    url = f"http://{args.host}:{args.port}/"
    if args.browser:
        browser_thread = threading.Timer(0, open_browser, args=(url,))
        browser_thread.daemon = True
        browser_thread.start()
    run_server(host=args.host, port=args.port, start_sniffer=args.sniffer)


if __name__ == "__main__":
    main()
