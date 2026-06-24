import json
import re
from pathlib import Path

from tools.pdf_import.archive.module_import.config import (
    PDF_IMPORT_ROOT,
    load_base_config,
    get_imports,
)


NOISE_LINE_PATTERNS = [
    r"^Studienordnung Bachelor-Studiengang",
    r"^Modulhandbuch für den Bachelor-Studiengang",
    r"^Anlage\s+\d+\s*\|\s*Seite\s+\d+",
    r"^Datenfeld$",
    r"^Erklärung$",
]

INLINE_FIELD_VALUE_PATTERNS = {
    "Modulnummer": r"^(?:B\d{2}[a-z]?|WP\d{2})\b",
    "Module ID": r"^(?:B\d{2}[a-z]?|WP\d{2})\b",
    "Modul ID": r"^(?:B\d{2}[a-z]?|WP\d{2})\b",
    "Titel": r"^\S+",
    "Title": r"^\S+",
    "Leistungspunkte": r"\d",
    "Credits": r"\d",
    "Workload": r"\d",
    "Präsenzzeit": r"\d",
    "Status": r"^(?:Pflichtmodul|Wahlpflichtmodul)\b",
    "Häufigkeit des Angebotes": r"^\S+",
    "Turnus": r"^\S+",
}


def load_jsonl(path: Path) -> list[dict]:
    records = []

    with path.open("r", encoding="utf-8") as f:
        for line in f:
            if line.strip():
                records.append(json.loads(line))

    return records


def write_jsonl(path: Path, records: list[dict]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)

    with path.open("w", encoding="utf-8") as f:
        for record in records:
            f.write(json.dumps(record, ensure_ascii=False) + "\n")


def get_field_labels(base_config: dict) -> list[str]:
    labels = base_config.get("field_labels", [])

    # Longest first, important for labels like:
    # "Lernziele / Kompetenzen"
    return sorted(set(labels), key=len, reverse=True)


def is_noise_line(line: str) -> bool:
    stripped = line.strip()

    for pattern in NOISE_LINE_PATTERNS:
        if re.match(pattern, stripped):
            return True

    return False


def clean_lines(raw_text: str) -> list[str]:
    lines = []

    for line in raw_text.splitlines():
        cleaned = line.strip()

        if not cleaned:
            continue

        if is_noise_line(cleaned):
            continue

        lines.append(cleaned)

    return lines


def match_field_label(line: str, field_labels: list[str]) -> tuple[str | None, str]:
    """
    Match two cases:

    Case 1:
    Titel
    Gebäudeentwurf 1

    Case 2:
    Titel Gebäudeentwurf 1
    """

    stripped = line.strip()

    for label in field_labels:
        if stripped == label:
            return label, ""

        if stripped.startswith(label + " "):
            value = stripped[len(label):].strip()
            value_pattern = INLINE_FIELD_VALUE_PATTERNS.get(label)

            if value_pattern and re.search(value_pattern, value):
                return label, value

    return None, stripped


def parse_fields_from_raw_text(raw_text: str, field_labels: list[str]) -> dict:
    lines = clean_lines(raw_text)

    fields = {}
    current_label = None
    current_value_lines = []

    for line in lines:
        label, inline_value = match_field_label(line, field_labels)

        if label is not None:
            if current_label is not None:
                fields[current_label] = "\n".join(current_value_lines).strip()

            current_label = label
            current_value_lines = []

            if inline_value:
                current_value_lines.append(inline_value)

        else:
            if current_label is not None:
                current_value_lines.append(line)

    if current_label is not None:
        fields[current_label] = "\n".join(current_value_lines).strip()

    return fields


def parse_module_block(module_block: dict, base_config: dict) -> dict:
    field_labels = get_field_labels(base_config)
    fields = parse_fields_from_raw_text(module_block["raw_text"], field_labels)

    return {
        "module_number": module_block["module_number"],
        "source_file": module_block["source_file"],
        "source_pages": module_block["source_pages"],
        "fields": fields,
        "raw_text": module_block["raw_text"],
    }


def run_field_parsing() -> None:
    base_config = load_base_config()
    imports = get_imports()

    for import_item in imports:
        import_id = import_item["import_id"]

        module_blocks_path = (
            PDF_IMPORT_ROOT
            / "data"
            / "work"
            / import_id
            / "module_blocks.jsonl"
        )

        output_path = (
            PDF_IMPORT_ROOT
            / "data"
            / "work"
            / import_id
            / "field_dicts.jsonl"
        )

        module_blocks = load_jsonl(module_blocks_path)

        field_dicts = [
            parse_module_block(block, base_config)
            for block in module_blocks
        ]

        write_jsonl(output_path, field_dicts)

        print(f"Written field dicts: {output_path}")


if __name__ == "__main__":
    run_field_parsing()
