import csv
import json
from pathlib import Path

from tools.pdf_import.module_import.config import PDF_IMPORT_ROOT
from tools.pdf_import.module_import.json_validator import parse_llm_json


def read_raw_llm_file(path: Path) -> dict | None:
    """
    Read one raw LLM output file and parse it as JSON.
    """

    with path.open("r", encoding="utf-8") as f:
        raw_text = f.read()

    module_data, parse_error = parse_llm_json(raw_text)

    if parse_error is not None:
        print(f"Could not parse JSON: {path}")
        print(parse_error)
        return None

    return module_data


def collect_additional_fields(raw_files: list[Path]) -> list[dict]:
    """
    Collect additional_fields from multiple extracted module JSON files.
    """

    rows = []

    for raw_file in raw_files:
        module_data = read_raw_llm_file(raw_file)

        if module_data is None:
            continue

        additional_fields = module_data.get("additional_fields", {})

        if not isinstance(additional_fields, dict):
            continue

        for field_label, field_value in additional_fields.items():
            rows.append({
                "raw_file": str(raw_file),
                "source_file": module_data.get("source_file"),
                "module_number": module_data.get("module_number"),
                "field_label": field_label,
                "field_value": field_value,
            })

    return rows


def write_additional_fields_report(rows: list[dict]) -> Path:
    """
    Write additional fields to CSV report.
    """

    output_path = PDF_IMPORT_ROOT / "data" / "output" / "additional_fields_report.csv"
    output_path.parent.mkdir(parents=True, exist_ok=True)

    fieldnames = [
        "raw_file",
        "source_file",
        "module_number",
        "field_label",
        "field_value",
    ]

    with output_path.open("w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)

    return output_path


def create_report_from_review_folder() -> Path:
    """
    Read all llm_raw_*.txt files in data/review and create a CSV report.
    """

    review_dir = PDF_IMPORT_ROOT / "data" / "review"

    raw_files = sorted(review_dir.glob("llm_raw_*.txt"))

    rows = collect_additional_fields(raw_files)

    output_path = write_additional_fields_report(rows)

    return output_path


if __name__ == "__main__":
    output_path = create_report_from_review_folder()
    print(f"Additional fields report written to: {output_path}")