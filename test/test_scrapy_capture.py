import unittest
from unittest.mock import patch

import scrapy_v2
from scapy.layers.inet import IP, TCP
from scapy.packet import Raw


class ScrapyCaptureTest(unittest.TestCase):
    def test_run_sniff_disables_promiscuous_mode_on_macos(self):
        with scrapy_v2._bind_lock:
            scrapy_v2._bound_src_ip = None
        scrapy_v2._sniff_stop_event.clear()

        with patch.object(scrapy_v2, "sniff", return_value=None) as fake_sniff:
            scrapy_v2.run_sniff()

        self.assertEqual(fake_sniff.call_count, 1)
        self.assertFalse(fake_sniff.call_args.kwargs["promisc"])

    def test_packet_callback_isolates_parser_failure_and_keeps_capture_alive(self):
        packet = IP(src="198.51.100.10", dst="192.0.2.10") / TCP(
            sport=8001,
            dport=50000,
        ) / Raw(b"broken-payload")
        next_packet = packet.copy()
        next_packet[Raw].load = b"next-payload-xx"
        scrapy_v2.stream_bufs.clear()

        with patch.object(
            scrapy_v2,
            "process_one_packet",
            side_effect=[RuntimeError("simulated parser failure"), 0],
        ) as parser:
            scrapy_v2.process_packet(packet)
            scrapy_v2.process_packet(next_packet)

        key = ("198.51.100.10", 8001, 50000)
        self.assertEqual(2, parser.call_count)
        self.assertEqual(
            bytearray(b"next-payload-xx"),
            scrapy_v2.stream_bufs[key],
        )


if __name__ == "__main__":
    unittest.main()
