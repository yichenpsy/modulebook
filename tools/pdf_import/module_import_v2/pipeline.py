import argparse
import csv
import json
import re
import unicodedata
from collections import Counter
from pathlib import Path

import yaml

from tools.pdf_import.module_import_v2.extract_module_pages import (
    build_label_to_field_map,
    find_first_module_page,
    find_last_module_page,
    load_field_label_map,
    write_boundary_pages_csv,
)
from tools.pdf_import.module_import_v2.coordinator_extractor import (
    add_coordinators_to_modules,
    extract_coordinators_from_pages,
)
from tools.pdf_import.module_import_v2.module_splitter import split_modules_to_json
from tools.pdf_import.module_import_v2.normalize_modules_from_md import (
    normalize_modules_file,
    write_json,
    write_jsonl,
)
from tools.pdf_import.module_import_v2.pdf_to_pages_markdown import (
    convert_pdf_to_pages_markdown,
)


PDF_IMPORT_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_INPUT_DIR = PDF_IMPORT_ROOT / "data" / "input"
DEFAULT_WORK_ROOT = PDF_IMPORT_ROOT / "data" / "work_v2"
DEFAULT_OUTPUT_REPORT = PDF_IMPORT_ROOT / "data" / "output" / "v2_import_report.csv"
DEFAULT_BASE_YAML = PDF_IMPORT_ROOT / "configs" / "base.yaml"
DEFAULT_IMPORT_MANIFEST = PDF_IMPORT_ROOT / "configs" / "import_manifest.yaml"


def load_base_config(path: Path) -> dict:
    with path.open("r", encoding="utf-8") as file:
        return yaml.safe_load(file)


def load_yaml(path: Path) -> dict:
    if not path.exists():
        return {}

    with path.open("r", encoding="utf-8") as file:
        return yaml.safe_load(file) or {}


def write_yaml(path: Path, data: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)

    with path.open("w", encoding="utf-8") as file:
        yaml.safe_dump(
            data,
            file,
            allow_unicode=True,
            sort_keys=False,
        )


def list_input_pdfs(input_dir: Path) -> list[Path]:
    return sorted(
        path
        for path in input_dir.glob("*.pdf")
        if path.is_file()
    )


def has_existing_v2_output(pdf_path: Path, work_root: Path) -> bool:
    return (work_root / pdf_path.stem / "normalized_modules.json").exists()


def filter_new_pdfs(pdf_paths: list[Path], work_root: Path) -> list[Path]:
    return [
        pdf_path
        for pdf_path in pdf_paths
        if not has_existing_v2_output(pdf_path, work_root)
    ]


def slugify_import_id(value: str) -> str:
    normalized = unicodedata.normalize("NFKD", value)
    ascii_text = normalized.encode("ascii", "ignore").decode("ascii")
    slug = re.sub(r"[^A-Za-z0-9]+", "_", ascii_text).strip("_").lower()
    return re.sub(r"_+", "_", slug)


def source_file_for_manifest(pdf_path: Path) -> str:
    return str(pdf_path.relative_to(PDF_IMPORT_ROOT.parents[1]))


def sync_import_manifest(
    pdf_paths: list[Path],
    manifest_path: Path = DEFAULT_IMPORT_MANIFEST,
) -> list[str]:
    manifest = load_yaml(manifest_path)
    imports = manifest.setdefault("imports", {})

    if not isinstance(imports, dict):
        raise ValueError("import_manifest.yaml must use a mapping under 'imports'.")

    existing_source_files = {
        import_item.get("source_file")
        for import_item in imports.values()
        if isinstance(import_item, dict)
    }

    added_import_ids = []

    for pdf_path in pdf_paths:
        source_file = source_file_for_manifest(pdf_path)

        if source_file in existing_source_files:
            continue

        import_id = slugify_import_id(pdf_path.stem)
        original_import_id = import_id
        suffix = 2

        while import_id in imports:
            import_id = f"{original_import_id}_{suffix}"
            suffix += 1

        imports[import_id] = {
            "source_file": source_file,
            "study_program_code": import_id,
            "study_program": pdf_path.stem,
            "expected_module_count": None,
        }
        added_import_ids.append(import_id)

    if added_import_ids:
        write_yaml(manifest_path, manifest)

    return added_import_ids


def expected_module_counts_by_source_file(manifest_path: Path) -> dict[str, int | None]:
    manifest = load_yaml(manifest_path)
    imports = manifest.get("imports", {})

    if not isinstance(imports, dict):
        return {}

    expected_counts = {}

    for import_item in imports.values():
        if not isinstance(import_item, dict):
            continue

        source_file = import_item.get("source_file")

        if source_file:
            expected_counts[source_file] = import_item.get("expected_module_count")

    return expected_counts


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
                    "source_pages": module.get("source_pages"),
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
    expected_module_counts: dict[str, int | None],
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

    source_file = source_file_for_manifest(pdf_path)

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
        output_path=normalized_json,
        base_path=base_yaml,
        jsonl_output_path=normalized_jsonl,
    )
    coordinators_by_module = extract_coordinators_from_pages(pages)
    coordinator_count = add_coordinators_to_modules(
        normalized_modules,
        coordinators_by_module,
    )
    write_json(normalized_json, normalized_modules)
    write_jsonl(normalized_jsonl, normalized_modules)

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
        "page_count_all": len(pages),
        "first_module_page": first_page_number,
        "last_module_page": last_page_number,
        "detected_module_count": len(normalized_modules),
        "coordinator_count": coordinator_count,
        "required_module_count": expected_module_counts.get(source_file),
        "has_all_required_fields": has_all_required_fields,
        "missing_required_fields": json.dumps(
            missing_required_fields,
            ensure_ascii=False,
        ),
        "unmatched_labels": counter_json(normalize_stats["unmatched_labels"]),
        "unmatched_label_count": len(normalize_stats["unmatched_labels"]),
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
        "page_count_all",
        "first_module_page",
        "last_module_page",
        "detected_module_count",
        "coordinator_count",
        "required_module_count",
        "has_all_required_fields",
        "missing_required_fields",
        "unmatched_labels",
        "unmatched_label_count",
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


def read_report(path: Path) -> list[dict]:
    if not path.exists():
        return []

    with path.open("r", encoding="utf-8") as file:
        return list(csv.DictReader(file))


def merge_report_rows(existing_rows: list[dict], new_rows: list[dict]) -> list[dict]:
    rows_by_pdf_name = {
        row["pdf_name"]: row
        for row in existing_rows
        if row.get("pdf_name")
    }

    for row in new_rows:
        rows_by_pdf_name[row["pdf_name"]] = row

    return sorted(rows_by_pdf_name.values(), key=lambda row: row["pdf_name"])


def run_pipeline(
    input_dir: Path = DEFAULT_INPUT_DIR,
    work_root: Path = DEFAULT_WORK_ROOT,
    output_report: Path = DEFAULT_OUTPUT_REPORT,
    base_yaml: Path = DEFAULT_BASE_YAML,
    import_manifest: Path = DEFAULT_IMPORT_MANIFEST,
    sync_manifest: bool = True,
    new_only: bool = False,
) -> list[dict]:
    base_config = load_base_config(base_yaml)
    all_pdf_paths = list_input_pdfs(input_dir)

    if sync_manifest:
        added_import_ids = sync_import_manifest(all_pdf_paths, import_manifest)

        if added_import_ids:
            print(f"Updated import manifest: {import_manifest}")
            print(f"Added imports: {', '.join(added_import_ids)}")

    expected_module_counts = expected_module_counts_by_source_file(import_manifest)
    pdf_paths = all_pdf_paths

    if new_only:
        pdf_paths = filter_new_pdfs(all_pdf_paths, work_root)
        print(f"New PDFs to process: {len(pdf_paths)}")

        if not pdf_paths:
            print("No new PDFs found.")
            return []

    processed_rows = []
    for pdf_path in pdf_paths:
        print(f"Processing: {pdf_path}")
        processed_rows.append(
            process_pdf(
                pdf_path=pdf_path,
                work_root=work_root,
                base_yaml=base_yaml,
                base_config=base_config,
                expected_module_counts=expected_module_counts,
            )
        )

    if new_only:
        report_rows = merge_report_rows(read_report(output_report), processed_rows)
    else:
        report_rows = processed_rows

    write_report(output_report, report_rows)
    return processed_rows


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input-dir", type=Path, default=DEFAULT_INPUT_DIR)
    parser.add_argument("--work-root", type=Path, default=DEFAULT_WORK_ROOT)
    parser.add_argument("--output-report", type=Path, default=DEFAULT_OUTPUT_REPORT)
    parser.add_argument("--base-yaml", type=Path, default=DEFAULT_BASE_YAML)
    parser.add_argument("--import-manifest", type=Path, default=DEFAULT_IMPORT_MANIFEST)
    parser.add_argument(
        "--no-sync-manifest",
        action="store_true",
        help="Do not add newly discovered PDFs to import_manifest.yaml.",
    )
    parser.add_argument(
        "--new-only",
        action="store_true",
        help="Only process PDFs without an existing work_v2/<pdf_stem>/normalized_modules.json.",
    )
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    rows = run_pipeline(
        input_dir=args.input_dir,
        work_root=args.work_root,
        output_report=args.output_report,
        base_yaml=args.base_yaml,
        import_manifest=args.import_manifest,
        sync_manifest=not args.no_sync_manifest,
        new_only=args.new_only,
    )

    print(f"Processed PDFs: {len(rows)}")
    print(f"Report: {args.output_report}")


if __name__ == "__main__":
    main()
