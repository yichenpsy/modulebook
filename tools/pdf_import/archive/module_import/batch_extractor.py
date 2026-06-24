import argparse
import csv
import json
import time
from pathlib import Path

from tools.pdf_import.archive.module_import.config import PDF_IMPORT_ROOT, get_imports
from tools.pdf_import.archive.module_import.llm_extractor import (
    load_prompt_template,
    load_jsonl,
    render_prompt,
)
from tools.pdf_import.archive.module_import.llm_client import call_llm
from tools.pdf_import.archive.module_import.json_validator import (
    parse_llm_json,
    validate_extracted_module,
)


def write_jsonl(path: Path, records: list[dict]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)

    with path.open("w", encoding="utf-8") as f:
        for record in records:
            f.write(json.dumps(record, ensure_ascii=False) + "\n")


def write_csv(path: Path, rows: list[dict], fieldnames: list[str]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)

    with path.open("w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)


def extract_single_module(
    import_id: str,
    module_block: dict,
    prompt_template: str,
    sleep_seconds: float = 0.0,
) -> dict:
    module_number = module_block["module_number"]

    prompt = render_prompt(prompt_template, module_block)

    result = {
        "import_id": import_id,
        "module_number": module_number,
        "source_file": module_block.get("source_file"),
        "source_pages": module_block.get("source_pages"),
        "raw_output": None,
        "parsed_json": None,
        "validation_status": "need_review",
        "validation_errors": [],
        "validation_warnings": [],
    }

    try:
        raw_output = call_llm(prompt)
        result["raw_output"] = raw_output

        module_data, parse_error = parse_llm_json(raw_output)

        if parse_error is not None:
            result["validation_errors"] = [f"JSON parse error: {parse_error}"]
            return result

        validation = validate_extracted_module(module_data)

        result["parsed_json"] = module_data
        result["validation_status"] = validation["validation_status"]
        result["validation_errors"] = validation["validation_errors"]
        result["validation_warnings"] = validation["validation_warnings"]

        return result

    except Exception as error:
        result["validation_errors"] = [f"LLM/API error: {error}"]
        return result

    finally:
        if sleep_seconds > 0:
            time.sleep(sleep_seconds)


def run_batch_extraction(
    import_id_filter: str | None = None,
    limit_per_import: int | None = None,
    sleep_seconds: float = 0.0,
) -> None:
    prompt_template = load_prompt_template()
    imports = get_imports()

    all_valid = []
    all_need_review = []
    validation_rows = []
    additional_field_rows = []

    for import_item in imports:
        import_id = import_item["import_id"]

        if import_id_filter is not None and import_id != import_id_filter:
            continue

        print(f"Processing import: {import_id}")

        module_blocks_path = (
            PDF_IMPORT_ROOT
            / "data"
            / "work"
            / import_id
            / "module_blocks.jsonl"
        )

        module_blocks = load_jsonl(module_blocks_path)

        if limit_per_import is not None:
            module_blocks = module_blocks[:limit_per_import]

        raw_results = []

        for index, module_block in enumerate(module_blocks, start=1):
            module_number = module_block["module_number"]

            print(f"  [{index}/{len(module_blocks)}] Extracting {module_number}")

            result = extract_single_module(
                import_id=import_id,
                module_block=module_block,
                prompt_template=prompt_template,
                sleep_seconds=sleep_seconds,
            )

            raw_results.append(result)

            parsed_json = result["parsed_json"]

            validation_rows.append({
                "import_id": import_id,
                "module_number": result["module_number"],
                "source_file": result["source_file"],
                "source_pages": json.dumps(result["source_pages"], ensure_ascii=False),
                "validation_status": result["validation_status"],
                "validation_errors": " | ".join(result["validation_errors"]),
                "validation_warnings": " | ".join(result["validation_warnings"]),
            })

            if parsed_json is not None:
                parsed_json["_import_id"] = import_id
                parsed_json["_validation_warnings"] = result["validation_warnings"]

                if result["validation_status"] == "valid":
                    all_valid.append(parsed_json)
                else:
                    parsed_json["_validation_errors"] = result["validation_errors"]
                    all_need_review.append(parsed_json)
            else:
                all_need_review.append({
                    "import_id": import_id,
                    "module_number": result["module_number"],
                    "source_file": result["source_file"],
                    "source_pages": result["source_pages"],
                    "raw_output": result["raw_output"],
                    "validation_errors": result["validation_errors"],
                })

            collect_additional_fields(
                import_id=import_id,
                module_data=parsed_json,
                additional_field_rows=additional_field_rows,
            )

        raw_output_path = (
            PDF_IMPORT_ROOT
            / "data"
            / "work"
            / import_id
            / "extracted_modules_raw.jsonl"
        )

        write_jsonl(raw_output_path, raw_results)

    output_dir = PDF_IMPORT_ROOT / "data" / "output"

    write_jsonl(output_dir / "modules_valid.jsonl", all_valid)
    write_jsonl(output_dir / "modules_need_review.jsonl", all_need_review)

    write_csv(
        output_dir / "validation_report.csv",
        validation_rows,
        fieldnames=[
            "import_id",
            "module_number",
            "source_file",
            "source_pages",
            "validation_status",
            "validation_errors",
            "validation_warnings",
        ],
    )

    write_csv(
        output_dir / "additional_fields_report.csv",
        additional_field_rows,
        fieldnames=[
            "import_id",
            "source_file",
            "module_number",
            "field_label",
            "field_value",
        ],
    )

    print("Batch extraction finished.")
    print(f"Valid modules: {len(all_valid)}")
    print(f"Need review: {len(all_need_review)}")


def collect_additional_fields(
    import_id: str,
    module_data: dict | None,
    additional_field_rows: list[dict],
) -> None:
    if module_data is None:
        return

    additional_fields = module_data.get("additional_fields", {})

    if not isinstance(additional_fields, dict):
        return

    for field_label, field_value in additional_fields.items():
        additional_field_rows.append({
            "import_id": import_id,
            "source_file": module_data.get("source_file"),
            "module_number": module_data.get("module_number"),
            "field_label": field_label,
            "field_value": field_value,
        })


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()

    parser.add_argument(
        "--import-id",
        type=str,
        default=None,
        help="Only process one import_id, e.g. medieninformatik_2024",
    )

    parser.add_argument(
        "--limit",
        type=int,
        default=None,
        help="Limit number of modules per import for testing",
    )

    parser.add_argument(
        "--sleep",
        type=float,
        default=0.0,
        help="Sleep seconds between LLM calls",
    )

    return parser.parse_args()


if __name__ == "__main__":
    args = parse_args()

    run_batch_extraction(
        import_id_filter=args.import_id,
        limit_per_import=args.limit,
        sleep_seconds=args.sleep,
    )