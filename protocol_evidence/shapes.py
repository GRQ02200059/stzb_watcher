import json
from pathlib import Path
import re


_NUMERIC_OBJECT_KEY = re.compile(r"(?<=[{,])\s*(-?\d+)\s*(?=:)")


def _type_name(value) -> str:
    if value is None:
        return "null"
    if isinstance(value, bool):
        return "boolean"
    if isinstance(value, int):
        return "integer"
    if isinstance(value, float):
        return "number"
    if isinstance(value, str):
        return "string"
    if isinstance(value, list):
        return "array"
    if isinstance(value, dict):
        return "object"
    raise TypeError(f"unsupported JSON value type: {type(value).__name__}")


def summarize_json_value(value) -> dict:
    root_type = _type_name(value)
    result = {"rootType": root_type}
    if isinstance(value, list):
        result["arrayLength"] = len(value)
        result["indexTypes"] = {
            str(index): [_type_name(item)]
            for index, item in enumerate(value)
        }
    elif isinstance(value, dict):
        result["objectKeyCount"] = len(value)
        result["objectValueTypes"] = sorted(
            {_type_name(item) for item in value.values()}
        )
    return result


def summarize_command_samples(
    repository_root: Path,
    sample_paths: list[str],
    limit: int = 64,
) -> dict:
    repository_root = Path(repository_root)
    paths = sorted(str(path) for path in sample_paths)
    scanned = paths[: max(0, int(limit))]
    summaries = []
    invalid_count = 0
    for relative_path in scanned:
        path = repository_root / relative_path
        try:
            text = path.read_text(encoding="utf-8")
            try:
                value = json.loads(text)
            except json.JSONDecodeError:
                normalized = _NUMERIC_OBJECT_KEY.sub(r'"\1"', text)
                value = json.loads(normalized)
            summaries.append(summarize_json_value(value))
        except (OSError, UnicodeError, json.JSONDecodeError):
            invalid_count += 1

    root_types = sorted({item["rootType"] for item in summaries})
    array_lengths = sorted(
        {
            int(item["arrayLength"])
            for item in summaries
            if item["rootType"] == "array"
        }
    )
    shape_signatures = {
        json.dumps(item, ensure_ascii=True, sort_keys=True, separators=(",", ":"))
        for item in summaries
    }
    return {
        "totalCount": len(paths),
        "scannedCount": len(scanned),
        "parsedCount": len(summaries),
        "invalidCount": invalid_count,
        "rootTypes": root_types,
        "arrayLengths": array_lengths,
        "drift": len(shape_signatures) > 1,
    }
