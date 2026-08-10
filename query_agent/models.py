from dataclasses import asdict, dataclass, field
from typing import Any, Dict, List


@dataclass(frozen=True)
class Evidence:
    source: str
    label: str
    entity_type: str
    entity_id: str
    freshness: str = "unknown"


@dataclass(frozen=True)
class UiAction:
    type: str
    route: str
    params: Dict[str, Any] = field(default_factory=dict)


@dataclass(frozen=True)
class QueryAgentResponse:
    ok: bool
    answer: str
    evidence: List[Evidence] = field(default_factory=list)
    ui_actions: List[UiAction] = field(default_factory=list)
    needs_clarification: bool = False
    error: str = ""
    data_completeness: str = "complete"

    def to_json(self) -> Dict[str, Any]:
        return {
            "ok": self.ok,
            "answer": self.answer,
            "evidence": [asdict(item) for item in self.evidence],
            "uiActions": [asdict(item) for item in self.ui_actions],
            "needsClarification": self.needs_clarification,
            "error": self.error,
            "dataCompleteness": self.data_completeness,
        }
