from pathlib import Path
import re


_HEX_ID = re.compile(r"[0-9a-fA-F]{1,8}")
_SAMPLE_NAME = re.compile(
    r"^cap_.+_([0-9a-fA-F]{8})_(.+)\.(?:json|txt)$"
)


def normalize_hex_id(value: str) -> str:
    text = str(value or "").strip()
    if _HEX_ID.fullmatch(text) is None:
        raise ValueError(f"invalid command id: {value!r}")
    return text.lower().zfill(8)


def decimal_command_id(hex_id: str) -> int:
    return int(normalize_hex_id(hex_id), 16)


def scan_capture_inventory(capture_root: Path) -> list[dict]:
    capture_root = Path(capture_root)
    if not capture_root.is_dir():
        raise ValueError(f"capture root is not a directory: {capture_root}")
    repository_root = capture_root.parent
    rows = []
    for command_dir in sorted(capture_root.iterdir(), key=lambda path: path.name):
        if not command_dir.is_dir():
            continue
        try:
            hex_id = normalize_hex_id(command_dir.name)
        except ValueError as error:
            raise ValueError(
                f"invalid command directory: {command_dir.name}"
            ) from error
        if command_dir.name.lower() != hex_id:
            raise ValueError(f"invalid command directory: {command_dir.name}")

        sample_paths = []
        decode_kinds = set()
        for sample in sorted(command_dir.iterdir(), key=lambda path: path.name):
            if not sample.is_file():
                continue
            match = _SAMPLE_NAME.fullmatch(sample.name)
            if match is None:
                continue
            if normalize_hex_id(match.group(1)) != hex_id:
                raise ValueError(
                    f"sample command id mismatch: {sample.relative_to(repository_root)}"
                )
            decode_kinds.add(match.group(2))
            sample_paths.append(sample.relative_to(repository_root).as_posix())

        rows.append(
            {
                "hexId": hex_id,
                "decimalId": decimal_command_id(hex_id),
                "count": len(sample_paths),
                "decodeKinds": sorted(decode_kinds),
                "samplePaths": sample_paths,
            }
        )
    return rows
