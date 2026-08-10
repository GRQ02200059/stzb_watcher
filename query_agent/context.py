DROP_KEYS = {
    "rawPacket",
    "rawPackets",
    "payload",
    "cmd",
    "logs",
    "filePath",
    "dbPath",
    "token",
    "code",
    "trace",
}

REJECT_KEYS = {
    "sql",
    "shell",
}

ALLOWED_PAGE_KEYS = {
    "page",
    "selectedWid",
    "selectedArmyId",
    "selectedBattleId",
    "selectedUserId",
    "query",
    "timeRange",
    "filters",
}


def build_query_context(message, page_context):
    if not isinstance(message, str) or not message.strip():
        raise ValueError("message is required")
    clean = {}
    for key, value in (page_context or {}).items():
        if key in REJECT_KEYS:
            raise ValueError(f"context key is forbidden: {key}")
        if key in DROP_KEYS:
            continue
        if key in ALLOWED_PAGE_KEYS:
            clean[key] = value
    return {"message": message.strip(), "pageContext": clean}
