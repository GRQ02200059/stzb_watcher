import sqlite3
import tempfile
import unittest
from unittest.mock import patch

import realtime_writer
from test.test_world_scene_parser import world_payload


class WorldSceneWriterIntegrationTest(unittest.TestCase):
    def test_process_data_persists_world_scene_packet(self):
        with tempfile.NamedTemporaryFile(suffix=".db") as tmp:
            def connect():
                conn = sqlite3.connect(tmp.name)
                conn.row_factory = sqlite3.Row
                return conn

            with patch.object(realtime_writer, "get_db", side_effect=connect):
                writer = realtime_writer.RealtimeWriter()
                writer.process_data("000013a4", world_payload(marker=1), "fixture")

            conn = connect()
            try:
                row = conn.execute("SELECT COUNT(*) AS c FROM world_scene_packets").fetchone()
                self.assertEqual(row["c"], 1)
            finally:
                conn.close()


if __name__ == "__main__":
    unittest.main()
