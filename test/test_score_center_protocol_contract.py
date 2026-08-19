import unittest
from pathlib import Path
from unittest.mock import Mock

from score_center.protocol_contract import require_score_protocol_contract


class ScoreCenterProtocolContractTest(unittest.TestCase):
    def test_requires_confirmed_member_wuxun(self):
        registry = Mock()
        registry.require_business_field.return_value = {
            "name": "memberWuxun",
            "evidence": "CLIENT_CONFIRMED",
        }
        result = require_score_protocol_contract(registry)
        registry.require_business_field.assert_called_once_with(
            "00000067", "[][10]"
        )
        self.assertEqual("memberWuxun", result["name"])

    def test_rejects_wrong_registered_field_name(self):
        registry = Mock()
        registry.require_business_field.return_value = {"name": "battleGongxun"}
        with self.assertRaisesRegex(ValueError, "memberWuxun"):
            require_score_protocol_contract(registry)


if __name__ == "__main__":
    unittest.main()
