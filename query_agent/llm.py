import json
import os
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from typing import Mapping, Optional


SYSTEM_PROMPT = """你是率土战场指挥台的只读 Query Agent。
你只能基于用户消息、白名单页面上下文、草稿答案、证据和 UI 动作回答。
不要编造数据库中没有的事实；证据不足时要求用户补充 WID、队伍 ID、玩家名或战报 ID。
禁止执行或承诺任何游戏动作、发包、自动化、数据库写入、shell 或文件操作。
回答使用简体中文，优先简洁、可执行，并保留关键 ID。
不要输出思考过程、<think> 标签或与答案无关的推理文本。"""


@dataclass(frozen=True)
class LlmConfig:
    base_url: str
    api_key: str
    model: str
    timeout_seconds: float = 60.0
    temperature: float = 0.2
    max_tokens: int = 700


class OpenAICompatibleLlmClient:
    def __init__(self, config: LlmConfig) -> None:
        self.config = config
        self.model_name = config.model

    def answer(self, context: dict) -> str:
        payload = {
            "model": self.config.model,
            "messages": [
                {"role": "system", "content": SYSTEM_PROMPT},
                {
                    "role": "user",
                    "content": json.dumps(context, ensure_ascii=False, sort_keys=True),
                },
            ],
            "temperature": self.config.temperature,
            "max_tokens": self.config.max_tokens,
        }
        data = json.dumps(payload).encode("utf-8")
        headers = {"Content-Type": "application/json"}
        if self.config.api_key:
            headers["Authorization"] = f"Bearer {self.config.api_key}"
        request = urllib.request.Request(
            _chat_completions_url(self.config.base_url),
            data=data,
            headers=headers,
            method="POST",
        )
        try:
            with urllib.request.urlopen(
                request, timeout=self.config.timeout_seconds
            ) as response:
                body = json.loads(response.read().decode("utf-8"))
        except urllib.error.HTTPError as error:
            detail = error.read().decode("utf-8", errors="replace")
            raise RuntimeError(f"LLM HTTP {error.code}: {detail[:300]}") from error
        except urllib.error.URLError as error:
            raise RuntimeError(f"LLM request failed: {error.reason}") from error

        try:
            return _strip_reasoning(body["choices"][0]["message"]["content"])
        except (KeyError, IndexError, TypeError) as error:
            raise RuntimeError("LLM response missing choices[0].message.content") from error


def build_llm_client_from_env(
    env: Optional[Mapping[str, str]] = None,
) -> Optional[OpenAICompatibleLlmClient]:
    source = os.environ if env is None else env
    api_key = (
        source.get("QUERY_AGENT_LLM_API_KEY")
        or source.get("OPENAI_API_KEY")
        or source.get("ARK_API_KEY")
        or ""
    )
    model = (
        source.get("QUERY_AGENT_LLM_MODEL")
        or source.get("OPENAI_MODEL")
        or source.get("ARK_MODEL")
        or source.get("ARK_CHAT_MODEL")
        or ""
    )
    base_url = (
        source.get("QUERY_AGENT_LLM_BASE_URL")
        or source.get("OPENAI_BASE_URL")
        or source.get("ARK_BASE_URL")
        or ""
    )
    timeout = _float_env(source, "QUERY_AGENT_LLM_TIMEOUT_SECONDS", 60.0)
    temperature = _float_env(source, "QUERY_AGENT_LLM_TEMPERATURE", 0.2)
    max_tokens = _int_env(source, "QUERY_AGENT_LLM_MAX_TOKENS", 700)

    if base_url and not model:
        model = _discover_model_id(base_url, timeout)
    if not base_url and not model and not api_key:
        local = _detect_local_llm(timeout)
        if local is not None:
            base_url, model = local

    if not model:
        return None

    if not base_url:
        base_url = (
            "https://ark.cn-beijing.volces.com/api/v3"
            if source.get("ARK_API_KEY")
            else "https://api.openai.com/v1"
        )

    return OpenAICompatibleLlmClient(
        LlmConfig(
            base_url=base_url,
            api_key=api_key,
            model=model,
            timeout_seconds=timeout,
            temperature=temperature,
            max_tokens=max_tokens,
        )
    )


def _chat_completions_url(base_url: str) -> str:
    normalized = base_url.rstrip("/")
    if normalized.endswith("/chat/completions"):
        return normalized
    path = urllib.parse.urlparse(normalized).path.rstrip("/")
    if path in ("", "/"):
        return f"{normalized}/v1/chat/completions"
    if path.endswith("/v1") or path.endswith("/api/v3"):
        return f"{normalized}/chat/completions"
    return f"{normalized}/chat/completions"


def _models_url(base_url: str) -> str:
    normalized = base_url.rstrip("/")
    path = urllib.parse.urlparse(normalized).path.rstrip("/")
    if path in ("", "/"):
        return f"{normalized}/v1/models"
    if path.endswith("/chat/completions"):
        return f"{normalized[: -len('/chat/completions')]}/models"
    return f"{normalized}/models"


def _detect_local_llm(timeout: float) -> Optional[tuple[str, str]]:
    for base_url in (
        "http://127.0.0.1:8088/v1",
        "http://127.0.0.1:11434/v1",
        "http://127.0.0.1:1234/v1",
        "http://127.0.0.1:8000/v1",
    ):
        model = _discover_model_id(base_url, timeout)
        if model:
            return base_url, model
    return None


def _discover_model_id(base_url: str, timeout: float) -> str:
    payload = _read_json_url(_models_url(base_url), timeout)
    for key in ("data", "models"):
        items = payload.get(key)
        if isinstance(items, list):
            for item in items:
                if isinstance(item, dict):
                    model = item.get("id") or item.get("model") or item.get("name")
                    if model:
                        return str(model)
    return ""


def _read_json_url(url: str, timeout: float) -> dict:
    request = urllib.request.Request(url, headers={"Accept": "application/json"})
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            return json.loads(response.read().decode("utf-8"))
    except (
        json.JSONDecodeError,
        OSError,
        TimeoutError,
        urllib.error.HTTPError,
        urllib.error.URLError,
    ):
        return {}


def _strip_reasoning(text: str) -> str:
    result = str(text or "").strip()
    while "<think>" in result and "</think>" in result:
        start = result.find("<think>")
        end = result.find("</think>", start) + len("</think>")
        result = (result[:start] + result[end:]).strip()
    return result


def _float_env(source: Mapping[str, str], key: str, default: float) -> float:
    try:
        return float(source.get(key, default))
    except (TypeError, ValueError):
        return default


def _int_env(source: Mapping[str, str], key: str, default: int) -> int:
    try:
        return int(source.get(key, default))
    except (TypeError, ValueError):
        return default
