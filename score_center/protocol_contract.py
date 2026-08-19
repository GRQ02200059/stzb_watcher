from pathlib import Path

from protocol_registry import ProtocolRegistry


DEFAULT_PROTOCOL_ROOT = (
    Path(__file__).resolve().parents[1] / "data/protocol/client-9.2.2"
)


def require_score_protocol_contract(registry=None):
    registry = registry or ProtocolRegistry(DEFAULT_PROTOCOL_ROOT)
    field = registry.require_business_field("00000067", "[][10]")
    if field.get("name") != "memberWuxun":
        raise ValueError(
            "Score Center requires the confirmed memberWuxun field "
            "from command 00000067 path [][10]"
        )
    return field
