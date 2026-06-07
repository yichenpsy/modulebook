import csv
import json
from pathlib import Path

from tools.pdf_import.module_import.config import (
    PROJECT_ROOT,
    PDF_IMPORT_ROOT,
    load_base_config,
    get_imports,
)
from tools.pdf_import.module_import.pdf_reader import extract_pages
from tools.pdf_import.module_import.text_cleaner import clean_pages
from tools.pdf_import.module_import.module_splitter import (
    split_module_blocks,
    create_split_summary,
)


def write_jsonl(path: Path, records: list[dict]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)

    with path.open("w", encoding="utf-8") as f:
        for record in records:
            f.write(json.dumps(record, ensure_ascii=False) + "\n")


def list_to_cell(value: list) -> str:
    if not value:
        return ""
    return "; ".join(str(item) for item in value)


def run_single_import(import_item: dict, base_config: dict) -> dict:
    import_id = import_item["import_id"]
    source_file = import_item["source_file"]
    pdf_path = PROJECT_ROOT / source_file

    work_dir = PDF_IMPORT_ROOT / "data" / "work" / import_id
    work_dir.mkdir(parents=True, exist_ok=True)

    pages = extract_pages(pdf_path)
    cleaned_pages = clean_pages(pages)

    pages_output_path = work_dir / "pages.jsonl"
    write_jsonl(pages_output_path, cleaned_pages)

    module_blocks = split_module_blocks(
        pages=cleaned_pages,
        module_start_patterns=base_config["module_start_patterns"],
        source_file=source_file,
    )

    blocks_output_path = work_dir / "module_blocks.jsonl"
    write_jsonl(blocks_output_path, module_blocks)

    quality_checks = base_config.get("quality_checks", {})
    min_block_chars = quality_checks.get("min_block_chars", 500)

    summary = create_split_summary(
        module_blocks=module_blocks,
        expected_module_count=import_item.get("expected_module_count"),
        min_block_chars=min_block_chars,
    )

    return {
        "import_id": import_id,
        "study_program": import_item.get("study_program"),
        "source_file": source_file,
        "pages_output": str(pages_output_path),
        "module_blocks_output": str(blocks_output_path),
        "detected_module_count": summary["detected_module_count"],
        "expected_module_count": summary["expected_module_count"],
        "duplicate_module_numbers": list_to_cell(summary["duplicate_module_numbers"]),
        "short_blocks": list_to_cell(summary["short_blocks"]),
        "blocks_missing_title": list_to_cell(summary["blocks_missing_title"]),
        "blocks_missing_status": list_to_cell(summary["blocks_missing_status"]),
        "status": summary["status"],
    }


def write_split_report(report_rows: list[dict]) -> None:
    output_path = PDF_IMPORT_ROOT / "data" / "output" / "split_report.csv"
    output_path.parent.mkdir(parents=True, exist_ok=True)

    fieldnames = [
        "import_id",
        "study_program",
        "source_file",
        "pages_output",
        "module_blocks_output",
        "detected_module_count",
        "expected_module_count",
        "duplicate_module_numbers",
        "short_blocks",
        "blocks_missing_title",
        "blocks_missing_status",
        "status",
    ]

    with output_path.open("w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(report_rows)


def run_pipeline() -> None:
    base_config = load_base_config()
    imports = get_imports()

    report_rows = []

    for import_item in imports:
        report_row = run_single_import(import_item, base_config)
        report_rows.append(report_row)

    write_split_report(report_rows)


if __name__ == "__main__":
    run_pipeline()