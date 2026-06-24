import csv
import json
from pathlib import Path

from tools.pdf_import.archive.module_import.config import (
    PROJECT_ROOT,
    PDF_IMPORT_ROOT,
    load_base_config,
    get_imports,
)
from tools.pdf_import.archive.module_import.field_parser import run_field_parsing
from tools.pdf_import.archive.module_import.pdf_reader import extract_pages
from tools.pdf_import.archive.module_import.python_normalizer import run_python_normalization
from tools.pdf_import.archive.module_import.text_cleaner import clean_pages
from tools.pdf_import.archive.module_import.module_splitter import (
    split_module_blocks,
    create_split_summary,
)


def write_jsonl(path: Path, records: list[dict]) -> None:
    # Ensure the output folder exists before writing JSON Lines data.
    path.parent.mkdir(parents=True, exist_ok=True)

    with path.open("w", encoding="utf-8") as f:
        for record in records:
            f.write(json.dumps(record, ensure_ascii=False) + "\n")


def list_to_cell(value: list) -> str:
    if not value:
        return ""
    return "; ".join(str(item) for item in value)


def run_single_import(import_item: dict, base_config: dict) -> dict:
    # One import item represents one source PDF from import_manifest.yaml.
    import_id = import_item["import_id"]
    source_file = import_item["source_file"]
    pdf_path = PROJECT_ROOT / source_file

    # Store intermediate files separately for each imported study program.
    work_dir = PDF_IMPORT_ROOT / "data" / "work" / import_id
    work_dir.mkdir(parents=True, exist_ok=True)

    # Read raw PDF pages first, then apply lightweight text cleanup.
    pages = extract_pages(pdf_path)
    cleaned_pages = clean_pages(pages)

    # pages.jsonl is useful for debugging PDF text extraction page by page.
    pages_output_path = work_dir / "pages.jsonl"
    write_jsonl(pages_output_path, cleaned_pages)

    # Split the full cleaned text into module-sized blocks using configured regexes.
    module_blocks = split_module_blocks(
        pages=cleaned_pages,
        module_start_patterns=base_config["module_start_patterns"],
        source_file=source_file,
    )

    # module_blocks.jsonl is the main input for later field extraction steps.
    blocks_output_path = work_dir / "module_blocks.jsonl"
    write_jsonl(blocks_output_path, module_blocks)

    # Compare the split result with optional quality settings from base.yaml/manifest.
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
    # The split report summarizes all imports in one CSV for quick review.
    output_path = PDF_IMPORT_ROOT / "data" / "output" / "split_report.csv"
    output_path.parent.mkdir(parents=True, exist_ok=True)

    fieldnames = [
        "import_id",
        "study_program",
        "status",
        "detected_module_count",
        "expected_module_count",
        "duplicate_module_numbers",
        "short_blocks",
        "blocks_missing_title",
        "source_file",
        "pages_output",
        "module_blocks_output",
        "blocks_missing_status",
    ]

    with output_path.open("w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(report_rows)


def run_pipeline() -> None:
    # The manifest must already exist so expected_module_count can be edited manually.
    base_config = load_base_config()
    imports = get_imports()

    report_rows = []

    # Process every PDF listed in import_manifest.yaml.
    for import_item in imports:
        report_row = run_single_import(import_item, base_config)
        report_rows.append(report_row)

    write_split_report(report_rows)

    # Convert split module blocks into parsed field dictionaries, then normalize them.
    run_field_parsing()
    run_python_normalization()


if __name__ == "__main__":
    run_pipeline()
