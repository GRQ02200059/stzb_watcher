import unittest
from unittest.mock import patch

from query_agent.llm import build_llm_client_from_env


class QueryAgentLlmTest(unittest.TestCase):
    def test_builds_local_openai_client_without_api_key(self):
        client = build_llm_client_from_env(
            {
                "QUERY_AGENT_LLM_BASE_URL": "http://127.0.0.1:8088/v1",
                "QUERY_AGENT_LLM_MODEL": "local-model",
            }
        )

        self.assertIsNotNone(client)
        self.assertEqual(client.model_name, "local-model")
        self.assertEqual(client.config.api_key, "")

    def test_autodetects_local_llama_cpp_model(self):
        def fake_read_json(url, _timeout):
            self.assertEqual(url, "http://127.0.0.1:8088/v1/models")
            self.assertGreater(_timeout, 0)
            return {"data": [{"id": "llama-local.gguf"}]}

        with patch("query_agent.llm._read_json_url", side_effect=fake_read_json):
            client = build_llm_client_from_env({})

        self.assertIsNotNone(client)
        self.assertEqual(client.model_name, "llama-local.gguf")
        self.assertEqual(client.config.base_url, "http://127.0.0.1:8088/v1")


if __name__ == "__main__":
    unittest.main()
