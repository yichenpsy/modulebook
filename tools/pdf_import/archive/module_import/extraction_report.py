import csv
import json
from pathlib import Path

from tools.pdf_import.archive.module_import.config import PDF_IMPORT_ROOT


def load_jsonl(path: Path) -> list[dict]:
    records = []

    with path.open("r", encoding="utf-8") as f:
        for line in f:
            if line.strip():
                records.append(json.loads(line))

    return records


def write_csv(path: Path, rows: list[dict], fieldnames: list[str]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)

    with path.open("w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)


def total_chars(values: list[str]) -> int:
    if not isinstance(values, list):
        return 0

    return sum(len(str(value).strip()) for value in values)


def list_keys(value: dict) -> str:
    if not isinstance(value, dict) or not value:
        return ""

    return "; ".join(value.keys())


def to_json_cell(value) -> str:
    return json.dumps(value, ensure_ascii=False)


def create_module_report_rows(modules: list[dict]) -> list[dict]:
    rows = []

    for module in modules:
        contents = module.get("contents", [])
        literature = module.get("literature", [])
        additional_fields = module.get("additional_fields", {})

        rows.append({
            "_import_id": module.get("_import_id"),
            "module_number": module.get("module_number"),
            "title_de": module.get("title_de"),
            "credits": module.get("credits"),
            "status": module.get("status"),
            "contents_count": len(contents) if isinstance(contents, list) else 0,
            "contents_chars": total_chars(contents),
            "literature_count": len(literature) if isinstance(literature, list) else 0,
            "literature_chars": total_chars(literature),
            "additional_fields_keys": list_keys(additional_fields),
            "source_pages": to_json_cell(module.get("source_pages")),
            "validation_warnings": " | ".join(module.get("_validation_warnings", [])),
        })

    return rows


def create_additional_fields_rows(modules: list[dict]) -> list[dict]:
    rows = []

    for module in modules:
        additional_fields = module.get("additional_fields", {})

        if not isinstance(additional_fields, dict):
            continue

        for field_label, field_value in additional_fields.items():
            rows.append({
                "_import_id": module.get("_import_id"),
                "module_number": module.get("module_number"),
                "source_file": module.get("source_file"),
                "source_pages": to_json_cell(module.get("source_pages")),
                "field_label": field_label,
                "field_value": field_value,
            })

    return rows


def run_extraction_report() -> None:
    input_path = PDF_IMPORT_ROOT / "data" / "output" / "modules_valid_python.jsonl"

    modules = load_jsonl(input_path)

    module_report_rows = create_module_report_rows(modules)
    additional_fields_rows = create_additional_fields_rows(modules)

    output_dir = PDF_IMPORT_ROOT / "data" / "output"

    write_csv(
        output_dir / "python_extraction_report.csv",
        module_report_rows,
        fieldnames=[
            "_import_id",
            "module_number",
            "title_de",
            "credits",
            "status",
            "contents_count",
            "contents_chars",
            "literature_count",
            "literature_chars",
            "additional_fields_keys",
            "source_pages",
            "validation_warnings",
        ],
    )

    write_csv(
        output_dir / "additional_fields_report_python.csv",
        additional_fields_rows,
        fieldnames=[
            "_import_id",
            "module_number",
            "source_file",
            "source_pages",
            "field_label",
            "field_value",
        ],
    )

    print("Extraction reports written.")
    print(f"Modules in report: {len(module_report_rows)}")
    print(f"Additional field rows: {len(additional_fields_rows)}")


if __name__ == "__main__":
    run_extraction_report()