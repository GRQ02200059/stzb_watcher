import importlib.util
import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
import subprocess
import sys
import threading
import unittest


SCRIPT = Path(__file__).resolve().parents[1] / 'packaging/scripts/smoke_windows.py'


class WindowsSmokeTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        spec = importlib.util.spec_from_file_location('windows_smoke', SCRIPT)
        cls.smoke = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(cls.smoke)

    def test_http_200_with_application_error_is_not_success(self):
        class Handler(BaseHTTPRequestHandler):
            def do_GET(self):
                body = json.dumps({'ok': False, 'error': 'missing configuration'}).encode()
                self.send_response(200)
                self.end_headers()
                self.wfile.write(body)

            def log_message(self, *args):
                pass

        with ThreadingHTTPServer(('127.0.0.1', 0), Handler) as server:
            thread = threading.Thread(target=server.serve_forever, daemon=True)
            thread.start()
            try:
                with self.assertRaisesRegex(RuntimeError, 'missing configuration'):
                    self.smoke.fetch_json(f'http://127.0.0.1:{server.server_port}/')
            finally:
                server.shutdown()
                thread.join()

    def test_crashed_process_fails_readiness_without_waiting_for_timeout(self):
        with subprocess.Popen([sys.executable, '-c', 'raise SystemExit(7)']) as process:
            process.wait(timeout=5)
            with self.assertRaisesRegex(RuntimeError, '7'):
                self.smoke.wait_ready(process, 'http://127.0.0.1:1/', timeout=60)


if __name__ == '__main__':
    unittest.main()
