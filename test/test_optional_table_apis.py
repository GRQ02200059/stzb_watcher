import os
import sqlite3
import tempfile
import unittest
from unittest.mock import patch

import api_server


class OptionalTableApisTest(unittest.TestCase):
    def setUp(self):
        fd, self.db_path = tempfile.mkstemp(suffix=".db")
        os.close(fd)

    def tearDown(self):
        try:
            os.unlink(self.db_path)
        except FileNotFoundError:
            pass

    def _connect(self):
        conn = sqlite3.connect(self.db_path)
        conn.row_factory = sqlite3.Row
        return conn

    def test_announcements_missing_table_returns_empty_list(self):
        with patch("api_server.get_db", self._connect):
            response = api_server.app.test_client().get("/api/announcements")

        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.get_json(), [])


if __name__ == "__main__":
    unittest.main()
