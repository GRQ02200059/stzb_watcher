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
    llm_used: bool = False
    llm_model: str = ""
    llm_error: str = ""

    def to_json(self) -> Dict[str, Any]:
        return {
            "ok": self.ok,
            "answer": self.answer,
            "evidence": [
                {
                    "source": item.source,
                    "label": item.label,
                    "entityType": item.entity_type,
                    "entityId": item.entity_id,
                    "freshness": item.freshness,
                }
                for item in self.evidence
            ],
            "uiActions": [asdict(item) for item in self.ui_actions],
            "needsClarification": self.needs_clarification,
            "error": self.error,
            "dataCompleteness": self.data_completeness,
            "llmUsed": self.llm_used,
            "llmModel": self.llm_model,
            "llmError": self.llm_error,
        }
