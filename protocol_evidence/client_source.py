from pathlib import Path, PurePosixPath
import re


_COMMAND_CONSTANT = re.compile(
    r"^\s*public\s+const\s+int\s+([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(\d+)\s*;\s*$"
)


def extract_command_constants(net_command_def: Path) -> dict[int, list[str]]:
    source = Path(net_command_def)
    names_to_values = {}
    values_to_names = {}
    for line in source.read_text(encoding="utf-8").splitlines():
        match = _COMMAND_CONSTANT.fullmatch(line)
        if match is None:
            continue
        name = match.group(1)
        value = int(match.group(2))
        existing = names_to_values.get(name)
        if existing is not None and existing != value:
            raise ValueError(
                f"conflicting command constant {name}: {existing} != {value}"
            )
        names_to_values[name] = value
        values_to_names.setdefault(value, set()).add(name)
    return {
        value: sorted(names)
        for value, names in sorted(values_to_names.items())
    }


def validate_source_anchor(client_root: Path, anchor: dict) -> None:
    if not isinstance(anchor, dict):
        raise ValueError("source anchor must be an object")
    file_name = anchor.get("file")
    lines = anchor.get("lines")
    if not isinstance(file_name, str) or not file_name:
        raise ValueError("source anchor file is required")
    relative = PurePosixPath(file_name)
    if relative.is_absolute() or ".." in relative.parts:
        raise ValueError("source anchor must use a safe relative path")
    if (
        not isinstance(lines, list)
        or len(lines) != 2
        or any(isinstance(value, bool) or not isinstance(value, int) for value in lines)
    ):
        raise ValueError("source anchor lines must be [start, end]")
    start, end = lines
    source = Path(client_root).joinpath(*relative.parts)
    if not source.is_file():
        raise ValueError(f"source anchor file does not exist: {file_name}")
    line_count = len(source.read_text(encoding="utf-8").splitlines())
    if start < 1 or end < start or end > line_count:
        raise ValueError(
            f"source anchor lines are out of range: {start}-{end}/{line_count}"
        )
