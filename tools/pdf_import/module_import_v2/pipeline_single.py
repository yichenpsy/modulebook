import argparse
import json
import sys
import traceback
from pathlib import Path

from tools.pdf_import.module_import_v2.pdf_to_pages_markdown import (
    convert_pdf_to_pages_markdown,
)
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
from tools.pdf_import.module_import_v2.pipeline import (
    DEFAULT_IMPORT_MANIFEST,
    load_base_config,
    expected_module_counts_by_source_file,
    required_fields_for_v2,
    required_field_summary,
    counter_json,
)


DEFAULT_BASE_YAML = Path("tools/pdf_import/configs/base.yaml")


def write_json(path: Path, data: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        json.dumps(data, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )


def run_single_pdf(
    input_pdf: Path,
    work_dir: Path,
    output_json: Path,
    report_json: Path,
    base_yaml: Path = DEFAULT_BASE_YAML,
    import_manifest: Path = DEFAULT_IMPORT_MANIFEST,
    source_file: str | None = None,
) -> dict:
    work_dir.mkdir(parents=True, exist_ok=True)

    pages_json = work_dir / "pages_md.json"
    boundary_csv = work_dir / "module_boundary_pages.csv"
    module_blocks_json = work_dir / "module_blocks_md.json"
    normalized_jsonl = work_dir / "normalized_modules.jsonl"

    base_config = load_base_config(base_yaml)
    expected_module_counts = expected_module_counts_by_source_file(import_manifest)
    source_file = source_file or str(input_pdf)

    pages = convert_pdf_to_pages_markdown(input_pdf, pages_json)

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
        input_pdf.stem,
        first_module_page,
        last_module_page,
    )

    first_page_number = (
        first_module_page["first_module_page_number"]
        if first_module_page is not None
        else None
    )

    last_page_number = (
        last_module_page["last_module_page_number"]
        if last_module_page is not None
        else None
    )

    module_blocks = split_modules_to_json(
        pages_json=pages_json,
        output_json=module_blocks_json,
        base_yaml=base_yaml,
        first_page_number=first_page_number,
        last_page_number=last_page_number,
        source_file=source_file,
    )

    normalized_modules, normalize_stats = normalize_modules_file(
        input_path=module_blocks_json,
        output_path=output_json,
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

    report = {
        "status": status,
        "input_pdf": str(input_pdf),
        "page_count_all": len(pages),
        "first_module_page": first_page_number,
        "last_module_page": last_page_number,
        "detected_module_count": len(normalized_modules),
        "required_module_count": expected_module_counts.get(source_file),
        "has_all_required_fields": has_all_required_fields,
        "missing_required_fields": missing_required_fields,
        "unmatched_labels": counter_json(normalize_stats["unmatched_labels"]),
        "unmatched_label_count": len(normalize_stats["unmatched_labels"]),
        "outputs": {
            "pages": str(pages_json),
            "module_boundary_pages": str(boundary_csv),
            "module_blocks": str(module_blocks_json),
            "normalized_modules": str(output_json),
            "normalized_modules_jsonl": str(normalized_jsonl),
        },
    }

    write_json(report_json, report)
    return report


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True, type=Path)
    parser.add_argument("--work-dir", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--report", required=True, type=Path)
    parser.add_argument("--base-yaml", type=Path, default=DEFAULT_BASE_YAML)
    parser.add_argument("--import-manifest", type=Path, default=DEFAULT_IMPORT_MANIFEST)
    parser.add_argument("--source-file", default=None)
    return parser.parse_args()


def main() -> int:
    args = parse_args()

    try:
        report = run_single_pdf(
            input_pdf=args.input,
            work_dir=args.work_dir,
            output_json=args.output,
            report_json=args.report,
            base_yaml=args.base_yaml,
            import_manifest=args.import_manifest,
            source_file=args.source_file,
        )

        print(json.dumps(report, ensure_ascii=False, indent=2))

        if report["status"] == "failed":
            return 1

        return 0

    except Exception as error:
        failed_report = {
            "status": "failed",
            "error_message": str(error),
            "traceback": traceback.format_exc(),
        }

        write_json(args.report, failed_report)
        print(json.dumps(failed_report, ensure_ascii=False, indent=2), file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
