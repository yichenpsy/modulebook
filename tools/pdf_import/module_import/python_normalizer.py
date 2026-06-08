import json
import re
from pathlib import Path

from tools.pdf_import.module_import.config import (
    PDF_IMPORT_ROOT,
    get_imports,
    load_base_config,
)
from tools.pdf_import.module_import.json_validator import validate_extracted_module


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


def get_field_map(base_config: dict) -> dict:
    return base_config.get("normalized_field_map", {})


def get_normalized_known_labels(field_map: dict) -> set[str]:
    labels = set()

    for aliases in field_map.values():
        labels.update(aliases)

    return labels


def get_first_field(fields: dict, labels: list[str]) -> str | None:
    for label in labels:
        value = fields.get(label)

        if value is not None and str(value).strip():
            return str(value).strip()

    return None


def get_mapped_field(fields: dict, field_map: dict, normalized_name: str) -> str | None:
    labels = field_map.get(normalized_name, [])
    return get_first_field(fields, labels)


def parse_credits(value: str | None) -> float | None:
    if not value:
        return None

    match = re.search(r"(\d+(?:[,.]\d+)?)", value)

    if not match:
        return None

    number_text = match.group(1).replace(",", ".")

    return float(number_text)


def split_title(value: str | None) -> tuple[str, str | None]:
    if not value:
        return "", None

    value = value.strip()

    if " / " in value:
        parts = value.split(" / ", 1)
        return parts[0].strip(), parts[1].strip()

    if "/" in value:
        parts = value.split("/", 1)
        return parts[0].strip(), parts[1].strip()

    return value, None


def normalize_status(value: str | None) -> str | None:
    if not value:
        return None

    value = value.strip()

    if "Wahlpflichtmodul" in value:
        return "Wahlpflichtmodul"

    if "Pflichtmodul" in value:
        return "Pflichtmodul"

    return value


def split_text_list(value: str | None) -> list[str]:
    """
    Convert long content/literature text into a simple list.

    MVP logic:
    - split by bullet lines
    - split by numbered lines
    - keep continuation lines together
    """

    if not value:
        return []

    lines = [line.strip() for line in value.splitlines() if line.strip()]

    items = []
    current_item = []

    for line in lines:
        starts_new_item = (
            line.startswith("-")
            or line.startswith("•")
            or re.match(r"^\d+[\.\)]\s+", line) is not None
        )

        if starts_new_item:
            if current_item:
                items.append(" ".join(current_item).strip())

            current_item = [line.lstrip("-• ").strip()]
        else:
            if current_item:
                current_item.append(line)
            else:
                current_item = [line]

    if current_item:
        items.append(" ".join(current_item).strip())

    return [item for item in items if item]


def normalize_field_dict(field_dict_record: dict, base_config: dict) -> dict:
    fields = field_dict_record["fields"]
    field_map = get_field_map(base_config)

    title_raw = get_mapped_field(fields, field_map, "title")
    title_de, title_en = split_title(title_raw)

    credits_raw = get_mapped_field(fields, field_map, "credits")

    normalized = {
        "module_number": (
            get_mapped_field(fields, field_map, "module_number")
            or field_dict_record.get("module_number")
        ),
        "title_de": title_de,
        "title_en": title_en,
        "credits": parse_credits(credits_raw),

        "workload": get_mapped_field(fields, field_map, "workload"),
        "learning_area": get_mapped_field(fields, field_map, "learning_area"),
        "learning_objectives": get_mapped_field(fields, field_map, "learning_objectives"),
        "prerequisites": get_mapped_field(fields, field_map, "prerequisites"),
        "level": get_mapped_field(fields, field_map, "level"),
        "teaching_form": get_mapped_field(fields, field_map, "teaching_form"),
        "status": normalize_status(get_mapped_field(fields, field_map, "status")),
        "frequency": get_mapped_field(fields, field_map, "frequency"),
        "exam_form": get_mapped_field(fields, field_map, "exam_form"),
        "module_grade": get_mapped_field(fields, field_map, "module_grade"),
        "recognized_modules": get_mapped_field(fields, field_map, "recognized_modules"),

        "contents": split_text_list(get_mapped_field(fields, field_map, "contents")),
        "literature": split_text_list(get_mapped_field(fields, field_map, "literature")),

        "additional_notes": get_mapped_field(fields, field_map, "additional_notes"),
        "room_requirements": get_mapped_field(fields, field_map, "room_requirements"),

        "additional_fields": {},
        "source_file": field_dict_record["source_file"],
        "source_pages": field_dict_record["source_pages"],
    }

    known_labels = get_normalized_known_labels(field_map)

    for label, value in fields.items():
        if label not in known_labels:
            normalized["additional_fields"][label] = value

    return normalized


def run_python_normalization() -> None:
    base_config = load_base_config()
    imports = get_imports()

    all_valid = []
    all_need_review = []

    for import_item in imports:
        import_id = import_item["import_id"]

        input_path = (
            PDF_IMPORT_ROOT
            / "data"
            / "work"
            / import_id
            / "field_dicts.jsonl"
        )

        output_path = (
            PDF_IMPORT_ROOT
            / "data"
            / "work"
            / import_id
            / "normalized_modules_python.jsonl"
        )

        field_dicts = load_jsonl(input_path)

        normalized_modules = []

        for record in field_dicts:
            normalized = normalize_field_dict(record, base_config)
            validation = validate_extracted_module(normalized)

            normalized["_import_id"] = import_id
            normalized["study_program"] = import_item.get("study_program")
            normalized["study_program_code"] = import_item.get("study_program_code")
            normalized["_validation_status"] = validation["validation_status"]
            normalized["_validation_errors"] = validation["validation_errors"]
            normalized["_validation_warnings"] = validation["validation_warnings"]

            normalized_modules.append(normalized)

            if validation["validation_status"] == "valid":
                all_valid.append(normalized)
            else:
                all_need_review.append(normalized)

        write_jsonl(output_path, normalized_modules)

        print(f"Written normalized modules: {output_path}")

    output_dir = PDF_IMPORT_ROOT / "data" / "output"

    write_jsonl(output_dir / "modules_valid_python.jsonl", all_valid)
    write_jsonl(output_dir / "modules_need_review_python.jsonl", all_need_review)

    print(f"Valid modules: {len(all_valid)}")
    print(f"Need review: {len(all_need_review)}")


if __name__ == "__main__":
    run_python_normalization()
