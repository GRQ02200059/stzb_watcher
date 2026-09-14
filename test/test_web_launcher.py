import unittest
from unittest.mock import patch, Mock
from urllib.error import URLError

import run_web_exe


class WebLauncherTest(unittest.TestCase):
    def test_browser_waits_for_a_successful_http_response(self):
        with (
            patch('urllib.request.urlopen', side_effect=[URLError('starting'), unittest.mock.MagicMock()]) as probe,
            patch('time.sleep'),
            patch('webbrowser.open') as browser,
        ):
            run_web_exe.open_browser('http://127.0.0.1:8080/')
        self.assertEqual(probe.call_count, 2)
        browser.assert_called_once_with('http://127.0.0.1:8080/')

    def test_browser_does_not_open_when_server_never_starts(self):
        with (
            patch('urllib.request.urlopen', side_effect=URLError('offline')),
            patch('time.monotonic', side_effect=[0, 0, 61]),
            patch('time.sleep'),
            patch('webbrowser.open') as browser,
        ):
            run_web_exe.open_browser('http://127.0.0.1:8080/')
        browser.assert_not_called()

    def test_parser_defaults_to_browser_port_8080_and_sniffer(self):
        args = run_web_exe.parse_args([])
        self.assertEqual(args.host, "127.0.0.1")
        self.assertEqual(args.port, 8080)
        self.assertTrue(args.browser)
        self.assertTrue(args.sniffer)

    def test_main_passes_local_web_options_and_opens_browser(self):
        calls = []
        with (
            patch.object(run_web_exe, "open_browser") as open_mock,
            patch.object(run_web_exe, "run_server", side_effect=lambda **kwargs: calls.append(kwargs)),
            patch.object(run_web_exe.threading, "Timer", side_effect=lambda _delay, callback, args: Mock(start=lambda: callback(*args))),
        ):
            run_web_exe.main(["--host", "127.0.0.1", "--port", "9876", "--no-sniffer"])

        self.assertEqual(calls, [{"host": "127.0.0.1", "port": 9876, "start_sniffer": False}])
        open_mock.assert_called_once_with("http://127.0.0.1:9876/")

    def test_no_browser_never_opens_external_browser(self):
        with (
            patch.object(run_web_exe, "open_browser") as open_mock,
            patch.object(run_web_exe, "run_server"),
        ):
            run_web_exe.main(["--no-browser", "--no-sniffer"])

        open_mock.assert_not_called()


if __name__ == "__main__":
    unittest.main()
