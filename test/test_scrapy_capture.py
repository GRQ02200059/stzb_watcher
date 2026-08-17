import unittest
from unittest.mock import patch

import scrapy_v2


class ScrapyCaptureTest(unittest.TestCase):
    def test_run_sniff_disables_promiscuous_mode_on_macos(self):
        with scrapy_v2._bind_lock:
            scrapy_v2._bound_src_ip = None
        scrapy_v2._sniff_stop_event.clear()

        with patch.object(scrapy_v2, "sniff", return_value=None) as fake_sniff:
            scrapy_v2.run_sniff()

        self.assertEqual(fake_sniff.call_count, 1)
        self.assertFalse(fake_sniff.call_args.kwargs["promisc"])


if __name__ == "__main__":
    unittest.main()
