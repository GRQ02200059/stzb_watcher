"""STZB Web 端一键启动入口。"""
from __future__ import annotations

import argparse
import threading
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
    webbrowser.open(url)


def run_server(**kwargs) -> None:
    from api_server import run_app
    run_app(open_browser=False, **kwargs)


def main(argv=None) -> None:
    args = parse_args(argv)
    url = f"http://{args.host}:{args.port}/"
    if args.browser:
        threading.Timer(1.5, open_browser, args=(url,)).start()
    run_server(host=args.host, port=args.port, start_sniffer=args.sniffer)


if __name__ == "__main__":
    main()
