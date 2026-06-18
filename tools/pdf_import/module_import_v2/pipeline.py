import argparse
import csv
import json
from collections import Counter
from pathlib import Path

import yaml

from tools.pdf_import.module_import.config import PDF_IMPORT_ROOT
from tools.pdf_import.module_import_v2.extract_module_pages import (
    build_label_to_field_map,
    find_first_module_page,
    find_last_module_page,
    load_field_label_map,
    write_boundary_pages_csv,
)
from tools.pdf_import.module_import_v2.module_splitter import split_modules_to_json
from tools.pdf_import.module_import_v2.normalize_modules_from_md import (
    normalize_modules_file,
)
from tools.pdf_import.module_import_v2.pdf_to_pages_markdown import (
    convert_pdf_to_pages_markdown,
)


DEFAULT_INPUT_DIR = PDF_IMPORT_ROOT / "data" / "input"
DEFAULT_WORK_ROOT = PDF_IMPORT_ROOT / "data" / "work_v2"
DEFAULT_OUTPUT_REPORT = PDF_IMPORT_ROOT / "data" / "output" / "v2_import_report.csv"
DEFAULT_BASE_YAML = PDF_IMPORT_ROOT / "configs" / "base.yaml"


def load_base_config(path: Path) -> dict:
    with path.open("r", encoding="utf-8") as file:
        return yaml.safe_load(file)


def list_input_pdfs(input_dir: Path) -> list[Path]:
    return sorted(
        path
        for path in input_dir.glob("*.pdf")
        if path.is_file()
    )


def non_empty(value) -> bool:
    if value is None:
        return False

    if isinstance(value, str):
        return bool(value.strip())

    if isinstance(value, (list, dict)):
        return bool(value)

    return True


def required_fields_for_v2(base_config: dict) -> list[str]:
    required_fields = []

    for field_name in base_config.get("required_fields", []):
        if field_name in {"title_de", "title_en"}:
            field_name = "title"

        if field_name not in required_fields:
            required_fields.append(field_name)

    return required_fields


def required_field_summary(
    normalized_modules: list[dict],
    required_fields: list[str],
) -> tuple[bool, list[dict]]:
    if not normalized_modules:
        return False, []

    modules_missing_required_fields = []

    for module in normalized_modules:
        missing_fields = [
            field_name
            for field_name in required_fields
            if not non_empty(module.get(field_name))
        ]

        if missing_fields:
            modules_missing_required_fields.append(
                {
                    "module_number": module.get("module_number"),
                    "missing_fields": missing_fields,
                }
            )

    return not modules_missing_required_fields, modules_missing_required_fields


def compact_label(value: str, max_chars: int = 160) -> str:
    value = " ".join(str(value).split())

    if len(value) <= max_chars:
        return value

    return value[: max_chars - 3] + "..."


def counter_json(values: list[str]) -> str:
    return json.dumps(
        dict(Counter(compact_label(value) for value in values).most_common()),
        ensure_ascii=False,
    )


def process_pdf(
    pdf_path: Path,
    work_root: Path,
    base_yaml: Path,
    base_config: dict,
) -> dict:
    pdf_name = pdf_path.stem
    work_dir = work_root / pdf_name

    pages_json = work_dir / "pages_md.json"
    boundary_csv = work_dir / "module_boundary_pages.csv"
    module_blocks_json = work_dir / "module_blocks_md.json"
    normalized_json = work_dir / "normalized_modules.json"
    normalized_jsonl = work_dir / "normalized_modules.jsonl"

    pages = convert_pdf_to_pages_markdown(pdf_path, pages_json)

    field_label_map = load_field_label_map(base_yaml)
    label_to_field = build_label_to_field_map(field_label_map)

    first_module_page = find_first_module_page(
        pages,
        label_to_field,
        field_label_map,
        min_field_count=10,
    )
    last_module_page = find_last_module_page(
        pages,
        label_to_field,
        field_label_map,
        min_field_count=2,
    )

    write_boundary_pages_csv(
        boundary_csv,
        pdf_name,
        first_module_page,
        last_module_page,
    )

    first_page_number = None
    if first_module_page is not None:
        first_page_number = first_module_page["first_module_page_number"]

    last_page_number = None
    if last_module_page is not None:
        last_page_number = last_module_page["last_module_page_number"]

    module_blocks = split_modules_to_json(
        pages_json=pages_json,
        output_json=module_blocks_json,
        base_yaml=base_yaml,
        first_page_number=first_page_number,
        last_page_number=last_page_number,
        source_file=str(pdf_path.relative_to(PDF_IMPORT_ROOT.parents[1])),
    )

    normalized_modules, normalize_stats = normalize_modules_file(
        input_path=module_blocks_json,
        output_path=normalized_json,
        base_path=base_yaml,
        jsonl_output_path=normalized_jsonl,
    )

    required_fields = required_fields_for_v2(base_config)
    has_all_required_fields, missing_required_fields = required_field_summary(
        normalized_modules,
        required_fields,
    )

    status = "ok"
    if first_module_page is None or last_module_page is None:
        status = "needs_review"
    if not module_blocks:
        status = "failed"

    return {
        "pdf_name": pdf_name,
        "status": status,
        "page_count": len(pages),
        "first_module_page": first_page_number,
        "last_module_page": last_page_number,
        "module_count": len(normalized_modules),
        "has_all_required_fields": has_all_required_fields,
        "missing_required_fields": json.dumps(
            missing_required_fields,
            ensure_ascii=False,
        ),
        "unmatched_label_count": len(normalize_stats["unmatched_labels"]),
        "matched_labels": counter_json(normalize_stats["matched_labels"]),
        "unmatched_labels": counter_json(normalize_stats["unmatched_labels"]),
        "work_dir": str(work_dir),
        "pages_output": str(pages_json),
        "module_blocks_output": str(module_blocks_json),
        "normalized_output": str(normalized_json),
        "normalized_jsonl_output": str(normalized_jsonl),
    }


def write_report(path: Path, rows: list[dict]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)

    fieldnames = [
        "pdf_name",
        "status",
        "page_count",
        "first_module_page",
        "last_module_page",
        "module_count",
        "has_all_required_fields",
        "missing_required_fields",
        "unmatched_label_count",
        "matched_labels",
        "unmatched_labels",
        "work_dir",
        "pages_output",
        "module_blocks_output",
        "normalized_output",
        "normalized_jsonl_output",
    ]

    with path.open("w", encoding="utf-8", newline="") as file:
        writer = csv.DictWriter(file, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)


def run_pipeline(
    input_dir: Path = DEFAULT_INPUT_DIR,
    work_root: Path = DEFAULT_WORK_ROOT,
    output_report: Path = DEFAULT_OUTPUT_REPORT,
    base_yaml: Path = DEFAULT_BASE_YAML,
) -> list[dict]:
    base_config = load_base_config(base_yaml)
    pdf_paths = list_input_pdfs(input_dir)

    rows = []
    for pdf_path in pdf_paths:
        print(f"Processing: {pdf_path}")
        rows.append(
            process_pdf(
                pdf_path=pdf_path,
                work_root=work_root,
                base_yaml=base_yaml,
                base_config=base_config,
            )
        )

    write_report(output_report, rows)
    return rows


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input-dir", type=Path, default=DEFAULT_INPUT_DIR)
    parser.add_argument("--work-root", type=Path, default=DEFAULT_WORK_ROOT)
    parser.add_argument("--output-report", type=Path, default=DEFAULT_OUTPUT_REPORT)
    parser.add_argument("--base-yaml", type=Path, default=DEFAULT_BASE_YAML)
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    rows = run_pipeline(
        input_dir=args.input_dir,
        work_root=args.work_root,
        output_report=args.output_report,
        base_yaml=args.base_yaml,
    )

    print(f"Processed PDFs: {len(rows)}")
    print(f"Report: {args.output_report}")


if __name__ == "__main__":
    main()
